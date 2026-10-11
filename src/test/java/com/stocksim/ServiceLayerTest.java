package com.stocksim;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.stocksim.dao.HoldingDAO;
import com.stocksim.dao.StockDAO;
import com.stocksim.dao.StockHistoryDAO;
import com.stocksim.dao.TransactionDAO;
import com.stocksim.dao.UserDAO;
import com.stocksim.exception.InsufficientBalanceException;
import com.stocksim.exception.InsufficientSharesException;
import com.stocksim.exception.InvalidQuantityException;
import com.stocksim.exception.UserNotFoundException;
import com.stocksim.model.Goal;
import com.stocksim.model.Holding;
import com.stocksim.model.PortfolioSummary;
import com.stocksim.model.PriceAlert;
import com.stocksim.model.SimulationState;
import com.stocksim.model.Stock;
import com.stocksim.model.StockAnalysis;
import com.stocksim.model.Transaction;
import com.stocksim.model.User;
import com.stocksim.service.AlertService;
import com.stocksim.service.AnalysisService;
import com.stocksim.service.AuthService;
import com.stocksim.service.PortfolioService;
import com.stocksim.service.ReportService;
import com.stocksim.service.RiskService;
import com.stocksim.service.SimulationService;
import com.stocksim.service.StateService;
import com.stocksim.service.TradingService;
import com.stocksim.util.DBConnection;

/** Business-logic tests: trading, portfolio maths, analysis, risk, simulation, exports. */
@DisplayName("Service layer")
class ServiceLayerTest {

    private User user;

    private final AuthService authService = new AuthService();
    private final TradingService tradingService = new TradingService();
    private final PortfolioService portfolioService = new PortfolioService();
    private final AnalysisService analysisService = new AnalysisService();
    private final RiskService riskService = new RiskService();
    private final SimulationService simulationService = new SimulationService();
    private final AlertService alertService = new AlertService();
    private final StockDAO stockDAO = new StockDAO();

    @BeforeAll
    static void databaseMustBeReachable() {
        assertTrue(DBConnection.testConnection());
    }

    @BeforeEach
    void setUp() {
        TestSupport.resetMarketToDayOne();      // all prices back to simulation day 1
        user = TestSupport.createTestUser();
    }

    @AfterEach
    void tearDown() {
        TestSupport.deleteTestUser(user);
        TestSupport.resetMarketToDayOne();
    }

    // =============== AuthService ===============

    @Test
    void registrationCreatesAUserWithTheStartingCapital() throws Exception {
        String unique = "reg" + System.nanoTime();
        User created = authService.register("Deepakshi R", unique, unique + "@example.com",
                "password123", "password123");
        try {
            assertTrue(created.getUserId() > 0);
            assertEquals(TestSupport.STARTING_BALANCE, created.getBalance(), 0.001);
            assertEquals(1, created.getSimDay());
            assertNotEquals("password123", created.getPassword(), "password must be hashed");
            assertEquals(64, created.getPassword().length());

            User loggedIn = authService.login(unique, "password123");
            assertEquals(created.getUserId(), loggedIn.getUserId());

            // login by email works too
            assertEquals(created.getUserId(),
                    authService.login(unique + "@example.com", "password123").getUserId());
        } finally {
            TestSupport.deleteTestUser(created);
        }
    }

    @Test
    void registrationRejectsBadInputAndDuplicates() {
        assertFalse(authService.validateRegistration("", "ab", "bad-email", "short", "nope").isEmpty());

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> authService.register("N", "x", "bad", "short", "different"));
        assertFalse(e.getMessage().isBlank());

        // the throwaway user from setUp already owns this username and email
        assertThrows(IllegalArgumentException.class,
                () -> authService.register("Dup", user.getUsername(), "fresh@example.com",
                        "password123", "password123"));
        assertThrows(IllegalArgumentException.class,
                () -> authService.register("Dup", "freshusername1", user.getEmail(),
                        "password123", "password123"));
    }

    @Test
    void loginFailsClosedForWrongCredentials() {
        assertThrows(UserNotFoundException.class,
                () -> authService.login(user.getUsername(), "wrongpassword"));
        assertThrows(UserNotFoundException.class,
                () -> authService.login("nobody_at_all", "password123"));
        assertThrows(UserNotFoundException.class, () -> authService.login("", ""));
    }

    // =============== TradingService ===============

    @Test
    void buyingDeductsCashCreatesAHoldingAndLogsATransaction() throws Exception {
        Stock tcs = TestSupport.stock("TCS");
        double price = tcs.getCurrentPrice();
        int quantity = 10;

        Transaction txn = tradingService.buyStock(user.getUserId(), tcs.getStockId(), quantity);

        assertEquals(Transaction.Type.BUY, txn.getTransactionType());
        assertEquals(quantity, txn.getQuantity());
        assertEquals(price, txn.getPrice(), 0.01);
        assertEquals(price * quantity, txn.getTotalAmount(), 0.01);

        double expectedBalance = round2(TestSupport.STARTING_BALANCE - price * quantity);
        assertEquals(expectedBalance,
                new UserDAO().findById(user.getUserId()).orElseThrow().getBalance(), 0.01);

        Holding holding = new HoldingDAO()
                .findByUserAndStock(user.getUserId(), tcs.getStockId()).orElseThrow();
        assertEquals(quantity, holding.getQuantity());
        assertEquals(price, holding.getAverageBuyPrice(), 0.01);

        assertEquals(1, new TransactionDAO().countByUser(user.getUserId()));
    }

    @Test
    void sellingAddsCashBackAndReducesTheHolding() throws Exception {
        Stock infy = TestSupport.stock("INFY");
        double price = infy.getCurrentPrice();

        tradingService.buyStock(user.getUserId(), infy.getStockId(), 20);
        tradingService.sellStock(user.getUserId(), infy.getStockId(), 8);

        assertEquals(12, new HoldingDAO().getQuantity(user.getUserId(), infy.getStockId()));

        double expected = round2(TestSupport.STARTING_BALANCE - price * 20 + price * 8);
        assertEquals(expected,
                new UserDAO().findById(user.getUserId()).orElseThrow().getBalance(), 0.02);

        assertEquals(2, new TransactionDAO().countByUser(user.getUserId()));
        assertEquals(1, new TransactionDAO()
                .countByUserAndType(user.getUserId(), Transaction.Type.SELL));
    }

    @Test
    void sellingEverythingRemovesTheHoldingAndRestoresTheCash() throws Exception {
        Stock itc = TestSupport.stock("ITC");
        tradingService.buyStock(user.getUserId(), itc.getStockId(), 15);
        tradingService.sellStock(user.getUserId(), itc.getStockId(), 15);

        assertTrue(new HoldingDAO().findByUserAndStock(user.getUserId(), itc.getStockId()).isEmpty());
        // bought and sold at the same price on the same day, so the cash comes back in full
        assertEquals(TestSupport.STARTING_BALANCE,
                new UserDAO().findById(user.getUserId()).orElseThrow().getBalance(), 0.02);
    }

    @Test
    void buyingMoreThanTheCashBalanceAllowsIsRefused() {
        Stock reliance = TestSupport.stock("RELIANCE");
        int tooMany = (int) (TestSupport.STARTING_BALANCE / reliance.getCurrentPrice()) + 100;

        InsufficientBalanceException e = assertThrows(InsufficientBalanceException.class,
                () -> tradingService.buyStock(user.getUserId(), reliance.getStockId(), tooMany));
        assertTrue(e.getMessage().contains("Not enough cash"));

        // nothing was changed by the failed attempt
        assertEquals(TestSupport.STARTING_BALANCE,
                new UserDAO().findById(user.getUserId()).orElseThrow().getBalance(), 0.001);
        assertTrue(new HoldingDAO().findByUserId(user.getUserId()).isEmpty());
        assertEquals(0, new TransactionDAO().countByUser(user.getUserId()));
    }

    @Test
    void sellingSharesYouDoNotOwnIsRefused() throws Exception {
        Stock sbin = TestSupport.stock("SBIN");

        assertThrows(InsufficientSharesException.class,
                () -> tradingService.sellStock(user.getUserId(), sbin.getStockId(), 1));

        tradingService.buyStock(user.getUserId(), sbin.getStockId(), 5);
        assertThrows(InsufficientSharesException.class,
                () -> tradingService.sellStock(user.getUserId(), sbin.getStockId(), 6));

        assertEquals(5, new HoldingDAO().getQuantity(user.getUserId(), sbin.getStockId()));
    }

    @Test
    void zeroAndNegativeQuantitiesAreRefused() {
        int stockId = TestSupport.stock("WIPRO").getStockId();
        assertThrows(InvalidQuantityException.class,
                () -> tradingService.buyStock(user.getUserId(), stockId, 0));
        assertThrows(InvalidQuantityException.class,
                () -> tradingService.buyStock(user.getUserId(), stockId, -5));
        assertThrows(InvalidQuantityException.class,
                () -> tradingService.sellStock(user.getUserId(), stockId, 0));
    }

    @Test
    void repeatedBuysAtDifferentPricesBlendIntoAWeightedAverage() throws Exception {
        Stock tatamotors = TestSupport.stock("TATAMOTORS");
        int stockId = tatamotors.getStockId();

        double priceDay1 = tatamotors.getCurrentPrice();
        tradingService.buyStock(user.getUserId(), stockId, 10);

        // move the market, then buy again at the new price
        simulationService.advance(user.getUserId(), 50);
        double priceDay51 = stockDAO.findById(stockId).orElseThrow().getCurrentPrice();
        tradingService.buyStock(user.getUserId(), stockId, 10);

        Holding holding = new HoldingDAO().findByUserAndStock(user.getUserId(), stockId).orElseThrow();
        assertEquals(20, holding.getQuantity());
        assertEquals((priceDay1 + priceDay51) / 2.0, holding.getAverageBuyPrice(), 0.05);
    }

    @Test
    void aFailedTradeLeavesNoPartialWrites() {
        // stock id 99999 does not exist, so the trade must abort before touching anything
        assertThrows(IllegalArgumentException.class,
                () -> tradingService.buyStock(user.getUserId(), 99999, 1));

        assertEquals(TestSupport.STARTING_BALANCE,
                new UserDAO().findById(user.getUserId()).orElseThrow().getBalance(), 0.001);
        assertEquals(0, new TransactionDAO().countByUser(user.getUserId()));
        assertTrue(new HoldingDAO().findByUserId(user.getUserId()).isEmpty());
    }

    @Test
    void maxAffordableQuantityMatchesTheCashBalance() {
        Stock hdfc = TestSupport.stock("HDFCBANK");
        int max = tradingService.maxAffordableQuantity(user.getUserId(), hdfc.getStockId());

        assertTrue(max > 0);
        assertTrue(max * hdfc.getCurrentPrice() <= TestSupport.STARTING_BALANCE);
        assertTrue((max + 1) * hdfc.getCurrentPrice() > TestSupport.STARTING_BALANCE);

        assertEquals(round2(hdfc.getCurrentPrice() * 5),
                tradingService.calculateCost(hdfc.getStockId(), 5), 0.01);
    }

    // =============== PortfolioService ===============

    @Test
    void portfolioSummaryAddsUp() throws Exception {
        Stock tcs = TestSupport.stock("TCS");
        Stock infy = TestSupport.stock("INFY");

        tradingService.buyStock(user.getUserId(), tcs.getStockId(), 10);
        tradingService.buyStock(user.getUserId(), infy.getStockId(), 20);

        double invested = tcs.getCurrentPrice() * 10 + infy.getCurrentPrice() * 20;

        PortfolioSummary summary = portfolioService.getPortfolioSummary(user.getUserId());
        assertEquals(2, summary.getHoldingCount());
        assertEquals(invested, summary.getInvested(), 0.05);
        assertEquals(invested, summary.getHoldingsValue(), 0.05, "prices have not moved yet");
        assertEquals(0.0, summary.getProfitLoss(), 0.05);
        assertEquals(round2(TestSupport.STARTING_BALANCE - invested), summary.getCash(), 0.05);
        assertEquals(TestSupport.STARTING_BALANCE, summary.getTotalValue(), 0.10,
                "cash plus holdings must still equal the starting capital");
        assertEquals(0.0, summary.getTotalReturnPercent(), 0.01);
    }

    @Test
    void profitAndLossTracksThePriceMovingUnderTheHolding() throws Exception {
        Stock stock = TestSupport.stock("HINDUNILVR");
        tradingService.buyStock(user.getUserId(), stock.getStockId(), 25);
        double buyPrice = stock.getCurrentPrice();

        simulationService.advance(user.getUserId(), 200);
        double newPrice = stockDAO.findById(stock.getStockId()).orElseThrow().getCurrentPrice();

        PortfolioSummary summary = portfolioService.getPortfolioSummary(user.getUserId());
        double expectedPl = round2((newPrice - buyPrice) * 25);

        assertEquals(expectedPl, summary.getProfitLoss(), 0.05);
        assertEquals(round2(newPrice * 25), summary.getHoldingsValue(), 0.05);
        assertEquals(expectedPl > 0, summary.getTotalReturnPercent() > 0);
    }

    @Test
    void bestAndWorstPerformersAreIdentified() throws Exception {
        tradingService.buyStock(user.getUserId(), TestSupport.stock("TCS").getStockId(), 5);
        tradingService.buyStock(user.getUserId(), TestSupport.stock("SBIN").getStockId(), 5);
        simulationService.advance(user.getUserId(), 300);

        Holding best = portfolioService.getBestPerformer(user.getUserId());
        Holding worst = portfolioService.getWorstPerformer(user.getUserId());

        assertNotNull(best);
        assertNotNull(worst);
        assertTrue(best.calculateProfitLossPercent() >= worst.calculateProfitLossPercent());
    }

    @Test
    void tradeStatisticsCountProfitableAndLossMakingSells() throws Exception {
        int stockId = TestSupport.stock("ICICIBANK").getStockId();
        tradingService.buyStock(user.getUserId(), stockId, 10);
        simulationService.advance(user.getUserId(), 400);
        tradingService.sellStock(user.getUserId(), stockId, 10);

        assertEquals(2, portfolioService.getTotalTrades(user.getUserId()));
        assertEquals(1, portfolioService.getBuyCount(user.getUserId()));
        assertEquals(1, portfolioService.getSellCount(user.getUserId()));

        int[] stats = portfolioService.getProfitableAndLossMakingTrades(user.getUserId());
        assertEquals(1, stats[0] + stats[1], "the single sell is either a win or a loss");
    }

    @Test
    void anEmptyPortfolioReportsZeroesRatherThanFailing() {
        PortfolioSummary summary = portfolioService.getPortfolioSummary(user.getUserId());

        assertEquals(0, summary.getHoldingCount());
        assertEquals(0.0, summary.getInvested(), 0.001);
        assertEquals(0.0, summary.getProfitLoss(), 0.001);
        assertEquals(0.0, summary.getProfitLossPercent(), 0.001, "no divide-by-zero");
        assertEquals(TestSupport.STARTING_BALANCE, summary.getTotalValue(), 0.001);
        assertNull(portfolioService.getBestPerformer(user.getUserId()));
    }

    // =============== AnalysisService ===============

    @Test
    void analysisProducesEveryMetricForAStock() {
        Stock tcs = TestSupport.stock("TCS");
        int day = 500;

        StockAnalysis a = analysisService.analyse(tcs.getStockId(), day);

        assertEquals("TCS", a.getSymbol());
        assertEquals(day, a.getSimulationDay());
        assertEquals(TestSupport.historicalPrice(tcs.getStockId(), day), a.getCurrentPrice(), 0.01);
        assertEquals(TestSupport.historicalPrice(tcs.getStockId(), day - 1), a.getPreviousClose(), 0.01);

        assertTrue(a.getMovingAverage5() > 0);
        assertTrue(a.getMovingAverage20() > 0);
        assertTrue(a.getWeek52High() >= a.getCurrentPrice() * 0.5);
        assertTrue(a.getWeek52High() >= a.getWeek52Low());
        assertTrue(a.getWeek52Low() > 0);
        assertTrue(a.getVolatilityPercent() >= 0);

        assertTrue(List.of("Low", "Medium", "High").contains(a.getVolatilityLabel()));
        assertTrue(List.of("Positive", "Negative", "Neutral").contains(a.getTrendLabel()));
        assertTrue(List.of("Positive", "Negative").contains(a.getMomentumLabel()));
        assertTrue(List.of("Low", "Moderate", "High").contains(a.getRiskProfile()));
    }

    @Test
    void percentChangesMatchTheUnderlyingHistory() {
        int stockId = TestSupport.stock("RELIANCE").getStockId();
        int day = 600;

        double today = TestSupport.historicalPrice(stockId, day);
        double sevenBack = TestSupport.historicalPrice(stockId, day - 7);
        double expected = round2(((today - sevenBack) / sevenBack) * 100.0);

        assertEquals(expected, analysisService.get7DayChange(stockId, day), 0.02);
        assertEquals(today, analysisService.priceOn(stockId, day), 0.01);
    }

    @Test
    void movingAverageEqualsTheMeanOfTheWindow() {
        int stockId = TestSupport.stock("WIPRO").getStockId();
        int day = 300;

        double sum = 0;
        for (int d = day - 4; d <= day; d++) {
            sum += TestSupport.historicalPrice(stockId, d);
        }
        assertEquals(round2(sum / 5), analysisService.getMovingAverage(stockId, day, 5), 0.02);
    }

    @Test
    void analysisIsSafeAtTheVeryStartOfTheSeries() {
        int stockId = TestSupport.stock("ITC").getStockId();
        StockAnalysis a = analysisService.analyse(stockId, 1);

        assertEquals(0.0, a.getDailyChangePercent(), 0.001, "day 1 has no previous close");
        assertTrue(a.getCurrentPrice() > 0);
        assertNotNull(a.getTrendLabel());
    }

    @Test
    void chartSeriesHasTheRequestedLength() {
        int stockId = TestSupport.stock("INFY").getStockId();
        assertEquals(30, analysisService.getPriceSeries(stockId, 500, 30).size());
        assertEquals(180, analysisService.getPriceSeries(stockId, 500, 180).size());
    }

    // =============== RiskService ===============

    @Test
    void concentrationInOneStockRaisesAWarning() throws Exception {
        Stock stock = TestSupport.stock("TCS");
        tradingService.buyStock(user.getUserId(), stock.getStockId(), 100);

        Map<String, Double> allocation = riskService.getStockAllocation(user.getUserId());
        assertEquals(1, allocation.size());
        assertEquals(100.0, allocation.get("TCS"), 0.01);

        assertEquals("Poor", riskService.getDiversificationLevel(user.getUserId()));
        List<String> warnings = riskService.getConcentrationWarnings(user.getUserId());
        assertFalse(warnings.isEmpty());
        assertTrue(warnings.get(0).contains("TCS"));

        assertEquals("TCS", riskService.getLargestHolding(user.getUserId()).getKey());
        assertEquals("IT", riskService.getLargestSector(user.getUserId()).getKey());
    }

    @Test
    void sectorAllocationSumsToOneHundredPercent() throws Exception {
        tradingService.buyStock(user.getUserId(), TestSupport.stock("TCS").getStockId(), 20);
        tradingService.buyStock(user.getUserId(), TestSupport.stock("HDFCBANK").getStockId(), 20);
        tradingService.buyStock(user.getUserId(), TestSupport.stock("ITC").getStockId(), 20);
        tradingService.buyStock(user.getUserId(), TestSupport.stock("TATAMOTORS").getStockId(), 20);

        Map<String, Double> sectors = riskService.getSectorAllocation(user.getUserId());
        assertEquals(4, sectors.size());
        assertEquals(100.0, sectors.values().stream().mapToDouble(Double::doubleValue).sum(), 0.1);

        // percentages must come back largest first
        double previous = Double.MAX_VALUE;
        for (double percent : sectors.values()) {
            assertTrue(percent <= previous);
            previous = percent;
        }
        assertNotEquals("No holdings", riskService.getDiversificationLevel(user.getUserId()));
    }

    @Test
    void riskServiceHandlesAnEmptyPortfolio() {
        assertTrue(riskService.getSectorAllocation(user.getUserId()).isEmpty());
        assertTrue(riskService.getStockAllocation(user.getUserId()).isEmpty());
        assertEquals("No holdings", riskService.getDiversificationLevel(user.getUserId()));
        assertTrue(riskService.getConcentrationWarnings(user.getUserId()).isEmpty());
        assertNull(riskService.getLargestHolding(user.getUserId()));
    }

    // =============== SimulationService ===============

    @Test
    void advancingTimeMovesTheDayAndRepricesTheWholeMarket() {
        assertEquals(1, simulationService.getCurrentDay(user.getUserId()));

        simulationService.advanceDay(user.getUserId());
        assertEquals(2, simulationService.getCurrentDay(user.getUserId()));

        simulationService.advanceWeek(user.getUserId());
        assertEquals(9, simulationService.getCurrentDay(user.getUserId()));

        SimulationService.AdvanceResult result = simulationService.advanceMonth(user.getUserId());
        assertEquals(39, result.getNewDay());
        assertEquals(30, result.getDaysAdvanced());

        // every stock's current_price must now equal its day-39 historical close
        for (Stock stock : stockDAO.findAll()) {
            assertEquals(TestSupport.historicalPrice(stock.getStockId(), 39),
                    stock.getCurrentPrice(), 0.01, stock.getSymbol() + " was not repriced");
        }
    }

    @Test
    void marketEventsFireInsideTheAdvancedRange() {
        SimulationService.AdvanceResult result = simulationService.advance(user.getUserId(), 60);
        assertFalse(result.getEvents().isEmpty(), "scripted events exist between day 1 and 60");
        assertTrue(result.getEvents().stream()
                .allMatch(e -> e.getSimulationDay() > 1 && e.getSimulationDay() <= 61));
    }

    @Test
    void theSimulationStopsAtTheLastDayWithData() {
        int lastDay = simulationService.getLastAvailableDay();
        assertTrue(lastDay > 1000);

        simulationService.resetToDay(user.getUserId(), lastDay - 3);
        SimulationService.AdvanceResult result = simulationService.advance(user.getUserId(), 30);

        assertEquals(lastDay, result.getNewDay(), "must clamp, never run past the data");
        assertTrue(result.isReachedEnd());

        // a further advance is a no-op rather than an error
        SimulationService.AdvanceResult again = simulationService.advanceDay(user.getUserId());
        assertEquals(lastDay, again.getNewDay());
        assertEquals(0, again.getDaysAdvanced());
    }

    @Test
    void pricesCanBeResynchronisedToAUsersOwnDay() {
        simulationService.resetToDay(user.getUserId(), 1234);
        simulationService.syncPricesToUser(user.getUserId());

        Stock tcs = TestSupport.stock("TCS");
        assertEquals(TestSupport.historicalPrice(tcs.getStockId(), 1234),
                tcs.getCurrentPrice(), 0.01);
    }

    // =============== AlertService ===============

    @Test
    void anAlertFiresWhenThePriceCrossesItsTarget() throws Exception {
        Stock stock = TestSupport.stock("TCS");
        double currentPrice = stock.getCurrentPrice();

        // an ABOVE alert just under the current price is met immediately
        alertService.createAlert(user.getUserId(), stock.getStockId(),
                PriceAlert.Condition.ABOVE, String.valueOf(currentPrice - 1));

        List<PriceAlert> fired = alertService.checkAlerts(user.getUserId(), 1);
        assertEquals(1, fired.size());
        assertEquals("TCS", fired.get(0).getSymbol());

        assertTrue(alertService.getActiveAlerts(user.getUserId()).isEmpty(),
                "an alert fires only once");
        assertTrue(alertService.getAlerts(user.getUserId()).get(0).isTriggered());
    }

    @Test
    void anAlertFarFromThePriceStaysQuiet() throws Exception {
        Stock stock = TestSupport.stock("ITC");
        alertService.createAlert(user.getUserId(), stock.getStockId(),
                PriceAlert.Condition.ABOVE, "999999");

        assertTrue(alertService.checkAlerts(user.getUserId(), 1).isEmpty());
        assertEquals(1, alertService.getActiveAlerts(user.getUserId()).size());
    }

    @Test
    void advancingAMonthDoesNotSkipAnAlertMetOnAnIntermediateDay() throws Exception {
        Stock stock = TestSupport.stock("RELIANCE");
        int stockId = stock.getStockId();

        // find a day inside the next month whose price is higher than every later day in it
        double peak = 0;
        for (int d = 2; d <= 31; d++) {
            peak = Math.max(peak, TestSupport.historicalPrice(stockId, d));
        }
        double dayThirtyOne = TestSupport.historicalPrice(stockId, 31);

        alertService.createAlert(user.getUserId(), stockId,
                PriceAlert.Condition.ABOVE, String.valueOf(peak - 0.01));

        SimulationService.AdvanceResult result = simulationService.advanceMonth(user.getUserId());
        assertEquals(31, result.getNewDay());
        assertEquals(1, result.getTriggeredAlerts().size(),
                "the peak was reached during the month even if day 31 is lower ("
                        + dayThirtyOne + " vs peak " + peak + ")");
    }

    @Test
    void alertCreationValidatesTheTargetPrice() {
        int stockId = TestSupport.stock("SBIN").getStockId();
        assertThrows(InvalidQuantityException.class, () ->
                alertService.createAlert(user.getUserId(), stockId,
                        PriceAlert.Condition.ABOVE, "not-a-number"));
        assertThrows(InvalidQuantityException.class, () ->
                alertService.createAlert(user.getUserId(), stockId,
                        PriceAlert.Condition.BELOW, "-10"));
    }

    // =============== ReportService (file I/O) ===============

    @Test
    void portfolioReportIsWrittenAsReadableText() throws Exception {
        tradingService.buyStock(user.getUserId(), TestSupport.stock("TCS").getStockId(), 10);
        tradingService.buyStock(user.getUserId(), TestSupport.stock("HDFCBANK").getStockId(), 10);

        User refreshed = new UserDAO().findById(user.getUserId()).orElseThrow();
        Path file = new ReportService().exportPortfolio(refreshed);
        try {
            assertTrue(Files.exists(file));
            assertTrue(Files.size(file) > 200);

            String content = Files.readString(file);
            assertTrue(content.contains("PORTFOLIO REPORT"));
            assertTrue(content.contains(refreshed.getUsername()));
            assertTrue(content.contains("TCS"));
            assertTrue(content.contains("HDFCBANK"));
            assertTrue(content.contains("SECTOR ALLOCATION"));
            assertTrue(content.contains("HOLDINGS (2)"));
        } finally {
            ReportService.deleteQuietly(file);
        }
    }

    @Test
    void transactionReportListsEveryTrade() throws Exception {
        int stockId = TestSupport.stock("INFY").getStockId();
        tradingService.buyStock(user.getUserId(), stockId, 10);
        tradingService.sellStock(user.getUserId(), stockId, 4);

        User refreshed = new UserDAO().findById(user.getUserId()).orElseThrow();
        List<Transaction> transactions = new TransactionDAO().findByUserId(user.getUserId());
        Path file = new ReportService().exportTransactions(refreshed, transactions);
        try {
            String content = Files.readString(file);
            assertTrue(content.contains("TRANSACTION HISTORY"));
            assertTrue(content.contains("TRANSACTIONS (2)"));
            assertTrue(content.contains("BUY"));
            assertTrue(content.contains("SELL"));
            assertTrue(content.contains("Total bought"));
        } finally {
            ReportService.deleteQuietly(file);
        }
    }

    @Test
    void exportingAnEmptyPortfolioStillProducesAFile() throws Exception {
        Path file = new ReportService().exportPortfolio(user);
        try {
            assertTrue(Files.exists(file));
            assertTrue(Files.readString(file).contains("do not own any shares"));
        } finally {
            ReportService.deleteQuietly(file);
        }
    }

    // =============== StateService (serialization) ===============

    @Test
    void simulationStateSurvivesASaveAndLoadRoundTrip() throws Exception {
        StateService stateService = new StateService();
        tradingService.buyStock(user.getUserId(), TestSupport.stock("TCS").getStockId(), 10);
        simulationService.advance(user.getUserId(), 250);

        double balanceAtSave = new UserDAO().findById(user.getUserId()).orElseThrow().getBalance();
        Path file = stateService.saveState(user.getUserId());
        try {
            assertTrue(Files.exists(file));

            SimulationState read = stateService.readState().orElseThrow();
            assertEquals(user.getUserId(), read.getUserId());
            assertEquals(251, read.getSimulationDay());
            assertEquals(balanceAtSave, read.getBalance(), 0.01);
            assertEquals(10, read.getStockPrices().size(), "all ten prices are in the snapshot");
            assertNotNull(read.getSavedAt());

            // move on, then restore
            simulationService.advance(user.getUserId(), 100);
            new UserDAO().updateBalance(user.getUserId(), 1.0);
            assertEquals(351, simulationService.getCurrentDay(user.getUserId()));

            SimulationState restored = stateService.loadState(user.getUserId()).orElseThrow();
            assertEquals(251, simulationService.getCurrentDay(user.getUserId()));
            assertEquals(balanceAtSave,
                    new UserDAO().findById(user.getUserId()).orElseThrow().getBalance(), 0.01);
            assertNotNull(restored.getLoadNote());
        } finally {
            new StateService().deleteState();
        }
    }

    @Test
    void loadingWithNoSavedFileIsHandledGracefully() {
        StateService stateService = new StateService();
        stateService.deleteState();
        assertTrue(stateService.readState().isEmpty());
        assertTrue(stateService.loadState(user.getUserId()).isEmpty());
        assertFalse(stateService.stateExists());
    }

    // =============== goals ===============

    @Test
    void goalProgressFollowsTheLivePortfolioValue() throws Exception {
        Goal goal = new Goal(user.getUserId(), "Reach 1,50,000", 150000.0);
        assertTrue(new com.stocksim.dao.GoalDAO().save(goal));

        double value = portfolioService.getPortfolioValue(user.getUserId());
        assertEquals(TestSupport.STARTING_BALANCE, value, 0.01);
        assertEquals(value / 150000.0, goal.getProgress(value), 0.001);
        assertFalse(goal.isAchieved(value));
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}

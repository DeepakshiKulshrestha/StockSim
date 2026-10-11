package com.stocksim;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.stocksim.dao.GoalDAO;
import com.stocksim.dao.TransactionDAO;
import com.stocksim.dao.UserDAO;
import com.stocksim.model.Goal;
import com.stocksim.model.Holding;
import com.stocksim.model.PortfolioSummary;
import com.stocksim.model.PriceAlert;
import com.stocksim.model.Stock;
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

/**
 * Walks the whole documented user journey in order, as one continuous session:
 * register, log in, research, buy, advance time, manage risk, set a goal and an
 * alert, export the reports, then check the numbers still add up.
 */
@DisplayName("End-to-end user journey")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserJourneyTest {

    private static final String PASSWORD = "password123";

    private static final AuthService auth = new AuthService();
    private static final TradingService trading = new TradingService();
    private static final PortfolioService portfolio = new PortfolioService();
    private static final AnalysisService analysis = new AnalysisService();
    private static final SimulationService simulation = new SimulationService();
    private static final RiskService risk = new RiskService();
    private static final AlertService alerts = new AlertService();

    private static String username;
    private static User user;
    private static int tcsId;
    private static int hdfcId;
    private static int itcId;
    private static double capitalAtStart;

    @BeforeAll
    static void setUp() {
        TestSupport.resetMarketToDayOne();
        username = "journey" + System.nanoTime();
        tcsId = TestSupport.stock("TCS").getStockId();
        hdfcId = TestSupport.stock("HDFCBANK").getStockId();
        itcId = TestSupport.stock("ITC").getStockId();
    }

    @AfterAll
    static void tearDown() {
        TestSupport.deleteTestUser(user);
        new StateService().deleteState();
        TestSupport.resetMarketToDayOne();
    }

    @Test
    @Order(1)
    @DisplayName("1. register and receive the virtual starting capital")
    void register() {
        user = auth.register("Journey Tester", username, username + "@example.com",
                PASSWORD, PASSWORD);
        capitalAtStart = user.getBalance();

        assertEquals(100000.00, capitalAtStart, 0.001);
        assertEquals(1, user.getSimDay());
    }

    @Test
    @Order(2)
    @DisplayName("2. log back in with the same credentials")
    void logIn() throws Exception {
        User loggedIn = auth.login(username, PASSWORD);
        assertEquals(user.getUserId(), loggedIn.getUserId());
        simulation.syncPricesToUser(loggedIn.getUserId());
        user = loggedIn;
    }

    @Test
    @Order(3)
    @DisplayName("3. research a stock before buying it")
    void research() {
        var report = analysis.analyse(tcsId, simulation.getCurrentDay(user.getUserId()));

        assertEquals("TCS", report.getSymbol());
        assertTrue(report.getCurrentPrice() > 0);
        assertNotNull(report.getRiskProfile());
        assertFalse(analysis.getPriceSeries(tcsId, 1, 30).isEmpty());
    }

    @Test
    @Order(4)
    @DisplayName("4. buy three stocks across three sectors")
    void buy() throws Exception {
        trading.buyStock(user.getUserId(), tcsId, 40);
        trading.buyStock(user.getUserId(), hdfcId, 60);
        trading.buyStock(user.getUserId(), itcId, 80);

        List<Holding> holdings = portfolio.getHoldings(user.getUserId());
        assertEquals(3, holdings.size());

        PortfolioSummary summary = portfolio.getPortfolioSummary(user.getUserId());
        assertTrue(summary.getCash() < capitalAtStart, "cash must have gone down");
        assertEquals(capitalAtStart, summary.getTotalValue(), 0.10,
                "immediately after buying, nothing has been gained or lost");
        assertEquals(3, new TransactionDAO().countByUser(user.getUserId()));
    }

    @Test
    @Order(5)
    @DisplayName("5. set an investment goal and a price alert")
    void setGoalAndAlert() throws Exception {
        Goal goal = new Goal(user.getUserId(), "Reach 1,50,000", 150000.0);
        assertTrue(new GoalDAO().save(goal));
        assertTrue(goal.getProgress(portfolio.getPortfolioValue(user.getUserId())) > 0.5);

        // Target the highest TCS close in the coming month, so the alert is guaranteed to
        // fire during the advance. (Picking "just below today's price" would be a coin flip:
        // simulation day 1 is January 2008 and TCS fell for most of that month.)
        double peakInMonth = 0;
        for (int day = 2; day <= 31; day++) {
            peakInMonth = Math.max(peakInMonth, TestSupport.historicalPrice(tcsId, day));
        }
        alerts.createAlert(user.getUserId(), tcsId, PriceAlert.Condition.ABOVE,
                String.valueOf(peakInMonth - 0.01));
        assertEquals(1, alerts.getActiveAlerts(user.getUserId()).size());
    }

    @Test
    @Order(6)
    @DisplayName("6. advance a month - prices move, events fire, alerts trigger")
    void advanceTime() {
        SimulationService.AdvanceResult result = simulation.advanceMonth(user.getUserId());

        assertEquals(31, result.getNewDay());
        assertEquals(1, result.getTriggeredAlerts().size(), "the TCS alert must have fired");
        assertFalse(result.getEvents().isEmpty(), "scripted market news fires in this window");

        // the market really did reprice
        assertEquals(TestSupport.historicalPrice(tcsId, 31),
                TestSupport.stock("TCS").getCurrentPrice(), 0.01);
        assertTrue(alerts.getActiveAlerts(user.getUserId()).isEmpty());
    }

    @Test
    @Order(7)
    @DisplayName("7. the portfolio has re-valued and P/L is now non-zero")
    void portfolioRevalues() {
        PortfolioSummary summary = portfolio.getPortfolioSummary(user.getUserId());

        double expectedHoldings = 0;
        for (Holding holding : portfolio.getHoldings(user.getUserId())) {
            assertEquals(TestSupport.historicalPrice(holding.getStockId(), 31),
                    holding.getCurrentPrice(), 0.01);
            expectedHoldings += holding.getQuantity() * holding.getCurrentPrice();
        }
        assertEquals(expectedHoldings, summary.getHoldingsValue(), 0.10);
        assertEquals(summary.getCash() + summary.getHoldingsValue(), summary.getTotalValue(), 0.02);
        assertEquals(summary.getHoldingsValue() - summary.getInvested(),
                summary.getProfitLoss(), 0.02);
        assertNotEquals(0.0, summary.getProfitLoss(), "prices moved over a month");
    }

    @Test
    @Order(8)
    @DisplayName("8. risk and diversification reflect the three sectors held")
    void checkRisk() {
        var sectors = risk.getSectorAllocation(user.getUserId());
        assertEquals(3, sectors.size(), "IT, Banking and FMCG");
        assertEquals(100.0, sectors.values().stream().mapToDouble(Double::doubleValue).sum(), 0.1);
        assertNotNull(risk.getDiversificationLevel(user.getUserId()));
        assertNotNull(risk.getLargestHolding(user.getUserId()));
    }

    @Test
    @Order(9)
    @DisplayName("9. sell part of a holding and bank the cash")
    void sell() throws Exception {
        double cashBefore = new UserDAO().findById(user.getUserId()).orElseThrow().getBalance();
        double price = TestSupport.stock("ITC").getCurrentPrice();

        Transaction txn = trading.sellStock(user.getUserId(), itcId, 30);

        assertEquals(30, txn.getQuantity());
        assertEquals(50, portfolio.getHoldings(user.getUserId()).stream()
                .filter(h -> h.getStockId() == itcId).findFirst().orElseThrow().getQuantity());
        assertEquals(cashBefore + price * 30,
                new UserDAO().findById(user.getUserId()).orElseThrow().getBalance(), 0.02);
        assertEquals(4, new TransactionDAO().countByUser(user.getUserId()));
    }

    @Test
    @Order(10)
    @DisplayName("10. export the portfolio and transaction reports")
    void exportReports() throws Exception {
        User refreshed = new UserDAO().findById(user.getUserId()).orElseThrow();
        ReportService reports = new ReportService();

        Path portfolioFile = reports.exportPortfolio(refreshed);
        Path transactionFile = reports.exportTransactions(refreshed,
                new TransactionDAO().findByUserId(user.getUserId()));
        try {
            String portfolioText = Files.readString(portfolioFile);
            assertTrue(portfolioText.contains("TCS"));
            assertTrue(portfolioText.contains("HDFCBANK"));
            assertTrue(portfolioText.contains("Simulation day  : 31"));

            String transactionText = Files.readString(transactionFile);
            assertTrue(transactionText.contains("TRANSACTIONS (4)"));
            assertTrue(transactionText.contains("SELL"));
        } finally {
            ReportService.deleteQuietly(portfolioFile);
            ReportService.deleteQuietly(transactionFile);
        }
    }

    @Test
    @Order(11)
    @DisplayName("11. save the simulation, move on, then restore it")
    void saveAndRestore() throws Exception {
        StateService state = new StateService();
        state.saveState(user.getUserId());

        simulation.advance(user.getUserId(), 60);
        assertEquals(91, simulation.getCurrentDay(user.getUserId()));

        state.loadState(user.getUserId());
        assertEquals(31, simulation.getCurrentDay(user.getUserId()),
                "the snapshot put the simulation back where it was saved");
        assertEquals(TestSupport.historicalPrice(tcsId, 31),
                TestSupport.stock("TCS").getCurrentPrice(), 0.01);
    }

    @Test
    @Order(12)
    @DisplayName("12. the books balance: cash + holdings accounts for every trade")
    void booksBalance() {
        PortfolioSummary summary = portfolio.getPortfolioSummary(user.getUserId());

        double netSpent = 0.0;
        for (Transaction txn : new TransactionDAO().findByUserId(user.getUserId())) {
            netSpent += txn.isBuy() ? txn.getTotalAmount() : -txn.getTotalAmount();
        }
        assertEquals(capitalAtStart - netSpent, summary.getCash(), 0.05,
                "cash must equal the starting capital minus everything net spent");

        assertEquals(3, portfolio.getBuyCount(user.getUserId()));
        assertEquals(1, portfolio.getSellCount(user.getUserId()));
        assertEquals(4, portfolio.getTotalTrades(user.getUserId()));

        Stock tcs = TestSupport.stock("TCS");
        assertTrue(tcs.getCurrentPrice() > 0);
    }
}

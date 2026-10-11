package com.stocksim;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.stocksim.dao.AlertDAO;
import com.stocksim.dao.GoalDAO;
import com.stocksim.dao.HoldingDAO;
import com.stocksim.dao.MarketEventDAO;
import com.stocksim.dao.StockDAO;
import com.stocksim.dao.StockHistoryDAO;
import com.stocksim.dao.TransactionDAO;
import com.stocksim.dao.UserDAO;
import com.stocksim.model.Goal;
import com.stocksim.model.Holding;
import com.stocksim.model.MarketEvent;
import com.stocksim.model.PriceAlert;
import com.stocksim.model.Stock;
import com.stocksim.model.StockPrice;
import com.stocksim.model.Transaction;
import com.stocksim.model.User;
import com.stocksim.util.DBConnection;

/** Verifies the schema, the seeded Kaggle data and every DAO against the real MySQL database. */
@DisplayName("Database schema, seed data and DAO layer")
class DatabaseAndDaoTest {

    private static final int EXPECTED_STOCKS = 10;

    private User user;

    @BeforeAll
    static void databaseMustBeReachable() {
        assertTrue(DBConnection.testConnection(),
                "MySQL must be running and the stocksim database must exist");
    }

    @BeforeEach
    void setUp() {
        user = TestSupport.createTestUser();
    }

    @AfterEach
    void tearDown() {
        TestSupport.deleteTestUser(user);
    }

    // ---------------- seed data ----------------

    @Test
    void allTenStocksAreSeededWithSectors() {
        List<Stock> stocks = new StockDAO().findAll();
        assertEquals(EXPECTED_STOCKS, stocks.size());

        for (String symbol : List.of("TCS", "INFY", "RELIANCE", "HDFCBANK", "ITC",
                                     "WIPRO", "ICICIBANK", "TATAMOTORS", "SBIN", "HINDUNILVR")) {
            Optional<Stock> stock = new StockDAO().findBySymbol(symbol);
            assertTrue(stock.isPresent(), symbol + " should be seeded");
            assertNotNull(stock.get().getSector());
            assertFalse(stock.get().getSector().isBlank());
        }

        Set<String> sectors = new StockDAO().findAllSectors();
        assertTrue(sectors.containsAll(Set.of("IT", "Banking", "Energy", "FMCG", "Auto")));
    }

    @Test
    void historicalPricesWereImportedFromTheCsv() {
        StockHistoryDAO historyDAO = new StockHistoryDAO();
        int lastDay = historyDAO.findMaxSimulationDay();

        assertTrue(lastDay > 1000, "expected thousands of trading days, got " + lastDay);
        assertEquals(EXPECTED_STOCKS * lastDay, TestSupport.countRows("stock_history"),
                "every stock must have a price for every simulation day");
    }

    @Test
    void everyStockHasAPriceOnTheSameSimulationDay() {
        // this is what makes portfolio values meaningful: day N is one real date for all stocks
        StockHistoryDAO historyDAO = new StockHistoryDAO();
        for (int day : new int[] { 1, 100, 1000, historyDAO.findMaxSimulationDay() }) {
            for (Stock stock : new StockDAO().findAll()) {
                Optional<StockPrice> price = historyDAO.findByStockAndDay(stock.getStockId(), day);
                assertTrue(price.isPresent(),
                        stock.getSymbol() + " has no price on simulation day " + day);
                assertTrue(price.get().getPrice() > 0);
                assertNotNull(price.get().getRecordedAt());
            }
        }
    }

    @Test
    void theSameSimulationDayIsTheSameCalendarDateForEveryStock() {
        StockHistoryDAO historyDAO = new StockHistoryDAO();
        int day = 500;
        java.time.LocalDate expected = null;
        for (Stock stock : new StockDAO().findAll()) {
            java.time.LocalDate actual = historyDAO.findByStockAndDay(stock.getStockId(), day)
                    .orElseThrow().getRecordedAt();
            if (expected == null) {
                expected = actual;
            } else {
                assertEquals(expected, actual,
                        stock.getSymbol() + " is misaligned on simulation day " + day);
            }
        }
    }

    @Test
    void marketEventsAreSeeded() {
        List<MarketEvent> all = new MarketEventDAO().findAll();
        assertFalse(all.isEmpty());
        assertTrue(all.stream().allMatch(e -> e.getSimulationDay() > 0));
        assertFalse(new MarketEventDAO().findInRange(0, 60).isEmpty());
    }

    // ---------------- UserDAO ----------------

    @Test
    void userDaoSavesFindsAndUpdates() {
        UserDAO userDAO = new UserDAO();

        assertTrue(user.getUserId() > 0, "the generated key must be written back");
        assertTrue(userDAO.findById(user.getUserId()).isPresent());
        assertTrue(userDAO.findByUsername(user.getUsername()).isPresent());
        assertTrue(userDAO.findByEmail(user.getEmail()).isPresent());
        assertTrue(userDAO.findByUsernameOrEmail(user.getEmail()).isPresent());
        assertTrue(userDAO.existsByUsername(user.getUsername()));
        assertFalse(userDAO.existsByUsername("definitely_not_a_real_user_xyz"));

        assertTrue(userDAO.updateBalance(user.getUserId(), 55555.55));
        assertEquals(55555.55, userDAO.findById(user.getUserId()).orElseThrow().getBalance(), 0.001);

        assertTrue(userDAO.updateSimDay(user.getUserId(), 42));
        assertEquals(42, userDAO.findById(user.getUserId()).orElseThrow().getSimDay());
    }

    @Test
    void userDaoReturnsEmptyOptionalRatherThanNull() {
        assertEquals(Optional.empty(), new UserDAO().findByUsername("no_such_user_at_all"));
        assertEquals(Optional.empty(), new UserDAO().findById(-1));
    }

    // ---------------- HoldingDAO ----------------

    @Test
    void holdingUpsertAddsSharesAndRecomputesAverageCost() {
        HoldingDAO holdingDAO = new HoldingDAO();
        int stockId = TestSupport.stock("TCS").getStockId();

        assertTrue(holdingDAO.upsert(user.getUserId(), stockId, 10, 100.0));
        Holding first = holdingDAO.findByUserAndStock(user.getUserId(), stockId).orElseThrow();
        assertEquals(10, first.getQuantity());
        assertEquals(100.0, first.getAverageBuyPrice(), 0.01);

        // second buy at a different price must blend into a weighted average
        assertTrue(holdingDAO.upsert(user.getUserId(), stockId, 10, 200.0));
        Holding merged = holdingDAO.findByUserAndStock(user.getUserId(), stockId).orElseThrow();
        assertEquals(20, merged.getQuantity());
        assertEquals(150.0, merged.getAverageBuyPrice(), 0.01);

        assertEquals(20, holdingDAO.getQuantity(user.getUserId(), stockId));
        assertNotNull(merged.getSymbol(), "the stock join must populate display fields");
        assertEquals("TCS", merged.getSymbol());
    }

    @Test
    void holdingIsDeletedWhenItReachesZeroShares() throws Exception {
        HoldingDAO holdingDAO = new HoldingDAO();
        int stockId = TestSupport.stock("INFY").getStockId();
        holdingDAO.upsert(user.getUserId(), stockId, 5, 100.0);

        try (var conn = DBConnection.getInstance().newConnection()) {
            assertTrue(holdingDAO.reduce(conn, user.getUserId(), stockId, 2));
            assertEquals(3, holdingDAO.getQuantity(user.getUserId(), stockId));

            assertTrue(holdingDAO.reduce(conn, user.getUserId(), stockId, 3));
            assertTrue(holdingDAO.findByUserAndStock(user.getUserId(), stockId).isEmpty(),
                    "a zero-share holding must be removed, not left as a 0 row");
        }
    }

    @Test
    void reduceRefusesToOversell() throws Exception {
        HoldingDAO holdingDAO = new HoldingDAO();
        int stockId = TestSupport.stock("WIPRO").getStockId();
        holdingDAO.upsert(user.getUserId(), stockId, 5, 100.0);

        try (var conn = DBConnection.getInstance().newConnection()) {
            assertFalse(holdingDAO.reduce(conn, user.getUserId(), stockId, 6),
                    "reduce must be a guard as well as an update");
            assertEquals(5, holdingDAO.getQuantity(user.getUserId(), stockId));
        }
    }

    // ---------------- TransactionDAO ----------------

    @Test
    void transactionDaoSavesAndFiltersByType() {
        TransactionDAO txnDAO = new TransactionDAO();
        int stockId = TestSupport.stock("ITC").getStockId();

        assertTrue(txnDAO.save(new Transaction(user.getUserId(), stockId,
                Transaction.Type.BUY, 10, 200.0, 1)));
        assertTrue(txnDAO.save(new Transaction(user.getUserId(), stockId,
                Transaction.Type.SELL, 4, 220.0, 2)));

        assertEquals(2, txnDAO.countByUser(user.getUserId()));
        assertEquals(1, txnDAO.countByUserAndType(user.getUserId(), Transaction.Type.BUY));
        assertEquals(1, txnDAO.countByUserAndType(user.getUserId(), Transaction.Type.SELL));

        List<Transaction> all = txnDAO.findByUserId(user.getUserId());
        assertEquals(2, all.size());
        assertNotNull(all.get(0).getTransactionDate(), "transaction_date defaults to now()");
        assertEquals("ITC", all.get(0).getSymbol());
        assertEquals(2000.0,
                txnDAO.findByUserAndType(user.getUserId(), Transaction.Type.BUY)
                      .get(0).getTotalAmount(), 0.01);
    }

    // ---------------- GoalDAO ----------------

    @Test
    void goalDaoSavesFindsAndDeletes() {
        GoalDAO goalDAO = new GoalDAO();
        Goal goal = new Goal(user.getUserId(), "Reach 1,50,000", 150000.0);

        assertTrue(goalDAO.save(goal));
        assertTrue(goal.getGoalId() > 0);
        assertEquals(1, goalDAO.findByUserId(user.getUserId()).size());
        assertEquals("Reach 1,50,000", goalDAO.findById(goal.getGoalId()).orElseThrow().getGoalName());

        assertTrue(goalDAO.delete(goal.getGoalId()));
        assertTrue(goalDAO.findByUserId(user.getUserId()).isEmpty());
    }

    // ---------------- AlertDAO ----------------

    @Test
    void alertDaoHandlesTheReservedWordColumn() {
        AlertDAO alertDAO = new AlertDAO();
        int stockId = TestSupport.stock("SBIN").getStockId();

        PriceAlert alert = new PriceAlert(user.getUserId(), stockId,
                PriceAlert.Condition.ABOVE, 500.0);
        assertTrue(alertDAO.save(alert), "`condition` is reserved in MySQL and must be quoted");
        assertTrue(alert.getAlertId() > 0);

        PriceAlert loaded = alertDAO.findByUserId(user.getUserId()).get(0);
        assertEquals(PriceAlert.Condition.ABOVE, loaded.getCondition());
        assertEquals(500.0, loaded.getTargetPrice(), 0.01);
        assertEquals("SBIN", loaded.getSymbol());
        assertFalse(loaded.isTriggered());

        assertEquals(1, alertDAO.findActive(user.getUserId()).size());
        assertTrue(alertDAO.markTriggered(alert.getAlertId()));
        assertTrue(alertDAO.findActive(user.getUserId()).isEmpty());
        assertTrue(alertDAO.findByUserId(user.getUserId()).get(0).isTriggered());

        assertTrue(alertDAO.delete(alert.getAlertId()));
    }

    // ---------------- StockHistoryDAO windows ----------------

    @Test
    void historyWindowsReturnOldestFirstAndRespectTheUpperBound() {
        StockHistoryDAO historyDAO = new StockHistoryDAO();
        int stockId = TestSupport.stock("RELIANCE").getStockId();

        List<StockPrice> window = historyDAO.findLastNDays(stockId, 100, 30);
        assertEquals(30, window.size());
        assertEquals(71, window.get(0).getSimulationDay());
        assertEquals(100, window.get(window.size() - 1).getSimulationDay(),
                "the window must end on the requested day, oldest first");

        // near the start of the series the window is simply shorter
        assertEquals(5, historyDAO.findLastNDays(stockId, 5, 30).size());

        double high = historyDAO.findMaxPrice(stockId, 1, 100);
        double low = historyDAO.findMinPrice(stockId, 1, 100);
        assertTrue(high >= low && low > 0);
    }

    @Test
    void cascadingDeleteRemovesEverythingBelongingToAUser() {
        int stockId = TestSupport.stock("ICICIBANK").getStockId();
        new HoldingDAO().upsert(user.getUserId(), stockId, 3, 100.0);
        new GoalDAO().save(new Goal(user.getUserId(), "temp", 1000.0));
        new AlertDAO().save(new PriceAlert(user.getUserId(), stockId,
                PriceAlert.Condition.BELOW, 10.0));
        new TransactionDAO().save(new Transaction(user.getUserId(), stockId,
                Transaction.Type.BUY, 3, 100.0, 1));

        int userId = user.getUserId();
        assertTrue(new UserDAO().delete(userId));

        assertTrue(new HoldingDAO().findByUserId(userId).isEmpty());
        assertTrue(new GoalDAO().findByUserId(userId).isEmpty());
        assertTrue(new AlertDAO().findByUserId(userId).isEmpty());
        assertTrue(new TransactionDAO().findByUserId(userId).isEmpty());

        user = null;   // already gone, nothing for tearDown to do
    }
}

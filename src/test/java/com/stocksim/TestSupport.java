package com.stocksim;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;

import com.stocksim.dao.StockDAO;
import com.stocksim.dao.UserDAO;
import com.stocksim.model.Stock;
import com.stocksim.model.User;
import com.stocksim.util.DBConnection;
import com.stocksim.util.PasswordUtil;

/**
 * Helpers shared by the integration tests: throwaway users that are removed
 * again afterwards, and lookups for the seeded stocks.
 */
public final class TestSupport {

    public static final double STARTING_BALANCE = 100000.00;

    private static final AtomicInteger COUNTER = new AtomicInteger();

    private TestSupport() {
    }

    /** Creates a uniquely named test user directly in the database. */
    public static User createTestUser() {
        return createTestUser(STARTING_BALANCE);
    }

    public static User createTestUser(double balance) {
        String suffix = System.nanoTime() + "_" + COUNTER.incrementAndGet();
        User user = new User("Test User", "t_" + suffix, "t_" + suffix + "@example.com",
                PasswordUtil.hash("password123"), balance);
        user.setSimDay(1);
        if (!new UserDAO().save(user)) {
            throw new IllegalStateException("Could not create the test user");
        }
        return user;
    }

    /** ON DELETE CASCADE removes the user's holdings, trades, goals and alerts too. */
    public static void deleteTestUser(User user) {
        if (user != null && user.getUserId() > 0) {
            new UserDAO().delete(user.getUserId());
        }
    }

    public static Stock stock(String symbol) {
        return new StockDAO().findBySymbol(symbol)
                .orElseThrow(() -> new IllegalStateException(
                        "Stock " + symbol + " is missing - run database.sql and seed the data"));
    }

    /** Historical close for a stock on a given simulation day, read straight from SQL. */
    public static double historicalPrice(int stockId, int simulationDay) {
        String sql = "SELECT price FROM stock_history WHERE stock_id = ? AND simulation_day = ?";
        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, stockId);
            ps.setInt(2, simulationDay);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0.0;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("historicalPrice failed", e);
        }
    }

    public static int countRows(String table) {
        try (Connection conn = DBConnection.get();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new IllegalStateException("countRows(" + table + ") failed", e);
        }
    }

    /** Puts the whole market back on simulation day 1 so tests start from a known state. */
    public static void resetMarketToDayOne() {
        new StockDAO().syncPricesToDay(1);
    }
}

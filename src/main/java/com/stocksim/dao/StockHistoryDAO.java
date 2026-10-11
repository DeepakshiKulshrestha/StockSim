package com.stocksim.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.stocksim.model.StockPrice;
import com.stocksim.util.DBConnection;

/** All SQL for the {@code stock_history} table - the source of every price in the app. */
public class StockHistoryDAO {

    private static final Logger LOGGER = Logger.getLogger(StockHistoryDAO.class.getName());

    /** Full price series for one stock, oldest first. */
    public List<StockPrice> findByStockId(int stockId) {
        return findMany("SELECT * FROM stock_history WHERE stock_id = ? ORDER BY simulation_day",
                stockId);
    }

    /** The close for one stock on one simulation day. */
    public Optional<StockPrice> findByStockAndDay(int stockId, int simulationDay) {
        List<StockPrice> results = findMany(
                "SELECT * FROM stock_history WHERE stock_id = ? AND simulation_day = ?",
                stockId, simulationDay);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /** Just the price, or 0.0 when that day has no data. */
    public double findPrice(int stockId, int simulationDay) {
        return findByStockAndDay(stockId, simulationDay).map(StockPrice::getPrice).orElse(0.0);
    }

    /**
     * The last {@code days} closes up to and including {@code upToDay}, oldest first.
     * This is what feeds the LineChart on the stock details screen.
     */
    public List<StockPrice> findLastNDays(int stockId, int upToDay, int days) {
        String sql = "SELECT * FROM stock_history "
                   + "WHERE stock_id = ? AND simulation_day <= ? "
                   + "ORDER BY simulation_day DESC LIMIT ?";
        List<StockPrice> descending = findMany(sql, stockId, upToDay, Math.max(1, days));
        Collections.reverse(descending);
        return descending;
    }

    /** Highest close in the window - used for the 52-week high. */
    public double findMaxPrice(int stockId, int fromDay, int toDay) {
        return aggregate("SELECT MAX(price) FROM stock_history "
                       + "WHERE stock_id = ? AND simulation_day BETWEEN ? AND ?",
                stockId, fromDay, toDay);
    }

    /** Lowest close in the window - used for the 52-week low. */
    public double findMinPrice(int stockId, int fromDay, int toDay) {
        return aggregate("SELECT MIN(price) FROM stock_history "
                       + "WHERE stock_id = ? AND simulation_day BETWEEN ? AND ?",
                stockId, fromDay, toDay);
    }

    /** Highest simulation day that exists, i.e. the last day the simulation can reach. */
    public int findMaxSimulationDay() {
        String sql = "SELECT MAX(simulation_day) FROM stock_history";
        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "findMaxSimulationDay failed", e);
            return 0;
        }
    }

    public OptionalInt findMaxDayForStock(int stockId) {
        String sql = "SELECT MAX(simulation_day) FROM stock_history WHERE stock_id = ?";
        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, stockId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return OptionalInt.of(rs.getInt(1));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "findMaxDayForStock failed", e);
        }
        return OptionalInt.empty();
    }

    public boolean save(StockPrice price) {
        String sql = "INSERT INTO stock_history (stock_id, price, recorded_at, simulation_day) "
                   + "VALUES (?, ?, ?, ?)";
        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, price.getStockId());
            ps.setDouble(2, price.getPrice());
            ps.setDate(3, java.sql.Date.valueOf(price.getRecordedAt()));
            ps.setInt(4, price.getSimulationDay());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "save(StockPrice) failed", e);
            return false;
        }
    }

    // ---------------------------------------------------------------

    private double aggregate(String sql, Object... params) {
        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0.0;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Aggregate query failed: " + sql, e);
            return 0.0;
        }
    }

    private List<StockPrice> findMany(String sql, Object... params) {
        List<StockPrice> prices = new ArrayList<>();
        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    prices.add(map(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Query failed: " + sql, e);
        }
        return prices;
    }

    private StockPrice map(ResultSet rs) throws SQLException {
        StockPrice price = new StockPrice();
        price.setHistoryId(rs.getInt("history_id"));
        price.setStockId(rs.getInt("stock_id"));
        price.setPrice(rs.getDouble("price"));
        java.sql.Date date = rs.getDate("recorded_at");
        if (date != null) {
            price.setRecordedAt(date.toLocalDate());
        }
        price.setSimulationDay(rs.getInt("simulation_day"));
        return price;
    }
}

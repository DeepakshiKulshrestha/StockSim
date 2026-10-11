package com.stocksim.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.stocksim.model.Stock;
import com.stocksim.util.DBConnection;

/** All SQL for the {@code stocks} table. */
public class StockDAO {

    private static final Logger LOGGER = Logger.getLogger(StockDAO.class.getName());

    public List<Stock> findAll() {
        return findMany("SELECT * FROM stocks ORDER BY symbol");
    }

    public List<Stock> findBySector(String sector) {
        return findMany("SELECT * FROM stocks WHERE sector = ? ORDER BY symbol", sector);
    }

    public Optional<Stock> findById(int stockId) {
        return findOne("SELECT * FROM stocks WHERE stock_id = ?", stockId);
    }

    public Optional<Stock> findBySymbol(String symbol) {
        return findOne("SELECT * FROM stocks WHERE symbol = ?", symbol);
    }

    /** Distinct sectors, used to populate the market screen's filter ComboBox. */
    public Set<String> findAllSectors() {
        Set<String> sectors = new LinkedHashSet<>();
        String sql = "SELECT DISTINCT sector FROM stocks ORDER BY sector";
        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                sectors.add(rs.getString("sector"));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "findAllSectors failed", e);
        }
        return sectors;
    }

    public boolean updatePrice(int stockId, double newPrice) {
        String sql = "UPDATE stocks SET current_price = ? WHERE stock_id = ?";
        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, newPrice);
            ps.setInt(2, stockId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "updatePrice failed for stock " + stockId, e);
            return false;
        }
    }

    /**
     * Repoints every stock's current_price at its historical close for the given
     * simulation day, in one statement. Called whenever simulated time moves.
     */
    public int syncPricesToDay(int simulationDay) {
        String sql = "UPDATE stocks s "
                   + "JOIN stock_history h ON h.stock_id = s.stock_id AND h.simulation_day = ? "
                   + "SET s.current_price = h.price";
        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, simulationDay);
            int updated = ps.executeUpdate();
            LOGGER.info("Synced " + updated + " stock prices to simulation day " + simulationDay);
            return updated;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "syncPricesToDay failed for day " + simulationDay, e);
            return 0;
        }
    }

    // ---------------------------------------------------------------

    private List<Stock> findMany(String sql, Object... params) {
        List<Stock> stocks = new ArrayList<>();
        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    stocks.add(map(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Query failed: " + sql, e);
        }
        return stocks;
    }

    private Optional<Stock> findOne(String sql, Object... params) {
        List<Stock> results = findMany(sql, params);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    private Stock map(ResultSet rs) throws SQLException {
        Stock stock = new Stock();
        stock.setStockId(rs.getInt("stock_id"));
        stock.setSymbol(rs.getString("symbol"));
        stock.setCompanyName(rs.getString("company_name"));
        stock.setSector(rs.getString("sector"));
        stock.setCurrentPrice(rs.getDouble("current_price"));
        return stock;
    }
}

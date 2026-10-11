package com.stocksim.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.stocksim.model.Holding;
import com.stocksim.util.DBConnection;

/**
 * All SQL for the {@code holdings} table.
 *
 * The transaction-aware overloads (the ones taking a Connection) exist so that
 * TradingService can run a buy or a sell as a single commit-or-rollback unit.
 */
public class HoldingDAO {

    private static final Logger LOGGER = Logger.getLogger(HoldingDAO.class.getName());

    /** Holdings joined with stock details and the live price, for the portfolio screen. */
    private static final String SELECT_WITH_STOCK =
            "SELECT h.*, s.symbol, s.company_name, s.sector, s.current_price "
          + "FROM holdings h JOIN stocks s ON s.stock_id = h.stock_id ";

    public List<Holding> findByUserId(int userId) {
        return findMany(SELECT_WITH_STOCK + "WHERE h.user_id = ? ORDER BY s.symbol", userId);
    }

    public Optional<Holding> findByUserAndStock(int userId, int stockId) {
        List<Holding> results =
                findMany(SELECT_WITH_STOCK + "WHERE h.user_id = ? AND h.stock_id = ?", userId, stockId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /** Shares of one stock the user currently owns (0 when none). */
    public int getQuantity(int userId, int stockId) {
        return findByUserAndStock(userId, stockId).map(Holding::getQuantity).orElse(0);
    }

    // ---------------- transaction-aware operations ----------------

    /**
     * Inserts the holding or, if the user already owns the stock, adds the shares and
     * recomputes the weighted average buy price - all in one statement.
     */
    public boolean upsert(Connection conn, int userId, int stockId, int quantity, double price)
            throws SQLException {

        String sql = "INSERT INTO holdings (user_id, stock_id, quantity, average_buy_price) "
                   + "VALUES (?, ?, ?, ?) "
                   + "ON DUPLICATE KEY UPDATE "
                   + "  average_buy_price = ((quantity * average_buy_price) + VALUES(quantity) * VALUES(average_buy_price)) "
                   + "                      / (quantity + VALUES(quantity)), "
                   + "  quantity = quantity + VALUES(quantity)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, stockId);
            ps.setInt(3, quantity);
            ps.setDouble(4, price);
            return ps.executeUpdate() > 0;
        }
    }

    /** Removes shares; deletes the row entirely when the holding reaches zero. */
    public boolean reduce(Connection conn, int userId, int stockId, int quantity) throws SQLException {
        String update = "UPDATE holdings SET quantity = quantity - ? "
                      + "WHERE user_id = ? AND stock_id = ? AND quantity >= ?";
        try (PreparedStatement ps = conn.prepareStatement(update)) {
            ps.setInt(1, quantity);
            ps.setInt(2, userId);
            ps.setInt(3, stockId);
            ps.setInt(4, quantity);
            if (ps.executeUpdate() == 0) {
                return false;            // guard: the user did not own enough shares
            }
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM holdings WHERE user_id = ? AND stock_id = ? AND quantity <= 0")) {
            ps.setInt(1, userId);
            ps.setInt(2, stockId);
            ps.executeUpdate();
        }
        return true;
    }

    /** Non-transactional convenience used by tests and setup code. */
    public boolean upsert(int userId, int stockId, int quantity, double price) {
        try (Connection conn = DBConnection.get()) {
            return upsert(conn, userId, stockId, quantity, price);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "upsert failed for user " + userId + " stock " + stockId, e);
            return false;
        }
    }

    public boolean delete(int userId, int stockId) {
        String sql = "DELETE FROM holdings WHERE user_id = ? AND stock_id = ?";
        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, stockId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "delete holding failed", e);
            return false;
        }
    }

    public int deleteAllForUser(int userId) {
        String sql = "DELETE FROM holdings WHERE user_id = ?";
        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "deleteAllForUser failed", e);
            return 0;
        }
    }

    // ---------------------------------------------------------------

    private List<Holding> findMany(String sql, Object... params) {
        List<Holding> holdings = new ArrayList<>();
        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    holdings.add(map(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Query failed: " + sql, e);
        }
        return holdings;
    }

    private Holding map(ResultSet rs) throws SQLException {
        Holding holding = new Holding();
        holding.setHoldingId(rs.getInt("holding_id"));
        holding.setUserId(rs.getInt("user_id"));
        holding.setStockId(rs.getInt("stock_id"));
        holding.setQuantity(rs.getInt("quantity"));
        holding.setAverageBuyPrice(rs.getDouble("average_buy_price"));
        holding.setSymbol(rs.getString("symbol"));
        holding.setCompanyName(rs.getString("company_name"));
        holding.setSector(rs.getString("sector"));
        holding.setCurrentPrice(rs.getDouble("current_price"));
        return holding;
    }
}

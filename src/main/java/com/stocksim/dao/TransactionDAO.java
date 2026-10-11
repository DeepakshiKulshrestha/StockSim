package com.stocksim.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.stocksim.model.Transaction;
import com.stocksim.util.DBConnection;

/** All SQL for the {@code transactions} table. */
public class TransactionDAO {

    private static final Logger LOGGER = Logger.getLogger(TransactionDAO.class.getName());

    private static final String SELECT_WITH_STOCK =
            "SELECT t.*, s.symbol, s.company_name "
          + "FROM transactions t JOIN stocks s ON s.stock_id = t.stock_id ";

    private static final String INSERT_SQL =
            "INSERT INTO transactions "
          + "(user_id, stock_id, transaction_type, quantity, price, total_amount, simulation_day) "
          + "VALUES (?, ?, ?, ?, ?, ?, ?)";

    /** Insert that joins the caller's transaction (buy/sell). */
    public boolean save(Connection conn, Transaction txn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, txn);
            if (ps.executeUpdate() == 0) {
                return false;
            }
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    txn.setTransactionId(keys.getInt(1));
                }
            }
            return true;
        }
    }

    /** Stand-alone insert (auto-commit). */
    public boolean save(Transaction txn) {
        try (Connection conn = DBConnection.get()) {
            return save(conn, txn);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "save(Transaction) failed", e);
            return false;
        }
    }

    public List<Transaction> findByUserId(int userId) {
        return findMany(SELECT_WITH_STOCK
                + "WHERE t.user_id = ? ORDER BY t.transaction_id DESC", userId);
    }

    public List<Transaction> findByUserAndType(int userId, Transaction.Type type) {
        return findMany(SELECT_WITH_STOCK
                + "WHERE t.user_id = ? AND t.transaction_type = ? ORDER BY t.transaction_id DESC",
                userId, type.name());
    }

    public List<Transaction> findByUserAndStock(int userId, int stockId) {
        return findMany(SELECT_WITH_STOCK
                + "WHERE t.user_id = ? AND t.stock_id = ? ORDER BY t.transaction_id", userId, stockId);
    }

    public int countByUser(int userId) {
        return count("SELECT COUNT(*) FROM transactions WHERE user_id = ?", userId);
    }

    public int countByUserAndType(int userId, Transaction.Type type) {
        return count("SELECT COUNT(*) FROM transactions WHERE user_id = ? AND transaction_type = ?",
                userId, type.name());
    }

    // ---------------------------------------------------------------

    private void bind(PreparedStatement ps, Transaction txn) throws SQLException {
        ps.setInt(1, txn.getUserId());
        ps.setInt(2, txn.getStockId());
        ps.setString(3, txn.getTransactionType().name());
        ps.setInt(4, txn.getQuantity());
        ps.setDouble(5, txn.getPrice());
        ps.setDouble(6, txn.getTotalAmount());
        ps.setInt(7, txn.getSimulationDay());
    }

    private int count(String sql, Object... params) {
        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Count query failed: " + sql, e);
            return 0;
        }
    }

    private List<Transaction> findMany(String sql, Object... params) {
        List<Transaction> list = new ArrayList<>();
        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Query failed: " + sql, e);
        }
        return list;
    }

    private Transaction map(ResultSet rs) throws SQLException {
        Transaction txn = new Transaction();
        txn.setTransactionId(rs.getInt("transaction_id"));
        txn.setUserId(rs.getInt("user_id"));
        txn.setStockId(rs.getInt("stock_id"));
        txn.setTransactionType(Transaction.Type.valueOf(rs.getString("transaction_type")));
        txn.setQuantity(rs.getInt("quantity"));
        txn.setPrice(rs.getDouble("price"));
        txn.setTotalAmount(rs.getDouble("total_amount"));
        Timestamp date = rs.getTimestamp("transaction_date");
        if (date != null) {
            txn.setTransactionDate(date.toLocalDateTime());
        }
        txn.setSimulationDay(rs.getInt("simulation_day"));
        txn.setSymbol(rs.getString("symbol"));
        txn.setCompanyName(rs.getString("company_name"));
        return txn;
    }
}

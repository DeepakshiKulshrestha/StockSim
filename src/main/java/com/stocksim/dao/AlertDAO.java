package com.stocksim.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.stocksim.model.PriceAlert;
import com.stocksim.util.DBConnection;

/**
 * All SQL for the {@code alerts} table.
 *
 * Note: {@code condition} is a MySQL reserved word, so it is backtick-quoted
 * in every statement below.
 */
public class AlertDAO {

    private static final Logger LOGGER = Logger.getLogger(AlertDAO.class.getName());

    private static final String SELECT_WITH_STOCK =
            "SELECT a.alert_id, a.user_id, a.stock_id, a.`condition` AS cond, "
          + "a.target_price, a.triggered, s.symbol, s.company_name "
          + "FROM alerts a JOIN stocks s ON s.stock_id = a.stock_id ";

    public boolean save(PriceAlert alert) {
        String sql = "INSERT INTO alerts (user_id, stock_id, `condition`, target_price, triggered) "
                   + "VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, alert.getUserId());
            ps.setInt(2, alert.getStockId());
            ps.setString(3, alert.getCondition().name());
            ps.setDouble(4, alert.getTargetPrice());
            ps.setBoolean(5, alert.isTriggered());
            if (ps.executeUpdate() == 0) {
                return false;
            }
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    alert.setAlertId(keys.getInt(1));
                }
            }
            LOGGER.info("Alert created: " + alert);
            return true;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "save(PriceAlert) failed", e);
            return false;
        }
    }

    public List<PriceAlert> findByUserId(int userId) {
        return findMany(SELECT_WITH_STOCK + "WHERE a.user_id = ? ORDER BY a.alert_id DESC", userId);
    }

    /** Alerts that have not fired yet - the only ones worth checking each day. */
    public List<PriceAlert> findActive(int userId) {
        return findMany(SELECT_WITH_STOCK
                + "WHERE a.user_id = ? AND a.triggered = FALSE ORDER BY a.alert_id", userId);
    }

    public boolean markTriggered(int alertId) {
        String sql = "UPDATE alerts SET triggered = TRUE WHERE alert_id = ?";
        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, alertId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "markTriggered failed for alert " + alertId, e);
            return false;
        }
    }

    public boolean delete(int alertId) {
        String sql = "DELETE FROM alerts WHERE alert_id = ?";
        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, alertId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "delete(PriceAlert) failed", e);
            return false;
        }
    }

    private List<PriceAlert> findMany(String sql, Object... params) {
        List<PriceAlert> alerts = new ArrayList<>();
        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    alerts.add(map(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Query failed: " + sql, e);
        }
        return alerts;
    }

    private PriceAlert map(ResultSet rs) throws SQLException {
        PriceAlert alert = new PriceAlert();
        alert.setAlertId(rs.getInt("alert_id"));
        alert.setUserId(rs.getInt("user_id"));
        alert.setStockId(rs.getInt("stock_id"));
        alert.setCondition(PriceAlert.Condition.valueOf(rs.getString("cond")));
        alert.setTargetPrice(rs.getDouble("target_price"));
        alert.setTriggered(rs.getBoolean("triggered"));
        alert.setSymbol(rs.getString("symbol"));
        alert.setCompanyName(rs.getString("company_name"));
        return alert;
    }
}

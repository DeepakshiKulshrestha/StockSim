package com.stocksim.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.stocksim.model.User;
import com.stocksim.util.DBConnection;

/**
 * All SQL for the {@code users} table. Every query uses a PreparedStatement,
 * so user input is never concatenated into SQL (no injection possible).
 */
public class UserDAO {

    private static final Logger LOGGER = Logger.getLogger(UserDAO.class.getName());

    /** Inserts the user and writes the generated key back onto the object. */
    public boolean save(User user) {
        String sql = "INSERT INTO users (name, username, email, password, balance, sim_day) "
                   + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, user.getName());
            ps.setString(2, user.getUsername());
            ps.setString(3, user.getEmail());
            ps.setString(4, user.getPassword());
            ps.setDouble(5, user.getBalance());
            ps.setInt(6, user.getSimDay() <= 0 ? 1 : user.getSimDay());

            if (ps.executeUpdate() == 0) {
                return false;
            }
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    user.setUserId(keys.getInt(1));
                }
            }
            LOGGER.info("User registered: " + user.getUsername() + " (id=" + user.getUserId() + ")");
            return true;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "save(User) failed for " + user.getUsername(), e);
            return false;
        }
    }

    public Optional<User> findById(int userId) {
        return findOne("SELECT * FROM users WHERE user_id = ?", userId);
    }

    public Optional<User> findByUsername(String username) {
        return findOne("SELECT * FROM users WHERE username = ?", username);
    }

    public Optional<User> findByEmail(String email) {
        return findOne("SELECT * FROM users WHERE email = ?", email);
    }

    /** Login accepts either the username or the email address. */
    public Optional<User> findByUsernameOrEmail(String identifier) {
        return findOne("SELECT * FROM users WHERE username = ? OR email = ?", identifier, identifier);
    }

    public boolean existsByUsername(String username) {
        return findByUsername(username).isPresent();
    }

    public boolean existsByEmail(String email) {
        return findByEmail(email).isPresent();
    }

    public boolean updateBalance(int userId, double newBalance) {
        String sql = "UPDATE users SET balance = ? WHERE user_id = ?";
        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, newBalance);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "updateBalance failed for user " + userId, e);
            return false;
        }
    }

    /**
     * Balance update that participates in a caller-managed transaction.
     * Used by TradingService so buy/sell can commit or roll back as one unit.
     */
    public boolean updateBalance(Connection conn, int userId, double newBalance) throws SQLException {
        String sql = "UPDATE users SET balance = ? WHERE user_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, newBalance);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean updateSimDay(int userId, int simDay) {
        String sql = "UPDATE users SET sim_day = ? WHERE user_id = ?";
        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, simDay);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "updateSimDay failed for user " + userId, e);
            return false;
        }
    }

    public boolean delete(int userId) {
        String sql = "DELETE FROM users WHERE user_id = ?";
        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "delete failed for user " + userId, e);
            return false;
        }
    }

    // ---------------------------------------------------------------

    private Optional<User> findOne(String sql, Object... params) {
        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Query failed: " + sql, e);
            return Optional.empty();
        }
    }

    /** ResultSet row -> User object. */
    private User map(ResultSet rs) throws SQLException {
        User user = new User();
        user.setUserId(rs.getInt("user_id"));
        user.setName(rs.getString("name"));
        user.setUsername(rs.getString("username"));
        user.setEmail(rs.getString("email"));
        user.setPassword(rs.getString("password"));
        user.setBalance(rs.getDouble("balance"));
        user.setSimDay(rs.getInt("sim_day"));
        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) {
            user.setCreatedAt(created.toLocalDateTime());
        }
        return user;
    }
}

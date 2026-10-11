package com.stocksim.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.stocksim.model.Goal;
import com.stocksim.util.DBConnection;

/** All SQL for the {@code goals} table. */
public class GoalDAO {

    private static final Logger LOGGER = Logger.getLogger(GoalDAO.class.getName());

    public boolean save(Goal goal) {
        String sql = "INSERT INTO goals (user_id, goal_name, target_amount) VALUES (?, ?, ?)";
        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, goal.getUserId());
            ps.setString(2, goal.getGoalName());
            ps.setDouble(3, goal.getTargetAmount());
            if (ps.executeUpdate() == 0) {
                return false;
            }
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    goal.setGoalId(keys.getInt(1));
                }
            }
            LOGGER.info("Goal created: " + goal.getGoalName() + " for user " + goal.getUserId());
            return true;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "save(Goal) failed", e);
            return false;
        }
    }

    public List<Goal> findByUserId(int userId) {
        List<Goal> goals = new ArrayList<>();
        String sql = "SELECT * FROM goals WHERE user_id = ? ORDER BY goal_id";
        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    goals.add(map(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "findByUserId(Goal) failed", e);
        }
        return goals;
    }

    public Optional<Goal> findById(int goalId) {
        String sql = "SELECT * FROM goals WHERE goal_id = ?";
        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, goalId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "findById(Goal) failed", e);
            return Optional.empty();
        }
    }

    public boolean delete(int goalId) {
        String sql = "DELETE FROM goals WHERE goal_id = ?";
        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, goalId);
            boolean deleted = ps.executeUpdate() > 0;
            if (deleted) {
                LOGGER.info("Goal deleted: id=" + goalId);
            }
            return deleted;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "delete(Goal) failed", e);
            return false;
        }
    }

    private Goal map(ResultSet rs) throws SQLException {
        Goal goal = new Goal();
        goal.setGoalId(rs.getInt("goal_id"));
        goal.setUserId(rs.getInt("user_id"));
        goal.setGoalName(rs.getString("goal_name"));
        goal.setTargetAmount(rs.getDouble("target_amount"));
        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) {
            goal.setCreatedAt(created.toLocalDateTime());
        }
        return goal;
    }
}

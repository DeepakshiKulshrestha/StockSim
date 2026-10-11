package com.stocksim.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.stocksim.model.MarketEvent;
import com.stocksim.util.DBConnection;

/** All SQL for the {@code market_events} table. */
public class MarketEventDAO {

    private static final Logger LOGGER = Logger.getLogger(MarketEventDAO.class.getName());

    /** Events that fire exactly on this simulation day. */
    public List<MarketEvent> findByDay(int simulationDay) {
        return findMany("SELECT * FROM market_events WHERE simulation_day = ? ORDER BY event_id",
                simulationDay);
    }

    /** Events fired anywhere in the (fromDay, toDay] window - used by NEXT WEEK / NEXT MONTH. */
    public List<MarketEvent> findInRange(int fromDayExclusive, int toDayInclusive) {
        return findMany("SELECT * FROM market_events "
                + "WHERE simulation_day > ? AND simulation_day <= ? ORDER BY simulation_day",
                fromDayExclusive, toDayInclusive);
    }

    /** Most recent events up to the given day - the dashboard news feed. */
    public List<MarketEvent> findRecent(int upToDay, int limit) {
        return findMany("SELECT * FROM market_events WHERE simulation_day <= ? "
                + "ORDER BY simulation_day DESC LIMIT ?", upToDay, Math.max(1, limit));
    }

    public List<MarketEvent> findAll() {
        return findMany("SELECT * FROM market_events ORDER BY simulation_day");
    }

    private List<MarketEvent> findMany(String sql, Object... params) {
        List<MarketEvent> events = new ArrayList<>();
        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    events.add(map(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Query failed: " + sql, e);
        }
        return events;
    }

    private MarketEvent map(ResultSet rs) throws SQLException {
        MarketEvent event = new MarketEvent();
        event.setEventId(rs.getInt("event_id"));
        event.setTitle(rs.getString("title"));
        event.setDescription(rs.getString("description"));
        event.setSector(rs.getString("sector"));
        event.setSimulationDay(rs.getInt("simulation_day"));
        event.setImpact(rs.getString("impact"));
        return event;
    }
}

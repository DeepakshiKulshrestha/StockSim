package com.stocksim.service;

import java.util.List;
import java.util.logging.Logger;

import com.stocksim.dao.MarketEventDAO;
import com.stocksim.dao.StockDAO;
import com.stocksim.dao.StockHistoryDAO;
import com.stocksim.dao.UserDAO;
import com.stocksim.model.MarketEvent;
import com.stocksim.model.PriceAlert;
import com.stocksim.model.User;
import com.stocksim.util.UserSession;

/**
 * Moves simulated time forward.
 *
 * "Advancing a day" means: move the user's sim_day pointer, repoint every stock's
 * current_price at its real historical close for the new day, fire any scripted
 * market events, and check the user's price alerts.
 */
public class SimulationService {

    private static final Logger LOGGER = Logger.getLogger(SimulationService.class.getName());

    /** Result of one advance, so the controller can update the UI in one go. */
    public static class AdvanceResult {
        private final int previousDay;
        private final int newDay;
        private final boolean reachedEnd;
        private final List<MarketEvent> events;
        private final List<PriceAlert> triggeredAlerts;

        public AdvanceResult(int previousDay, int newDay, boolean reachedEnd,
                             List<MarketEvent> events, List<PriceAlert> triggeredAlerts) {
            this.previousDay = previousDay;
            this.newDay = newDay;
            this.reachedEnd = reachedEnd;
            this.events = events;
            this.triggeredAlerts = triggeredAlerts;
        }

        public int getPreviousDay() { return previousDay; }
        public int getNewDay() { return newDay; }
        public int getDaysAdvanced() { return newDay - previousDay; }
        public boolean isReachedEnd() { return reachedEnd; }
        public List<MarketEvent> getEvents() { return events; }
        public List<PriceAlert> getTriggeredAlerts() { return triggeredAlerts; }
    }

    private final UserDAO userDAO;
    private final StockDAO stockDAO;
    private final StockHistoryDAO historyDAO;
    private final MarketEventDAO eventDAO;
    private final AlertService alertService;

    public SimulationService() {
        this(new UserDAO(), new StockDAO(), new StockHistoryDAO(),
             new MarketEventDAO(), new AlertService());
    }

    public SimulationService(UserDAO userDAO, StockDAO stockDAO, StockHistoryDAO historyDAO,
                             MarketEventDAO eventDAO, AlertService alertService) {
        this.userDAO = userDAO;
        this.stockDAO = stockDAO;
        this.historyDAO = historyDAO;
        this.eventDAO = eventDAO;
        this.alertService = alertService;
    }

    public int getCurrentDay(int userId) {
        return userDAO.findById(userId).map(User::getSimDay).orElse(1);
    }

    /** Last simulation day that has price data - the simulation cannot go past this. */
    public int getLastAvailableDay() {
        return historyDAO.findMaxSimulationDay();
    }

    public AdvanceResult advanceDay(int userId)   { return advance(userId, 1); }
    public AdvanceResult advanceWeek(int userId)  { return advance(userId, 7); }
    public AdvanceResult advanceMonth(int userId) { return advance(userId, 30); }

    /**
     * Moves the simulation forward by {@code days}, clamped to the last day that has data.
     */
    public AdvanceResult advance(int userId, int days) {
        if (days <= 0) {
            throw new IllegalArgumentException("Days to advance must be positive.");
        }

        int currentDay = getCurrentDay(userId);
        int lastDay = getLastAvailableDay();
        int targetDay = Math.min(currentDay + days, lastDay);
        boolean reachedEnd = targetDay >= lastDay;

        if (targetDay == currentDay) {
            LOGGER.warning("Simulation is already at the last available day (" + lastDay + ").");
            return new AdvanceResult(currentDay, currentDay, true, List.of(), List.of());
        }

        userDAO.updateSimDay(userId, targetDay);
        stockDAO.syncPricesToDay(targetDay);

        List<MarketEvent> events = eventDAO.findInRange(currentDay, targetDay);
        List<PriceAlert> alerts = alertService.checkAlertsInRange(userId, currentDay, targetDay);

        // keep the in-memory session in step with the database
        if (UserSession.getInstance().getUserId() == userId) {
            UserSession.getInstance().setSimulationDay(targetDay);
        }

        LOGGER.info(String.format("Simulation advanced: day %d -> %d (%d event(s), %d alert(s) fired)",
                currentDay, targetDay, events.size(), alerts.size()));

        return new AdvanceResult(currentDay, targetDay, reachedEnd, events, alerts);
    }

    /**
     * Repoints stocks.current_price at the given user's current simulation day.
     * Called right after login so the market reflects where that user left off.
     */
    public int syncPricesToUser(int userId) {
        int day = getCurrentDay(userId);
        return stockDAO.syncPricesToDay(day);
    }

    /** News cards for the dashboard: events on or before the user's current day. */
    public List<MarketEvent> getRecentEvents(int userId, int limit) {
        return eventDAO.findRecent(getCurrentDay(userId), limit);
    }

    /** Resets a user back to simulation day 1 without touching their money or trades. */
    public boolean resetToDay(int userId, int day) {
        int lastDay = getLastAvailableDay();
        int clamped = Math.max(1, Math.min(day, lastDay));
        boolean ok = userDAO.updateSimDay(userId, clamped);
        if (ok) {
            stockDAO.syncPricesToDay(clamped);
            if (UserSession.getInstance().getUserId() == userId) {
                UserSession.getInstance().setSimulationDay(clamped);
            }
            LOGGER.info("Simulation reset to day " + clamped + " for user " + userId);
        }
        return ok;
    }
}

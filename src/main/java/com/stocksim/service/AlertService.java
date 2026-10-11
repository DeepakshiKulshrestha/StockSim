package com.stocksim.service;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.stocksim.dao.AlertDAO;
import com.stocksim.dao.StockDAO;
import com.stocksim.exception.InvalidQuantityException;
import com.stocksim.model.PriceAlert;
import com.stocksim.model.Stock;
import com.stocksim.util.Validator;

/** Creating price alerts and checking them whenever simulated time moves forward. */
public class AlertService {

    private static final Logger LOGGER = Logger.getLogger(AlertService.class.getName());

    private final AlertDAO alertDAO;
    private final StockDAO stockDAO;
    private final AnalysisService analysisService;

    public AlertService() {
        this(new AlertDAO(), new StockDAO(), new AnalysisService());
    }

    public AlertService(AlertDAO alertDAO, StockDAO stockDAO, AnalysisService analysisService) {
        this.alertDAO = alertDAO;
        this.stockDAO = stockDAO;
        this.analysisService = analysisService;
    }

    public PriceAlert createAlert(int userId, int stockId, PriceAlert.Condition condition,
                                  String targetPriceText) throws InvalidQuantityException {

        if (!Validator.isPositiveDouble(targetPriceText)) {
            throw new InvalidQuantityException("Target price must be a number greater than zero.");
        }
        Stock stock = stockDAO.findById(stockId)
                .orElseThrow(() -> new IllegalArgumentException("No stock with id " + stockId));

        PriceAlert alert = new PriceAlert(userId, stockId, condition,
                Validator.parseDoubleOrZero(targetPriceText));
        alert.setSymbol(stock.getSymbol());
        alert.setCompanyName(stock.getCompanyName());

        if (!alertDAO.save(alert)) {
            throw new IllegalStateException("Could not save the alert. Please try again.");
        }
        return alert;
    }

    public List<PriceAlert> getAlerts(int userId) {
        return alertDAO.findByUserId(userId);
    }

    public List<PriceAlert> getActiveAlerts(int userId) {
        return alertDAO.findActive(userId);
    }

    public boolean deleteAlert(int alertId) {
        return alertDAO.delete(alertId);
    }

    /**
     * Checks every un-fired alert against the price on {@code simulationDay} and marks
     * the ones whose condition is now met.
     *
     * @return the alerts that fired on this call, so the UI can show a notification
     */
    public List<PriceAlert> checkAlerts(int userId, int simulationDay) {
        List<PriceAlert> triggered = new ArrayList<>();

        for (PriceAlert alert : alertDAO.findActive(userId)) {
            double price = analysisService.priceOn(alert.getStockId(), simulationDay);
            if (price > 0 && alert.isConditionMet(price)) {
                if (alertDAO.markTriggered(alert.getAlertId())) {
                    alert.setTriggered(true);
                    triggered.add(alert);
                    LOGGER.info(String.format("ALERT FIRED: %s went %s Rs %.2f (price Rs %.2f, day %d)",
                            alert.getSymbol(), alert.getConditionName(),
                            alert.getTargetPrice(), price, simulationDay));
                }
            }
        }
        return triggered;
    }

    /**
     * Same check across a whole range of days, so NEXT WEEK / NEXT MONTH cannot skip
     * over an alert that was only satisfied on an intermediate day.
     */
    public List<PriceAlert> checkAlertsInRange(int userId, int fromDayExclusive, int toDayInclusive) {
        List<PriceAlert> triggered = new ArrayList<>();
        List<PriceAlert> active = alertDAO.findActive(userId);

        for (PriceAlert alert : active) {
            for (int day = fromDayExclusive + 1; day <= toDayInclusive; day++) {
                double price = analysisService.priceOn(alert.getStockId(), day);
                if (price > 0 && alert.isConditionMet(price)) {
                    if (alertDAO.markTriggered(alert.getAlertId())) {
                        alert.setTriggered(true);
                        triggered.add(alert);
                        LOGGER.info(String.format(
                                "ALERT FIRED: %s went %s Rs %.2f (price Rs %.2f on day %d)",
                                alert.getSymbol(), alert.getConditionName(),
                                alert.getTargetPrice(), price, day));
                    }
                    break;                    // an alert fires only once
                }
            }
        }
        return triggered;
    }
}

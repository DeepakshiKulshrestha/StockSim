package com.stocksim.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.stocksim.dao.HoldingDAO;
import com.stocksim.model.Holding;
import com.stocksim.util.ConfigLoader;

/**
 * Concentration and diversification checks over the user's holdings.
 * Demonstrates HashMap and Comparator use (Unit 2).
 */
public class RiskService {

    private static final Logger LOGGER = Logger.getLogger(RiskService.class.getName());

    private final HoldingDAO holdingDAO;
    private final double stockThreshold;
    private final double sectorThreshold;

    public RiskService() {
        this(new HoldingDAO());
    }

    public RiskService(HoldingDAO holdingDAO) {
        this.holdingDAO = holdingDAO;
        this.stockThreshold = ConfigLoader.getDouble("risk.stock.threshold", 40.0);
        this.sectorThreshold = ConfigLoader.getDouble("risk.sector.threshold", 60.0);
    }

    /** sector -> percentage of the invested portfolio, largest first. */
    public Map<String, Double> getSectorAllocation(int userId) {
        List<Holding> holdings = holdingDAO.findByUserId(userId);
        double total = totalValue(holdings);
        if (total <= 0) {
            return new LinkedHashMap<>();
        }

        Map<String, Double> bySector = new HashMap<>();
        for (Holding holding : holdings) {
            String sector = holding.getSector() == null ? "Unknown" : holding.getSector();
            bySector.merge(sector, holding.calculateValue(), Double::sum);
        }
        return toSortedPercentages(bySector, total);
    }

    /** stock symbol -> percentage of the invested portfolio, largest first. */
    public Map<String, Double> getStockAllocation(int userId) {
        List<Holding> holdings = holdingDAO.findByUserId(userId);
        double total = totalValue(holdings);
        if (total <= 0) {
            return new LinkedHashMap<>();
        }

        Map<String, Double> byStock = new HashMap<>();
        for (Holding holding : holdings) {
            byStock.merge(holding.getSymbol(), holding.calculateValue(), Double::sum);
        }
        return toSortedPercentages(byStock, total);
    }

    /** {@code {symbol, percent}} of the single biggest position, or null when there are none. */
    public Map.Entry<String, Double> getLargestHolding(int userId) {
        return getStockAllocation(userId).entrySet().stream().findFirst().orElse(null);
    }

    public Map.Entry<String, Double> getLargestSector(int userId) {
        return getSectorAllocation(userId).entrySet().stream().findFirst().orElse(null);
    }

    /**
     * "Good" when no single stock exceeds 25% and at least 3 sectors are represented,
     * "Poor" when one stock is over the 40% threshold or everything sits in one sector.
     */
    public String getDiversificationLevel(int userId) {
        Map<String, Double> stocks = getStockAllocation(userId);
        Map<String, Double> sectors = getSectorAllocation(userId);
        if (stocks.isEmpty()) {
            return "No holdings";
        }

        double largestStock = stocks.values().iterator().next();
        double largestSector = sectors.values().iterator().next();

        if (largestStock > stockThreshold || largestSector > sectorThreshold || sectors.size() == 1) {
            return "Poor";
        }
        if (largestStock <= 25.0 && sectors.size() >= 3) {
            return "Good";
        }
        return "Moderate";
    }

    /** Human-readable warnings, or an empty list when the portfolio is well spread. */
    public List<String> getConcentrationWarnings(int userId) {
        List<String> warnings = new ArrayList<>();

        for (Map.Entry<String, Double> entry : getStockAllocation(userId).entrySet()) {
            if (entry.getValue() > stockThreshold) {
                warnings.add(String.format(
                        "High concentration: %s is %.1f%% of your portfolio (limit %.0f%%).",
                        entry.getKey(), entry.getValue(), stockThreshold));
            }
        }
        for (Map.Entry<String, Double> entry : getSectorAllocation(userId).entrySet()) {
            if (entry.getValue() > sectorThreshold) {
                warnings.add(String.format(
                        "Sector concentration: %.1f%% of your portfolio is in %s (limit %.0f%%).",
                        entry.getValue(), entry.getKey(), sectorThreshold));
            }
        }
        if (!warnings.isEmpty()) {
            LOGGER.warning("Concentration risk for user " + userId + ": " + warnings.size() + " warning(s)");
        }
        return warnings;
    }

    /** First warning as a single string, or null - convenient for a one-line label. */
    public String getConcentrationWarning(int userId) {
        List<String> warnings = getConcentrationWarnings(userId);
        return warnings.isEmpty() ? null : warnings.get(0);
    }

    public double getStockThreshold() { return stockThreshold; }

    public double getSectorThreshold() { return sectorThreshold; }

    // ---------------------------------------------------------------

    private double totalValue(List<Holding> holdings) {
        double total = 0.0;
        for (Holding holding : holdings) {
            total += holding.calculateValue();
        }
        return total;
    }

    /** Converts rupee amounts to percentages and orders them largest first. */
    private Map<String, Double> toSortedPercentages(Map<String, Double> amounts, double total) {
        List<Map.Entry<String, Double>> entries = new ArrayList<>(amounts.entrySet());
        entries.sort(Map.Entry.<String, Double>comparingByValue().reversed()
                .thenComparing(Map.Entry.comparingByKey()));

        Map<String, Double> percentages = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : entries) {
            percentages.put(entry.getKey(), round2((entry.getValue() / total) * 100.0));
        }
        return percentages;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /** Exposed so the UI can sort chart slices the same way. */
    public static Comparator<Map.Entry<String, Double>> byValueDescending() {
        return Map.Entry.<String, Double>comparingByValue().reversed();
    }
}

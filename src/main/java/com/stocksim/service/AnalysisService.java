package com.stocksim.service;

import java.util.List;
import java.util.logging.Logger;

import com.stocksim.dao.StockDAO;
import com.stocksim.dao.StockHistoryDAO;
import com.stocksim.model.Stock;
import com.stocksim.model.StockAnalysis;
import com.stocksim.model.StockPrice;

/**
 * Every metric on the stock details / analysis screens, all derived from the
 * stock_history table. Nothing here is random - it is real NSE price history.
 */
public class AnalysisService {

    private static final Logger LOGGER = Logger.getLogger(AnalysisService.class.getName());

    /** 52 weeks of trading days (roughly 5 sessions a week). */
    public static final int TRADING_DAYS_PER_YEAR = 252;

    private final StockDAO stockDAO;
    private final StockHistoryDAO historyDAO;

    public AnalysisService() {
        this(new StockDAO(), new StockHistoryDAO());
    }

    public AnalysisService(StockDAO stockDAO, StockHistoryDAO historyDAO) {
        this.stockDAO = stockDAO;
        this.historyDAO = historyDAO;
    }

    // ---------------- individual metrics ----------------

    /** Percent change from the previous trading day to {@code day}. */
    public double getDailyChange(int stockId, int day) {
        return percentChangeOver(stockId, day, 1);
    }

    public double get7DayChange(int stockId, int day) {
        return percentChangeOver(stockId, day, 7);
    }

    public double get30DayChange(int stockId, int day) {
        return percentChangeOver(stockId, day, 30);
    }

    /** Simple moving average of the last {@code n} closes up to {@code day}. */
    public double getMovingAverage(int stockId, int day, int n) {
        List<StockPrice> window = historyDAO.findLastNDays(stockId, day, n);
        if (window.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (StockPrice p : window) {
            sum += p.getPrice();
        }
        return round2(sum / window.size());
    }

    public double get52WeekHigh(int stockId, int day) {
        return round2(historyDAO.findMaxPrice(stockId, Math.max(1, day - TRADING_DAYS_PER_YEAR + 1), day));
    }

    public double get52WeekLow(int stockId, int day) {
        return round2(historyDAO.findMinPrice(stockId, Math.max(1, day - TRADING_DAYS_PER_YEAR + 1), day));
    }

    /** Standard deviation of the last 30 closes, as a percentage of their mean. */
    public double getVolatilityPercent(int stockId, int day) {
        List<StockPrice> window = historyDAO.findLastNDays(stockId, day, 30);
        if (window.size() < 2) {
            return 0.0;
        }
        double mean = window.stream().mapToDouble(StockPrice::getPrice).average().orElse(0.0);
        if (mean == 0) {
            return 0.0;
        }
        double variance = 0.0;
        for (StockPrice p : window) {
            variance += Math.pow(p.getPrice() - mean, 2);
        }
        variance /= (window.size() - 1);                 // sample standard deviation
        return round2((Math.sqrt(variance) / mean) * 100.0);
    }

    /** "Low" below 2%, "Medium" below 5%, otherwise "High". */
    public String getVolatility(int stockId, int day) {
        return volatilityLabel(getVolatilityPercent(stockId, day));
    }

    /** Compares the price with its own 20-day moving average. */
    public String getTrendLabel(int stockId, int day) {
        double price = priceOn(stockId, day);
        double ma20 = getMovingAverage(stockId, day, 20);
        if (ma20 == 0) {
            return "Neutral";
        }
        double gap = ((price - ma20) / ma20) * 100.0;
        if (gap > 1.0) return "Positive";
        if (gap < -1.0) return "Negative";
        return "Neutral";
    }

    // ---------------- everything at once ----------------

    /** Builds the full metric set for one stock on one simulation day. */
    public StockAnalysis analyse(int stockId, int day) {
        Stock stock = stockDAO.findById(stockId)
                .orElseThrow(() -> new IllegalArgumentException("No stock with id " + stockId));

        double current = priceOn(stockId, day);
        double previous = day > 1 ? priceOn(stockId, day - 1) : current;

        StockAnalysis a = new StockAnalysis();
        a.setStockId(stockId);
        a.setSymbol(stock.getSymbol());
        a.setCompanyName(stock.getCompanyName());
        a.setSector(stock.getSector());
        a.setSimulationDay(day);

        a.setCurrentPrice(round2(current));
        a.setPreviousClose(round2(previous));
        a.setDailyChange(round2(current - previous));
        a.setDailyChangePercent(previous == 0 ? 0.0 : round2(((current - previous) / previous) * 100.0));
        a.setSevenDayChangePercent(get7DayChange(stockId, day));
        a.setThirtyDayChangePercent(get30DayChange(stockId, day));
        a.setMovingAverage5(getMovingAverage(stockId, day, 5));
        a.setMovingAverage20(getMovingAverage(stockId, day, 20));
        a.setWeek52High(get52WeekHigh(stockId, day));
        a.setWeek52Low(get52WeekLow(stockId, day));

        double volatility = getVolatilityPercent(stockId, day);
        a.setVolatilityPercent(volatility);
        a.setStandardDeviation(round2(volatility * current / 100.0));
        a.setVolatilityLabel(volatilityLabel(volatility));

        a.setTrendLabel(getTrendLabel(stockId, day));
        a.setMomentumLabel(a.getSevenDayChangePercent() >= 0 ? "Positive" : "Negative");
        a.setRiskProfile(riskProfile(volatility));

        LOGGER.fine("Analysed " + a);
        return a;
    }

    /** Price series for the chart: the last {@code days} closes up to {@code day}. */
    public List<StockPrice> getPriceSeries(int stockId, int day, int days) {
        return historyDAO.findLastNDays(stockId, day, days);
    }

    /** Close on a specific simulation day, falling back to the most recent day that has data. */
    public double priceOn(int stockId, int day) {
        double price = historyDAO.findPrice(stockId, day);
        if (price > 0) {
            return price;
        }
        List<StockPrice> recent = historyDAO.findLastNDays(stockId, day, 1);
        return recent.isEmpty() ? 0.0 : recent.get(0).getPrice();
    }

    // ---------------------------------------------------------------

    private double percentChangeOver(int stockId, int day, int lookback) {
        double now = priceOn(stockId, day);
        int pastDay = Math.max(1, day - lookback);
        double past = priceOn(stockId, pastDay);
        if (past == 0 || pastDay == day) {
            return 0.0;
        }
        return round2(((now - past) / past) * 100.0);
    }

    private static String volatilityLabel(double volatilityPercent) {
        if (volatilityPercent < 2.0) return "Low";
        if (volatilityPercent < 5.0) return "Medium";
        return "High";
    }

    private static String riskProfile(double volatilityPercent) {
        if (volatilityPercent < 2.0) return "Low";
        if (volatilityPercent < 5.0) return "Moderate";
        return "High";
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}

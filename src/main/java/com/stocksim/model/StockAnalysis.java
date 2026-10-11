package com.stocksim.model;

/** Every metric the analysis screen shows for one stock on one simulation day. */
public class StockAnalysis {

    private int stockId;
    private String symbol;
    private String companyName;
    private String sector;
    private int simulationDay;

    private double currentPrice;
    private double previousClose;
    private double dailyChange;          // rupees
    private double dailyChangePercent;
    private double sevenDayChangePercent;
    private double thirtyDayChangePercent;
    private double movingAverage5;
    private double movingAverage20;
    private double week52High;
    private double week52Low;
    private double standardDeviation;
    private double volatilityPercent;    // std-dev as % of mean price

    private String volatilityLabel;      // Low / Medium / High
    private String trendLabel;           // Positive / Negative / Neutral
    private String momentumLabel;        // Positive / Negative
    private String riskProfile;          // Low / Moderate / High

    public int getStockId() { return stockId; }
    public void setStockId(int stockId) { this.stockId = stockId; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getSector() { return sector; }
    public void setSector(String sector) { this.sector = sector; }

    public int getSimulationDay() { return simulationDay; }
    public void setSimulationDay(int simulationDay) { this.simulationDay = simulationDay; }

    public double getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(double currentPrice) { this.currentPrice = currentPrice; }

    public double getPreviousClose() { return previousClose; }
    public void setPreviousClose(double previousClose) { this.previousClose = previousClose; }

    public double getDailyChange() { return dailyChange; }
    public void setDailyChange(double dailyChange) { this.dailyChange = dailyChange; }

    public double getDailyChangePercent() { return dailyChangePercent; }
    public void setDailyChangePercent(double v) { this.dailyChangePercent = v; }

    public double getSevenDayChangePercent() { return sevenDayChangePercent; }
    public void setSevenDayChangePercent(double v) { this.sevenDayChangePercent = v; }

    public double getThirtyDayChangePercent() { return thirtyDayChangePercent; }
    public void setThirtyDayChangePercent(double v) { this.thirtyDayChangePercent = v; }

    public double getMovingAverage5() { return movingAverage5; }
    public void setMovingAverage5(double v) { this.movingAverage5 = v; }

    public double getMovingAverage20() { return movingAverage20; }
    public void setMovingAverage20(double v) { this.movingAverage20 = v; }

    public double getWeek52High() { return week52High; }
    public void setWeek52High(double v) { this.week52High = v; }

    public double getWeek52Low() { return week52Low; }
    public void setWeek52Low(double v) { this.week52Low = v; }

    public double getStandardDeviation() { return standardDeviation; }
    public void setStandardDeviation(double v) { this.standardDeviation = v; }

    public double getVolatilityPercent() { return volatilityPercent; }
    public void setVolatilityPercent(double v) { this.volatilityPercent = v; }

    public String getVolatilityLabel() { return volatilityLabel; }
    public void setVolatilityLabel(String v) { this.volatilityLabel = v; }

    public String getTrendLabel() { return trendLabel; }
    public void setTrendLabel(String v) { this.trendLabel = v; }

    public String getMomentumLabel() { return momentumLabel; }
    public void setMomentumLabel(String v) { this.momentumLabel = v; }

    public String getRiskProfile() { return riskProfile; }
    public void setRiskProfile(String v) { this.riskProfile = v; }

    @Override
    public String toString() {
        return String.format("%s day %d: Rs %.2f (%.2f%%), trend=%s, volatility=%s, risk=%s",
                symbol, simulationDay, currentPrice, dailyChangePercent,
                trendLabel, volatilityLabel, riskProfile);
    }
}

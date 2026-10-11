package com.stocksim.model;

import java.io.Serializable;

/** One of the 10 tradable NSE stocks. Maps to the {@code stocks} table. */
public class Stock implements Serializable {

    private static final long serialVersionUID = 1L;

    private int stockId;
    private String symbol;
    private String companyName;
    private String sector;
    private double currentPrice;

    /** Not persisted - filled in by services so the market table can colour the row. */
    private double changePercent;

    public Stock() {
    }

    public Stock(String symbol, String companyName, String sector, double currentPrice) {
        this.symbol = symbol;
        this.companyName = companyName;
        this.sector = sector;
        this.currentPrice = currentPrice;
    }

    public int getStockId() { return stockId; }
    public void setStockId(int stockId) { this.stockId = stockId; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getSector() { return sector; }
    public void setSector(String sector) { this.sector = sector; }

    public double getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(double currentPrice) { this.currentPrice = currentPrice; }

    public double getChangePercent() { return changePercent; }
    public void setChangePercent(double changePercent) { this.changePercent = changePercent; }

    @Override
    public String toString() {
        return symbol + " (" + companyName + ") Rs " + String.format("%.2f", currentPrice);
    }
}

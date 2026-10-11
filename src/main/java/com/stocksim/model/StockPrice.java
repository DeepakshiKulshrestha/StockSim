package com.stocksim.model;

import java.io.Serializable;
import java.time.LocalDate;

/** One historical daily close. Maps to a row of {@code stock_history}. */
public class StockPrice implements Serializable {

    private static final long serialVersionUID = 1L;

    private int historyId;
    private int stockId;
    private double price;
    private LocalDate recordedAt;
    private int simulationDay;

    public StockPrice() {
    }

    public StockPrice(int stockId, double price, LocalDate recordedAt, int simulationDay) {
        this.stockId = stockId;
        this.price = price;
        this.recordedAt = recordedAt;
        this.simulationDay = simulationDay;
    }

    public int getHistoryId() { return historyId; }
    public void setHistoryId(int historyId) { this.historyId = historyId; }

    public int getStockId() { return stockId; }
    public void setStockId(int stockId) { this.stockId = stockId; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public LocalDate getRecordedAt() { return recordedAt; }
    public void setRecordedAt(LocalDate recordedAt) { this.recordedAt = recordedAt; }

    public int getSimulationDay() { return simulationDay; }
    public void setSimulationDay(int simulationDay) { this.simulationDay = simulationDay; }

    @Override
    public String toString() {
        return "Day " + simulationDay + " (" + recordedAt + ") = Rs " + String.format("%.2f", price);
    }
}

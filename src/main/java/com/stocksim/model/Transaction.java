package com.stocksim.model;

import java.io.Serializable;
import java.time.LocalDateTime;

/** One BUY or SELL record. Maps to the {@code transactions} table. */
public class Transaction implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Matches the ENUM('BUY','SELL') column. */
    public enum Type { BUY, SELL }

    private int transactionId;
    private int userId;
    private int stockId;
    private Type transactionType;
    private int quantity;
    private double price;
    private double totalAmount;
    private LocalDateTime transactionDate;
    private int simulationDay;

    /** Display-only, joined in from the stocks table. */
    private String symbol;
    private String companyName;

    public Transaction() {
    }

    public Transaction(int userId, int stockId, Type transactionType,
                       int quantity, double price, int simulationDay) {
        this.userId = userId;
        this.stockId = stockId;
        this.transactionType = transactionType;
        this.quantity = quantity;
        this.price = price;
        this.totalAmount = quantity * price;
        this.simulationDay = simulationDay;
    }

    public boolean isBuy() { return transactionType == Type.BUY; }

    public int getTransactionId() { return transactionId; }
    public void setTransactionId(int transactionId) { this.transactionId = transactionId; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public int getStockId() { return stockId; }
    public void setStockId(int stockId) { this.stockId = stockId; }

    public Type getTransactionType() { return transactionType; }
    public void setTransactionType(Type transactionType) { this.transactionType = transactionType; }

    /** Convenience for TableView cell factories. */
    public String getTypeName() { return transactionType == null ? "" : transactionType.name(); }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }

    public LocalDateTime getTransactionDate() { return transactionDate; }
    public void setTransactionDate(LocalDateTime transactionDate) { this.transactionDate = transactionDate; }

    public int getSimulationDay() { return simulationDay; }
    public void setSimulationDay(int simulationDay) { this.simulationDay = simulationDay; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    @Override
    public String toString() {
        return String.format("%s %d x %s @ Rs %.2f = Rs %.2f (day %d)",
                getTypeName(), quantity, symbol, price, totalAmount, simulationDay);
    }
}

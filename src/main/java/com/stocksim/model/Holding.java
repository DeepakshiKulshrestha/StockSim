package com.stocksim.model;

import com.stocksim.exception.InsufficientSharesException;
import com.stocksim.exception.InvalidQuantityException;

/**
 * Shares of one stock owned by one user. Maps to the {@code holdings} table.
 *
 * Demonstrates all three Unit-1 OOP requirements at once:
 * extends the abstract {@link FinancialEntity}, implements {@link Calculable}
 * and {@link Tradable}.
 */
public class Holding extends FinancialEntity implements Calculable, Tradable {

    private static final long serialVersionUID = 1L;

    private int holdingId;
    private int stockId;
    private int quantity;
    private double averageBuyPrice;

    /** Live price injected by the service layer so P/L can be computed. */
    private double currentPrice;

    /** Display-only fields joined in from the stocks table. */
    private String symbol;
    private String companyName;
    private String sector;

    public Holding() {
    }

    public Holding(int userId, int stockId, int quantity, double averageBuyPrice) {
        super(userId);
        this.stockId = stockId;
        this.quantity = quantity;
        this.averageBuyPrice = averageBuyPrice;
    }

    // ---------- price-parameterised calculations (used by services and tests) ----------

    public double getCurrentValue(double price) {
        return quantity * price;
    }

    public double getInvestedValue() {
        return quantity * averageBuyPrice;
    }

    public double getProfitLoss(double price) {
        return (price - averageBuyPrice) * quantity;
    }

    public double getProfitLossPercent(double price) {
        if (averageBuyPrice == 0) {
            return 0.0;
        }
        return ((price - averageBuyPrice) / averageBuyPrice) * 100.0;
    }

    // ---------- Calculable ----------

    @Override
    public double calculateValue() {
        return getCurrentValue(currentPrice);
    }

    @Override
    public double calculateProfitLoss() {
        return getProfitLoss(currentPrice);
    }

    @Override
    public double calculateProfitLossPercent() {
        return getProfitLossPercent(currentPrice);
    }

    // ---------- Tradable ----------

    /** Adds shares and recomputes the weighted average cost. */
    @Override
    public void buy(int qty, double price) throws InvalidQuantityException {
        if (qty <= 0) {
            throw new InvalidQuantityException("Quantity must be greater than zero.");
        }
        double newInvested = getInvestedValue() + (qty * price);
        this.quantity += qty;
        this.averageBuyPrice = newInvested / this.quantity;
    }

    /** Removes shares; the average buy price of the remaining shares does not change. */
    @Override
    public void sell(int qty) throws InsufficientSharesException, InvalidQuantityException {
        if (qty <= 0) {
            throw new InvalidQuantityException("Quantity must be greater than zero.");
        }
        if (qty > this.quantity) {
            throw new InsufficientSharesException(
                    "You own only " + this.quantity + " share(s), cannot sell " + qty + ".");
        }
        this.quantity -= qty;
    }

    // ---------- FinancialEntity ----------

    @Override
    public double getValue() {
        return calculateValue();
    }

    @Override
    public String getDescription() {
        return String.format("%d share(s) of %s at avg Rs %.2f",
                quantity, symbol == null ? ("stock #" + stockId) : symbol, averageBuyPrice);
    }

    // ---------- accessors ----------

    public int getHoldingId() { return holdingId; }
    public void setHoldingId(int holdingId) { this.holdingId = holdingId; }

    public int getStockId() { return stockId; }
    public void setStockId(int stockId) { this.stockId = stockId; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public double getAverageBuyPrice() { return averageBuyPrice; }
    public void setAverageBuyPrice(double averageBuyPrice) { this.averageBuyPrice = averageBuyPrice; }

    public double getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(double currentPrice) { this.currentPrice = currentPrice; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getSector() { return sector; }
    public void setSector(String sector) { this.sector = sector; }

    @Override
    public String toString() {
        return getDescription();
    }
}

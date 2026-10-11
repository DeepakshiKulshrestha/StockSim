package com.stocksim.model;

import java.io.Serializable;

/** A price trigger the user set on a stock. Maps to the {@code alerts} table. */
public class PriceAlert implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Matches the ENUM('ABOVE','BELOW') column. */
    public enum Condition { ABOVE, BELOW }

    private int alertId;
    private int userId;
    private int stockId;
    private Condition condition;
    private double targetPrice;
    private boolean triggered;

    /** Display-only, joined in from the stocks table. */
    private String symbol;
    private String companyName;

    public PriceAlert() {
    }

    public PriceAlert(int userId, int stockId, Condition condition, double targetPrice) {
        this.userId = userId;
        this.stockId = stockId;
        this.condition = condition;
        this.targetPrice = targetPrice;
        this.triggered = false;
    }

    /** True when the live price satisfies the condition this alert was set with. */
    public boolean isConditionMet(double currentPrice) {
        if (condition == null) {
            return false;
        }
        return condition == Condition.ABOVE ? currentPrice >= targetPrice
                                            : currentPrice <= targetPrice;
    }

    public String getStatus() { return triggered ? "Triggered" : "Active"; }

    public String getConditionName() { return condition == null ? "" : condition.name(); }

    public int getAlertId() { return alertId; }
    public void setAlertId(int alertId) { this.alertId = alertId; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public int getStockId() { return stockId; }
    public void setStockId(int stockId) { this.stockId = stockId; }

    public Condition getCondition() { return condition; }
    public void setCondition(Condition condition) { this.condition = condition; }

    public double getTargetPrice() { return targetPrice; }
    public void setTargetPrice(double targetPrice) { this.targetPrice = targetPrice; }

    public boolean isTriggered() { return triggered; }
    public void setTriggered(boolean triggered) { this.triggered = triggered; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    @Override
    public String toString() {
        return String.format("Alert: %s %s Rs %.2f [%s]",
                symbol, getConditionName(), targetPrice, getStatus());
    }
}

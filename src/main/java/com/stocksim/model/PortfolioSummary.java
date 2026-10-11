package com.stocksim.model;

/**
 * Everything the dashboard and portfolio screens need about a user's money,
 * computed in one pass by PortfolioService.
 */
public class PortfolioSummary {

    private double cash;             // uninvested balance
    private double invested;         // sum of quantity * average buy price
    private double holdingsValue;    // sum of quantity * current price
    private double totalValue;       // cash + holdingsValue
    private double profitLoss;       // holdingsValue - invested
    private double profitLossPercent;
    private double totalReturnPercent; // against the 1,00,000 starting capital
    private double startingCapital;
    private int holdingCount;

    public double getCash() { return cash; }
    public void setCash(double cash) { this.cash = cash; }

    public double getInvested() { return invested; }
    public void setInvested(double invested) { this.invested = invested; }

    public double getHoldingsValue() { return holdingsValue; }
    public void setHoldingsValue(double holdingsValue) { this.holdingsValue = holdingsValue; }

    public double getTotalValue() { return totalValue; }
    public void setTotalValue(double totalValue) { this.totalValue = totalValue; }

    public double getProfitLoss() { return profitLoss; }
    public void setProfitLoss(double profitLoss) { this.profitLoss = profitLoss; }

    public double getProfitLossPercent() { return profitLossPercent; }
    public void setProfitLossPercent(double profitLossPercent) { this.profitLossPercent = profitLossPercent; }

    public double getTotalReturnPercent() { return totalReturnPercent; }
    public void setTotalReturnPercent(double totalReturnPercent) { this.totalReturnPercent = totalReturnPercent; }

    public double getStartingCapital() { return startingCapital; }
    public void setStartingCapital(double startingCapital) { this.startingCapital = startingCapital; }

    public int getHoldingCount() { return holdingCount; }
    public void setHoldingCount(int holdingCount) { this.holdingCount = holdingCount; }

    @Override
    public String toString() {
        return String.format(
                "PortfolioSummary{cash=%.2f, invested=%.2f, holdings=%.2f, total=%.2f, pl=%.2f (%.2f%%)}",
                cash, invested, holdingsValue, totalValue, profitLoss, profitLossPercent);
    }
}

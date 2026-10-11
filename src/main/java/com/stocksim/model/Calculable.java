package com.stocksim.model;

/**
 * Implemented by entities whose worth and profit/loss can be computed.
 * Demonstrates: interfaces, default methods.
 */
public interface Calculable {

    /** Current market value. */
    double calculateValue();

    /** Absolute profit or loss in rupees. */
    double calculateProfitLoss();

    /** Profit/loss as a percentage of the amount invested. */
    default double calculateProfitLossPercent() {
        double invested = calculateValue() - calculateProfitLoss();
        return invested == 0 ? 0.0 : (calculateProfitLoss() / invested) * 100.0;
    }
}

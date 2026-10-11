package com.stocksim.model;

import java.time.LocalDateTime;

/**
 * An investment target such as "Reach Rs 1,50,000".
 * Also extends {@link FinancialEntity} - a second subclass proving the abstraction.
 */
public class Goal extends FinancialEntity {

    private static final long serialVersionUID = 1L;

    private int goalId;
    private String goalName;
    private double targetAmount;
    private LocalDateTime createdAt;

    /** Injected by the service layer: the user's current portfolio value. */
    private double currentAmount;

    public Goal() {
    }

    public Goal(int userId, String goalName, double targetAmount) {
        super(userId);
        this.goalName = goalName;
        this.targetAmount = targetAmount;
    }

    /** Progress clamped to 0.0 .. 1.0 so it can feed a JavaFX ProgressBar directly. */
    public double getProgress(double currentPortfolioValue) {
        if (targetAmount <= 0) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, currentPortfolioValue / targetAmount));
    }

    public double getProgressPercent(double currentPortfolioValue) {
        return getProgress(currentPortfolioValue) * 100.0;
    }

    public boolean isAchieved(double currentPortfolioValue) {
        return currentPortfolioValue >= targetAmount;
    }

    @Override
    public double getValue() {
        return targetAmount;
    }

    @Override
    public String getDescription() {
        return String.format("%s - target Rs %,.2f", goalName, targetAmount);
    }

    public int getGoalId() { return goalId; }
    public void setGoalId(int goalId) { this.goalId = goalId; }

    public String getGoalName() { return goalName; }
    public void setGoalName(String goalName) { this.goalName = goalName; }

    public double getTargetAmount() { return targetAmount; }
    public void setTargetAmount(double targetAmount) { this.targetAmount = targetAmount; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public double getCurrentAmount() { return currentAmount; }
    public void setCurrentAmount(double currentAmount) { this.currentAmount = currentAmount; }

    @Override
    public String toString() {
        return getDescription();
    }
}

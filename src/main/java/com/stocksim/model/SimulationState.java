package com.stocksim.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Snapshot of a running simulation, written to simulation_state.ser with
 * ObjectOutputStream and read back with ObjectInputStream.
 * Demonstrates: Java serialization (Unit 3).
 */
public class SimulationState implements Serializable {

    private static final long serialVersionUID = 1L;

    private int userId;
    private String username;
    private int simulationDay;
    private double balance;
    private LocalDateTime savedAt;

    /** stockId -> price on the saved day. */
    private Map<Integer, Double> stockPrices = new HashMap<>();

    /** transient: rebuilt from the database on load, never written to the file. */
    private transient String loadNote;

    public SimulationState() {
    }

    public SimulationState(int userId, String username, int simulationDay, double balance) {
        this.userId = userId;
        this.username = username;
        this.simulationDay = simulationDay;
        this.balance = balance;
        this.savedAt = LocalDateTime.now();
    }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public int getSimulationDay() { return simulationDay; }
    public void setSimulationDay(int simulationDay) { this.simulationDay = simulationDay; }

    public double getBalance() { return balance; }
    public void setBalance(double balance) { this.balance = balance; }

    public LocalDateTime getSavedAt() { return savedAt; }
    public void setSavedAt(LocalDateTime savedAt) { this.savedAt = savedAt; }

    public Map<Integer, Double> getStockPrices() { return stockPrices; }
    public void setStockPrices(Map<Integer, Double> stockPrices) { this.stockPrices = stockPrices; }

    public String getLoadNote() { return loadNote; }
    public void setLoadNote(String loadNote) { this.loadNote = loadNote; }

    @Override
    public String toString() {
        return String.format("SimulationState{user='%s', day=%d, balance=%.2f, savedAt=%s}",
                username, simulationDay, balance, savedAt);
    }
}

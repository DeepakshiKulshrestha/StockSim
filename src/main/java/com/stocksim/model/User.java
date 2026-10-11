package com.stocksim.model;

import java.io.Serializable;
import java.time.LocalDateTime;

/** A registered StockSim user. Maps to the {@code users} table. */
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    private int userId;
    private String name;
    private String username;
    private String email;
    private String password;      // SHA-256 hash, never plain text
    private double balance;       // virtual cash
    private int simDay;
    private LocalDateTime createdAt;

    public User() {
    }

    public User(String name, String username, String email, String password, double balance) {
        this.name = name;
        this.username = username;
        this.email = email;
        this.password = password;
        this.balance = balance;
        this.simDay = 1;
    }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public double getBalance() { return balance; }
    public void setBalance(double balance) { this.balance = balance; }

    public int getSimDay() { return simDay; }
    public void setSimDay(int simDay) { this.simDay = simDay; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return String.format("User{id=%d, username='%s', balance=%.2f, simDay=%d}",
                userId, username, balance, simDay);
    }
}

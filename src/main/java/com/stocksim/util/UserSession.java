package com.stocksim.util;

import com.stocksim.model.User;

/**
 * Singleton holding the currently logged-in user so every screen can reach it
 * without passing the User object through each controller.
 * Demonstrates: singleton pattern, static members (Unit 1).
 */
public final class UserSession {

    private static UserSession instance;

    private User currentUser;

    private UserSession() {
    }

    public static synchronized UserSession getInstance() {
        if (instance == null) {
            instance = new UserSession();
        }
        return instance;
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public boolean isLoggedIn() {
        return currentUser != null;
    }

    public int getUserId() {
        return currentUser == null ? -1 : currentUser.getUserId();
    }

    public String getUsername() {
        return currentUser == null ? "" : currentUser.getUsername();
    }

    public int getSimulationDay() {
        return currentUser == null ? 1 : currentUser.getSimDay();
    }

    public void setSimulationDay(int day) {
        if (currentUser != null) {
            currentUser.setSimDay(day);
        }
    }

    public double getBalance() {
        return currentUser == null ? 0.0 : currentUser.getBalance();
    }

    public void setBalance(double balance) {
        if (currentUser != null) {
            currentUser.setBalance(balance);
        }
    }

    /** Called on logout - clears the session so the next login starts clean. */
    public void logout() {
        this.currentUser = null;
    }
}

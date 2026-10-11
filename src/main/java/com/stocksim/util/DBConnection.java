package com.stocksim.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Singleton that owns the database configuration and hands out JDBC connections.
 *
 * The singleton is created once (it loads the driver and reads application.properties
 * a single time); each call to {@link #get()} returns a fresh Connection that the
 * caller closes, normally with try-with-resources in a DAO method. Keeping one
 * connection per operation is what lets TradingService run a buy or a sell inside
 * its own commit/rollback transaction without disturbing anything else.
 *
 * Demonstrates: singleton pattern, static members, JDBC (Units 1 and 4).
 */
public final class DBConnection {

    private static final Logger LOGGER = Logger.getLogger(DBConnection.class.getName());

    private static DBConnection instance;

    private final String url;
    private final String user;
    private final String password;

    private DBConnection() {
        this.url = ConfigLoader.get("db.url");
        this.user = ConfigLoader.get("db.user");
        this.password = ConfigLoader.get("db.password");
        try {
            Class.forName(ConfigLoader.get("db.driver", "com.mysql.cj.jdbc.Driver"));
            LOGGER.info("MySQL JDBC driver loaded for " + url.split("\\?")[0]);
        } catch (ClassNotFoundException e) {
            LOGGER.log(Level.SEVERE, "MySQL JDBC driver not found on classpath", e);
            throw new IllegalStateException("MySQL JDBC driver not found", e);
        }
    }

    public static synchronized DBConnection getInstance() {
        if (instance == null) {
            instance = new DBConnection();
        }
        return instance;
    }

    /** A new connection in auto-commit mode. The caller owns it and must close it. */
    public Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(url, user, password);
        conn.setAutoCommit(true);
        return conn;
    }

    /**
     * Same as {@link #getConnection()}. Kept as a separate name because
     * TradingService reads better when it is explicit that it is taking a
     * connection of its own to run a transaction on.
     */
    public Connection newConnection() throws SQLException {
        return getConnection();
    }

    /** Shorthand used throughout the DAO layer so DAOs never touch DriverManager. */
    public static Connection get() throws SQLException {
        return getInstance().getConnection();
    }

    /** True when the database is reachable - checked by Main before showing the UI. */
    public static boolean testConnection() {
        try (Connection conn = getInstance().getConnection()) {
            return conn.isValid(3);
        } catch (SQLException | RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Database connection test failed: " + e.getMessage(), e);
            return false;
        }
    }
}

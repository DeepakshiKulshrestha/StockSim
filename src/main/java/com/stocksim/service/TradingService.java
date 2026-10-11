package com.stocksim.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.stocksim.dao.HoldingDAO;
import com.stocksim.dao.StockDAO;
import com.stocksim.dao.TransactionDAO;
import com.stocksim.dao.UserDAO;
import com.stocksim.exception.InsufficientBalanceException;
import com.stocksim.exception.InsufficientSharesException;
import com.stocksim.exception.InvalidQuantityException;
import com.stocksim.exception.UserNotFoundException;
import com.stocksim.model.Holding;
import com.stocksim.model.Stock;
import com.stocksim.model.Transaction;
import com.stocksim.model.User;
import com.stocksim.util.DBConnection;

/**
 * Buying and selling. Each trade touches three tables (users, holdings, transactions)
 * and must be all-or-nothing, so both methods run inside an explicit JDBC transaction:
 * setAutoCommit(false) -> three statements -> commit(), or rollback() on any failure.
 */
public class TradingService {

    private static final Logger LOGGER = Logger.getLogger(TradingService.class.getName());

    private final UserDAO userDAO;
    private final StockDAO stockDAO;
    private final HoldingDAO holdingDAO;
    private final TransactionDAO transactionDAO;

    public TradingService() {
        this(new UserDAO(), new StockDAO(), new HoldingDAO(), new TransactionDAO());
    }

    public TradingService(UserDAO userDAO, StockDAO stockDAO,
                          HoldingDAO holdingDAO, TransactionDAO transactionDAO) {
        this.userDAO = userDAO;
        this.stockDAO = stockDAO;
        this.holdingDAO = holdingDAO;
        this.transactionDAO = transactionDAO;
    }

    /**
     * Buys {@code quantity} shares at the stock's current price.
     *
     * @return the recorded transaction
     */
    public Transaction buyStock(int userId, int stockId, int quantity)
            throws InvalidQuantityException, InsufficientBalanceException, UserNotFoundException {

        if (quantity <= 0) {
            throw new InvalidQuantityException("Quantity must be a whole number greater than zero.");
        }

        User user = requireUser(userId);
        Stock stock = requireStock(stockId);

        double price = stock.getCurrentPrice();
        double totalCost = round2(price * quantity);

        if (user.getBalance() < totalCost) {
            LOGGER.warning(String.format(
                    "Insufficient balance for user %d. Required: %.2f, Available: %.2f",
                    userId, totalCost, user.getBalance()));
            throw new InsufficientBalanceException(String.format(
                    "Not enough cash. This trade costs Rs %,.2f but you only have Rs %,.2f.",
                    totalCost, user.getBalance()));
        }

        double newBalance = round2(user.getBalance() - totalCost);
        Transaction txn = new Transaction(userId, stockId, Transaction.Type.BUY,
                quantity, price, user.getSimDay());
        txn.setSymbol(stock.getSymbol());
        txn.setCompanyName(stock.getCompanyName());

        Connection conn = null;
        try {
            conn = DBConnection.getInstance().newConnection();
            conn.setAutoCommit(false);                                   // ---- BEGIN ----

            if (!userDAO.updateBalance(conn, userId, newBalance)) {
                throw new SQLException("Balance update affected no rows");
            }
            if (!holdingDAO.upsert(conn, userId, stockId, quantity, price)) {
                throw new SQLException("Holding upsert affected no rows");
            }
            if (!transactionDAO.save(conn, txn)) {
                throw new SQLException("Transaction insert affected no rows");
            }

            conn.commit();                                               // ---- COMMIT ----
            user.setBalance(newBalance);

            LOGGER.info(String.format("BUY: %d %s shares at Rs %.2f - Total: Rs %.2f (user %d)",
                    quantity, stock.getSymbol(), price, totalCost, userId));
            return txn;

        } catch (SQLException e) {
            rollback(conn);                                              // ---- ROLLBACK ----
            LOGGER.log(Level.SEVERE, "BUY failed and was rolled back for user " + userId, e);
            throw new IllegalStateException("The purchase could not be completed: " + e.getMessage(), e);
        } finally {
            closeQuietly(conn);
        }
    }

    /**
     * Sells {@code quantity} shares at the stock's current price.
     *
     * @return the recorded transaction
     */
    public Transaction sellStock(int userId, int stockId, int quantity)
            throws InvalidQuantityException, InsufficientSharesException, UserNotFoundException {

        if (quantity <= 0) {
            throw new InvalidQuantityException("Quantity must be a whole number greater than zero.");
        }

        User user = requireUser(userId);
        Stock stock = requireStock(stockId);

        Holding holding = holdingDAO.findByUserAndStock(userId, stockId)
                .orElseThrow(() -> new InsufficientSharesException(
                        "You do not own any shares of " + stock.getSymbol() + "."));

        if (holding.getQuantity() < quantity) {
            LOGGER.warning(String.format("Insufficient shares for user %d on %s. Wanted %d, owns %d",
                    userId, stock.getSymbol(), quantity, holding.getQuantity()));
            throw new InsufficientSharesException(String.format(
                    "You own only %d share(s) of %s, so you cannot sell %d.",
                    holding.getQuantity(), stock.getSymbol(), quantity));
        }

        double price = stock.getCurrentPrice();
        double saleAmount = round2(price * quantity);
        double newBalance = round2(user.getBalance() + saleAmount);

        Transaction txn = new Transaction(userId, stockId, Transaction.Type.SELL,
                quantity, price, user.getSimDay());
        txn.setSymbol(stock.getSymbol());
        txn.setCompanyName(stock.getCompanyName());

        Connection conn = null;
        try {
            conn = DBConnection.getInstance().newConnection();
            conn.setAutoCommit(false);                                   // ---- BEGIN ----

            if (!userDAO.updateBalance(conn, userId, newBalance)) {
                throw new SQLException("Balance update affected no rows");
            }
            if (!holdingDAO.reduce(conn, userId, stockId, quantity)) {
                throw new SQLException("Holding reduce affected no rows - shares changed concurrently");
            }
            if (!transactionDAO.save(conn, txn)) {
                throw new SQLException("Transaction insert affected no rows");
            }

            conn.commit();                                               // ---- COMMIT ----
            user.setBalance(newBalance);

            double realised = round2((price - holding.getAverageBuyPrice()) * quantity);
            LOGGER.info(String.format(
                    "SELL: %d %s shares at Rs %.2f - Total: Rs %.2f, realised P/L: Rs %.2f (user %d)",
                    quantity, stock.getSymbol(), price, saleAmount, realised, userId));
            return txn;

        } catch (SQLException e) {
            rollback(conn);                                              // ---- ROLLBACK ----
            LOGGER.log(Level.SEVERE, "SELL failed and was rolled back for user " + userId, e);
            throw new IllegalStateException("The sale could not be completed: " + e.getMessage(), e);
        } finally {
            closeQuietly(conn);
        }
    }

    /** Cost of a prospective buy, for the live "Total Cost" label in the dialog. */
    public double calculateCost(int stockId, int quantity) {
        return stockDAO.findById(stockId)
                .map(s -> round2(s.getCurrentPrice() * Math.max(0, quantity)))
                .orElse(0.0);
    }

    /** Largest number of shares the user could afford right now. */
    public int maxAffordableQuantity(int userId, int stockId) {
        Optional<User> user = userDAO.findById(userId);
        Optional<Stock> stock = stockDAO.findById(stockId);
        if (user.isEmpty() || stock.isEmpty() || stock.get().getCurrentPrice() <= 0) {
            return 0;
        }
        return (int) Math.floor(user.get().getBalance() / stock.get().getCurrentPrice());
    }

    // ---------------------------------------------------------------

    private User requireUser(int userId) throws UserNotFoundException {
        return userDAO.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("No user found with id " + userId));
    }

    private Stock requireStock(int stockId) {
        return stockDAO.findById(stockId)
                .orElseThrow(() -> new IllegalArgumentException("No stock found with id " + stockId));
    }

    private void rollback(Connection conn) {
        if (conn != null) {
            try {
                conn.rollback();
                LOGGER.warning("JDBC transaction rolled back.");
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Rollback itself failed", e);
            }
        }
    }

    private void closeQuietly(Connection conn) {
        if (conn != null) {
            try {
                conn.setAutoCommit(true);
                conn.close();
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Closing the trade connection failed", e);
            }
        }
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}

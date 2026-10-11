package com.stocksim.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

import com.stocksim.dao.HoldingDAO;
import com.stocksim.dao.TransactionDAO;
import com.stocksim.dao.UserDAO;
import com.stocksim.model.Holding;
import com.stocksim.model.PortfolioSummary;
import com.stocksim.model.Transaction;
import com.stocksim.util.ConfigLoader;

/** Aggregates holdings + cash into the numbers shown on the dashboard and portfolio screens. */
public class PortfolioService {

    private static final Logger LOGGER = Logger.getLogger(PortfolioService.class.getName());

    private final UserDAO userDAO;
    private final HoldingDAO holdingDAO;
    private final TransactionDAO transactionDAO;

    public PortfolioService() {
        this(new UserDAO(), new HoldingDAO(), new TransactionDAO());
    }

    public PortfolioService(UserDAO userDAO, HoldingDAO holdingDAO, TransactionDAO transactionDAO) {
        this.userDAO = userDAO;
        this.holdingDAO = holdingDAO;
        this.transactionDAO = transactionDAO;
    }

    /** Holdings with symbol, sector and live price already attached, biggest position first. */
    public List<Holding> getHoldings(int userId) {
        List<Holding> holdings = new ArrayList<>(holdingDAO.findByUserId(userId));
        // Comparator as a lambda (Unit 2): sort by current market value, descending
        holdings.sort(Comparator.comparingDouble(Holding::calculateValue).reversed());
        return holdings;
    }

    public PortfolioSummary getPortfolioSummary(int userId) {
        double cash = userDAO.findById(userId).map(u -> u.getBalance()).orElse(0.0);
        List<Holding> holdings = holdingDAO.findByUserId(userId);

        double invested = 0.0;
        double holdingsValue = 0.0;
        for (Holding holding : holdings) {
            invested += holding.getInvestedValue();
            holdingsValue += holding.calculateValue();
        }

        double startingCapital = ConfigLoader.getDouble("sim.starting.balance", 100000.00);
        double totalValue = cash + holdingsValue;
        double profitLoss = holdingsValue - invested;

        PortfolioSummary summary = new PortfolioSummary();
        summary.setCash(round2(cash));
        summary.setInvested(round2(invested));
        summary.setHoldingsValue(round2(holdingsValue));
        summary.setTotalValue(round2(totalValue));
        summary.setProfitLoss(round2(profitLoss));
        summary.setProfitLossPercent(invested == 0 ? 0.0 : round2((profitLoss / invested) * 100.0));
        summary.setStartingCapital(startingCapital);
        summary.setTotalReturnPercent(startingCapital == 0 ? 0.0
                : round2(((totalValue - startingCapital) / startingCapital) * 100.0));
        summary.setHoldingCount(holdings.size());
        return summary;
    }

    /** Total portfolio value (cash + holdings) - what investment goals measure against. */
    public double getPortfolioValue(int userId) {
        return getPortfolioSummary(userId).getTotalValue();
    }

    /** Best performing holding by P/L percentage, or null when the user owns nothing. */
    public Holding getBestPerformer(int userId) {
        return holdingDAO.findByUserId(userId).stream()
                .max(Comparator.comparingDouble(Holding::calculateProfitLossPercent))
                .orElse(null);
    }

    /** Worst performing holding by P/L percentage, or null when the user owns nothing. */
    public Holding getWorstPerformer(int userId) {
        return holdingDAO.findByUserId(userId).stream()
                .min(Comparator.comparingDouble(Holding::calculateProfitLossPercent))
                .orElse(null);
    }

    public int getTotalTrades(int userId) {
        return transactionDAO.countByUser(userId);
    }

    public int getBuyCount(int userId) {
        return transactionDAO.countByUserAndType(userId, Transaction.Type.BUY);
    }

    public int getSellCount(int userId) {
        return transactionDAO.countByUserAndType(userId, Transaction.Type.SELL);
    }

    /**
     * Counts SELL trades that made money, by comparing the sale price with the
     * weighted average cost of the BUY trades that preceded it for the same stock.
     */
    public int[] getProfitableAndLossMakingTrades(int userId) {
        List<Transaction> all = transactionDAO.findByUserId(userId);
        all.sort(Comparator.comparingInt(Transaction::getTransactionId));

        java.util.Map<Integer, double[]> running = new java.util.HashMap<>(); // stockId -> {qty, totalCost}
        int profitable = 0;
        int lossMaking = 0;

        for (Transaction txn : all) {
            double[] position = running.computeIfAbsent(txn.getStockId(), k -> new double[2]);
            if (txn.isBuy()) {
                position[0] += txn.getQuantity();
                position[1] += txn.getTotalAmount();
            } else {
                double avgCost = position[0] > 0 ? position[1] / position[0] : txn.getPrice();
                if (txn.getPrice() > avgCost) {
                    profitable++;
                } else if (txn.getPrice() < avgCost) {
                    lossMaking++;
                }
                double soldCost = avgCost * txn.getQuantity();
                position[0] = Math.max(0, position[0] - txn.getQuantity());
                position[1] = Math.max(0, position[1] - soldCost);
            }
        }
        LOGGER.fine("Trade stats for user " + userId + ": " + profitable + " profitable, "
                + lossMaking + " loss-making");
        return new int[] { profitable, lossMaking };
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}

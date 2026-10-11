package com.stocksim.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.stocksim.dao.TransactionDAO;
import com.stocksim.dao.UserDAO;
import com.stocksim.model.Holding;
import com.stocksim.model.PortfolioSummary;
import com.stocksim.model.Transaction;
import com.stocksim.service.AnalysisService;
import com.stocksim.service.PortfolioService;
import com.stocksim.service.RiskService;
import com.stocksim.util.Formats;
import com.stocksim.util.UserSession;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;

/** Overall performance: return against the starting capital, trade stats and three charts. */
public class PerformanceController extends BaseController {

    private static final Logger LOGGER = Logger.getLogger(PerformanceController.class.getName());

    /** How many points the portfolio-value line is drawn with. */
    private static final int VALUE_CHART_POINTS = 40;

    @FXML private Label initialCapitalLabel;
    @FXML private Label currentValueLabel;
    @FXML private Label totalReturnLabel;
    @FXML private Label netGainLabel;
    @FXML private Label bestLabel;
    @FXML private Label worstLabel;
    @FXML private Label totalTradesLabel;
    @FXML private Label buySellLabel;
    @FXML private Label profitableLabel;
    @FXML private Label lossMakingLabel;
    @FXML private Label valueChartNote;

    @FXML private LineChart<Number, Number> valueChart;
    @FXML private NumberAxis valueXAxis;
    @FXML private NumberAxis valueYAxis;
    @FXML private PieChart sectorPieChart;
    @FXML private BarChart<String, Number> plChart;

    private final PortfolioService portfolioService = new PortfolioService();
    private final RiskService riskService = new RiskService();
    private final AnalysisService analysisService = new AnalysisService();
    private final TransactionDAO transactionDAO = new TransactionDAO();
    private final UserDAO userDAO = new UserDAO();

    @Override
    protected String screenName() {
        return "Performance";
    }

    @Override
    protected void onInit() {
        refreshScreen();
    }

    @Override
    protected void refreshScreen() {
        userDAO.findById(userId()).ifPresent(u -> UserSession.getInstance().setCurrentUser(u));

        PortfolioSummary summary = portfolioService.getPortfolioSummary(userId());
        List<Holding> holdings = portfolioService.getHoldings(userId());

        showSummary(summary);
        showTradeStats();
        showPerformers(holdings);

        drawValueChart(summary);
        drawSectorChart();
        drawProfitLossChart(holdings);

        refreshHeader();
    }

    private void showSummary(PortfolioSummary summary) {
        initialCapitalLabel.setText(Formats.money(summary.getStartingCapital()));
        currentValueLabel.setText(Formats.money(summary.getTotalValue()));

        totalReturnLabel.setText(Formats.signedPercent(summary.getTotalReturnPercent()));
        applySignStyle(totalReturnLabel, summary.getTotalReturnPercent());

        double netGain = summary.getTotalValue() - summary.getStartingCapital();
        netGainLabel.setText(Formats.signedMoney(netGain));
        applySignStyle(netGainLabel, netGain);
    }

    private void showTradeStats() {
        int total = portfolioService.getTotalTrades(userId());
        totalTradesLabel.setText(String.valueOf(total));
        buySellLabel.setText(portfolioService.getBuyCount(userId()) + " buys, "
                + portfolioService.getSellCount(userId()) + " sells");

        int[] stats = portfolioService.getProfitableAndLossMakingTrades(userId());
        profitableLabel.setText(stats[0] + " profitable");
        applySignStyle(profitableLabel, stats[0] > 0 ? 1 : 0);
        lossMakingLabel.setText(stats[1] + " loss-making");
    }

    private void showPerformers(List<Holding> holdings) {
        if (holdings.isEmpty()) {
            bestLabel.setText("-");
            worstLabel.setText("-");
            return;
        }

        Holding best = portfolioService.getBestPerformer(userId());
        Holding worst = portfolioService.getWorstPerformer(userId());

        bestLabel.setText(best.getSymbol() + "  "
                + Formats.signedPercent(best.calculateProfitLossPercent()));
        applySignStyle(bestLabel, best.calculateProfitLossPercent());

        worstLabel.setText(worst.getSymbol() + "  "
                + Formats.signedPercent(worst.calculateProfitLossPercent()));
        applySignStyle(worstLabel, worst.calculateProfitLossPercent());
    }

    /**
     * Replays the user's trades day by day to plot what the portfolio was worth.
     * Cash is derived backwards from the current balance, so the line always ends
     * on today's real total.
     */
    private void drawValueChart(PortfolioSummary summary) {
        valueChart.getData().clear();

        List<Transaction> transactions = new ArrayList<>(transactionDAO.findByUserId(userId()));
        transactions.sort(java.util.Comparator.comparingInt(Transaction::getTransactionId));

        int today = simulationDay();
        if (transactions.isEmpty()) {
            valueChartNote.setText(
                    "No trades yet, so the portfolio has simply been "
                            + Formats.money(summary.getStartingCapital()) + " in cash.");
            XYChart.Series<Number, Number> flat = new XYChart.Series<>();
            flat.getData().add(new XYChart.Data<>(1, summary.getStartingCapital()));
            flat.getData().add(new XYChart.Data<>(today, summary.getStartingCapital()));
            valueChart.getData().add(flat);
            autoRange(valueYAxis, summary.getStartingCapital() * 0.95,
                      summary.getStartingCapital() * 1.05);
            return;
        }

        int firstDay = transactions.get(0).getSimulationDay();
        int startDay = Math.max(1, firstDay - 1);
        int step = Math.max(1, (today - startDay) / VALUE_CHART_POINTS);

        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName("Portfolio value");

        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;

        for (int day = startDay; day <= today; day += step) {
            double value = valueOnDay(transactions, day, summary.getStartingCapital());
            series.getData().add(new XYChart.Data<>(day, value));
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        // always finish exactly on today's real number
        series.getData().add(new XYChart.Data<>(today, summary.getTotalValue()));
        min = Math.min(min, summary.getTotalValue());
        max = Math.max(max, summary.getTotalValue());

        valueChart.getData().add(series);
        autoRange(valueYAxis, min, max);

        valueXAxis.setAutoRanging(false);
        valueXAxis.setLowerBound(startDay);
        valueXAxis.setUpperBound(today);
        valueXAxis.setTickUnit(Math.max(1, (today - startDay) / 8.0));

        valueChartNote.setText("Reconstructed from your " + transactions.size()
                + " trade(s) between simulation day " + firstDay + " and day " + today + ".");
    }

    /** Cash + holdings as they stood at the end of {@code day}. */
    private double valueOnDay(List<Transaction> transactions, int day, double startingCapital) {
        double cash = startingCapital;
        Map<Integer, Integer> shares = new java.util.HashMap<>();

        for (Transaction txn : transactions) {
            if (txn.getSimulationDay() > day) {
                break;                       // transactions are in chronological order
            }
            if (txn.isBuy()) {
                cash -= txn.getTotalAmount();
                shares.merge(txn.getStockId(), txn.getQuantity(), Integer::sum);
            } else {
                cash += txn.getTotalAmount();
                shares.merge(txn.getStockId(), -txn.getQuantity(), Integer::sum);
            }
        }

        double holdingsValue = 0.0;
        for (Map.Entry<Integer, Integer> entry : shares.entrySet()) {
            if (entry.getValue() > 0) {
                holdingsValue += entry.getValue() * analysisService.priceOn(entry.getKey(), day);
            }
        }
        return cash + holdingsValue;
    }

    private void drawSectorChart() {
        Map<String, Double> sectors = riskService.getSectorAllocation(userId());
        sectorPieChart.setData(FXCollections.observableArrayList(
                sectors.entrySet().stream()
                       .map(e -> new PieChart.Data(
                               e.getKey() + " " + Formats.percent(e.getValue()), e.getValue()))
                       .toList()));
        sectorPieChart.setTitle(sectors.isEmpty() ? "No holdings yet" : "");
    }

    private void drawProfitLossChart(List<Holding> holdings) {
        plChart.getData().clear();
        if (holdings.isEmpty()) {
            return;
        }

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("P/L");
        for (Holding holding : holdings) {
            series.getData().add(
                    new XYChart.Data<>(holding.getSymbol(), holding.calculateProfitLoss()));
        }
        plChart.getData().add(series);

        for (XYChart.Data<String, Number> item : series.getData()) {
            if (item.getNode() != null) {
                boolean up = item.getYValue().doubleValue() >= 0;
                item.getNode().setStyle("-fx-bar-fill: " + (up ? "#4caf50" : "#f44336") + ";");
            }
        }
        LOGGER.fine("P/L chart drawn for " + holdings.size() + " holding(s)");
    }

    private void autoRange(NumberAxis axis, double min, double max) {
        double padding = Math.max(100.0, (max - min) * 0.1);
        axis.setAutoRanging(false);
        axis.setLowerBound(Math.floor(min - padding));
        axis.setUpperBound(Math.ceil(max + padding));
        axis.setTickUnit(Math.max(1, (axis.getUpperBound() - axis.getLowerBound()) / 8));
    }
}

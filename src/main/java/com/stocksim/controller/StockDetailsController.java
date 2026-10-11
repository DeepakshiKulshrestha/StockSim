package com.stocksim.controller;

import java.util.List;
import java.util.logging.Logger;

import com.stocksim.dao.HoldingDAO;
import com.stocksim.dao.StockDAO;
import com.stocksim.dao.UserDAO;
import com.stocksim.model.Stock;
import com.stocksim.model.StockAnalysis;
import com.stocksim.model.StockPrice;
import com.stocksim.service.AnalysisService;
import com.stocksim.util.Formats;
import com.stocksim.util.SceneManager;
import com.stocksim.util.UserSession;

import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;

/**
 * One stock in detail: real historical price chart, metrics from AnalysisService,
 * and the BUY / SELL entry points.
 */
public class StockDetailsController extends BaseController {

    private static final Logger LOGGER = Logger.getLogger(StockDetailsController.class.getName());

    @FXML private Label companyLabel;
    @FXML private Label symbolSectorLabel;
    @FXML private Label priceLabel;
    @FXML private Label changeLabel;
    @FXML private Label holdingLabel;

    @FXML private LineChart<Number, Number> priceChart;
    @FXML private NumberAxis xAxis;
    @FXML private NumberAxis yAxis;
    @FXML private ToggleGroup periodGroup;
    @FXML private ToggleButton oneDayToggle;
    @FXML private ToggleButton oneWeekToggle;
    @FXML private ToggleButton oneMonthToggle;
    @FXML private ToggleButton sixMonthToggle;
    @FXML private ToggleButton oneYearToggle;

    @FXML private Label highLabel;
    @FXML private Label lowLabel;
    @FXML private Label volatilityLabel;
    @FXML private Label sevenDayLabel;
    @FXML private Label thirtyDayLabel;
    @FXML private Label movingAverageLabel;
    @FXML private Label movingAverage20Label;
    @FXML private Label trendLabel;
    @FXML private Label momentumLabel;
    @FXML private Label riskLabel;

    private final StockDAO stockDAO = new StockDAO();
    private final HoldingDAO holdingDAO = new HoldingDAO();
    private final UserDAO userDAO = new UserDAO();
    private final AnalysisService analysisService = new AnalysisService();
    private final TradeDialog tradeDialog = new TradeDialog();

    private int stockId = -1;
    private Stock stock;

    @Override
    protected String screenName() {
        return "Market";                 // reached from the market, so keep Market highlighted
    }

    @Override
    protected void onInit() {
        // periods are held on the toggles so the chart knows how many days to draw
        oneDayToggle.setUserData(30);    // a single day is not a chart, so 1D shows the last month
        oneWeekToggle.setUserData(7);
        oneMonthToggle.setUserData(30);
        sixMonthToggle.setUserData(126);
        oneYearToggle.setUserData(252);

        periodGroup.selectedToggleProperty().addListener((obs, old, now) -> {
            if (now == null) {
                periodGroup.selectToggle(old);   // never leave the group with nothing selected
            } else if (stock != null) {
                drawChart();
            }
        });
    }

    /** Called by SceneManager right after this screen is loaded. */
    public void showStock(int stockId) {
        this.stockId = stockId;
        refreshScreen();
    }

    @Override
    protected void refreshScreen() {
        if (stockId <= 0) {
            return;
        }
        userDAO.findById(userId()).ifPresent(u -> UserSession.getInstance().setCurrentUser(u));

        stock = stockDAO.findById(stockId).orElse(null);
        if (stock == null) {
            SceneManager.error("Stock Not Found", "That stock is no longer available.",
                    "Returning to the market.");
            SceneManager.switchTo("market.fxml", "Market");
            return;
        }

        StockAnalysis analysis = analysisService.analyse(stockId, simulationDay());
        showHeader(analysis);
        showMetrics(analysis);
        showHolding();
        drawChart();
        refreshHeader();
    }

    private void showHeader(StockAnalysis a) {
        companyLabel.setText(a.getCompanyName());
        symbolSectorLabel.setText(a.getSymbol() + "  |  " + a.getSector()
                + "  |  Simulation day " + a.getSimulationDay());

        priceLabel.setText(Formats.money(a.getCurrentPrice()));
        changeLabel.setText(Formats.signedMoney(a.getDailyChange())
                + "  (" + Formats.signedPercent(a.getDailyChangePercent()) + ") today");
        applySignStyle(changeLabel, a.getDailyChange());
    }

    private void showMetrics(StockAnalysis a) {
        highLabel.setText(Formats.money(a.getWeek52High()));
        lowLabel.setText(Formats.money(a.getWeek52Low()));

        volatilityLabel.setText(a.getVolatilityLabel()
                + " (" + Formats.percent(a.getVolatilityPercent()) + ")");

        sevenDayLabel.setText(Formats.signedPercent(a.getSevenDayChangePercent()));
        applySignStyle(sevenDayLabel, a.getSevenDayChangePercent());

        thirtyDayLabel.setText(Formats.signedPercent(a.getThirtyDayChangePercent()));
        applySignStyle(thirtyDayLabel, a.getThirtyDayChangePercent());

        movingAverageLabel.setText(Formats.money(a.getMovingAverage5()) + " (5d)");
        movingAverage20Label.setText("20-day: " + Formats.money(a.getMovingAverage20()));

        trendLabel.setText(a.getTrendLabel());
        applySignStyle(trendLabel, switch (a.getTrendLabel()) {
            case "Positive" -> 1;
            case "Negative" -> -1;
            default -> 0;
        });

        momentumLabel.setText(a.getMomentumLabel());
        applySignStyle(momentumLabel, "Positive".equals(a.getMomentumLabel()) ? 1 : -1);

        riskLabel.setText(a.getRiskProfile());
        riskLabel.getStyleClass().removeAll("positive", "negative", "neutral");
        riskLabel.getStyleClass().add(switch (a.getRiskProfile()) {
            case "Low" -> "positive";
            case "High" -> "negative";
            default -> "neutral";
        });
    }

    private void showHolding() {
        int owned = holdingDAO.getQuantity(userId(), stockId);
        if (owned == 0) {
            holdingLabel.setText("You do not own any " + stock.getSymbol()
                    + " shares yet.   Cash available: "
                    + Formats.money(currentUser().getBalance()));
        } else {
            double value = owned * stock.getCurrentPrice();
            holdingLabel.setText("You own " + owned + " share(s) worth " + Formats.money(value)
                    + ".   Cash available: " + Formats.money(currentUser().getBalance()));
        }
    }

    /** Draws the real historical closes for the selected period. */
    private void drawChart() {
        Toggle selected = periodGroup.getSelectedToggle();
        int days = selected == null ? 30 : (int) selected.getUserData();
        int day = simulationDay();

        List<StockPrice> series = analysisService.getPriceSeries(stockId, day, days);
        priceChart.getData().clear();

        if (series.isEmpty()) {
            priceChart.setTitle("No price history available");
            return;
        }

        XYChart.Series<Number, Number> chartSeries = new XYChart.Series<>();
        chartSeries.setName(stock.getSymbol());

        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (StockPrice price : series) {
            chartSeries.getData().add(
                    new XYChart.Data<>(price.getSimulationDay(), price.getPrice()));
            min = Math.min(min, price.getPrice());
            max = Math.max(max, price.getPrice());
        }

        // pad the value axis a little so the line is not glued to the edges
        double padding = Math.max(1.0, (max - min) * 0.08);
        yAxis.setAutoRanging(false);
        yAxis.setLowerBound(Math.floor(min - padding));
        yAxis.setUpperBound(Math.ceil(max + padding));
        yAxis.setTickUnit(Math.max(1, (yAxis.getUpperBound() - yAxis.getLowerBound()) / 8));

        xAxis.setAutoRanging(false);
        xAxis.setLowerBound(series.get(0).getSimulationDay());
        xAxis.setUpperBound(series.get(series.size() - 1).getSimulationDay());
        xAxis.setTickUnit(Math.max(1, series.size() / 8.0));

        priceChart.setTitle(stock.getSymbol() + " - last " + series.size() + " trading days ("
                + Formats.date(series.get(0).getRecordedAt()) + " to "
                + Formats.date(series.get(series.size() - 1).getRecordedAt()) + ")");
        priceChart.getData().add(chartSeries);

        LOGGER.fine("Chart drawn for " + stock.getSymbol() + ": " + series.size() + " points");
    }

    // ---------------- trading ----------------

    @FXML
    private void handleBuy() {
        if (stock == null) {
            return;
        }
        tradeDialog.showBuy(stock, currentUser().getBalance())
                   .ifPresent(txn -> refreshScreen());
    }

    @FXML
    private void handleSell() {
        if (stock == null) {
            return;
        }
        int owned = holdingDAO.getQuantity(userId(), stockId);
        tradeDialog.showSell(stock, owned, currentUser().getBalance())
                   .ifPresent(txn -> refreshScreen());
    }

    @FXML
    private void handleBack() {
        SceneManager.switchTo("market.fxml", "Market");
    }
}

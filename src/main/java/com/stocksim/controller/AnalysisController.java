package com.stocksim.controller;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.stocksim.dao.StockDAO;
import com.stocksim.model.Stock;
import com.stocksim.model.StockAnalysis;
import com.stocksim.service.AnalysisService;
import com.stocksim.service.RiskService;
import com.stocksim.util.Formats;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/** Two tabs: per-stock metrics with a comparison BarChart, and portfolio risk with a PieChart. */
public class AnalysisController extends BaseController {

    private static final Logger LOGGER = Logger.getLogger(AnalysisController.class.getName());

    @FXML private javafx.scene.control.TabPane analysisTabs;

    // --- tab 1: stock analysis ---
    @FXML private ComboBox<Stock> stockCombo;
    @FXML private Label stockPriceLabel;
    @FXML private Label dailyChangeLabel;
    @FXML private Label sevenDayLabel;
    @FXML private Label thirtyDayLabel;
    @FXML private Label volatilityLabel;
    @FXML private Label ma5Label;
    @FXML private Label ma20Label;
    @FXML private Label highLabel;
    @FXML private Label lowLabel;
    @FXML private Label trendLabel;
    @FXML private Label momentumLabel;
    @FXML private Label riskLabel;
    @FXML private Label sectorLabel;
    @FXML private BarChart<String, Number> changeChart;

    // --- tab 2: risk ---
    @FXML private VBox warningBox;
    @FXML private Label largestHoldingLabel;
    @FXML private Label largestSectorLabel;
    @FXML private Label diversificationLabel;
    @FXML private PieChart sectorPieChart;
    @FXML private BarChart<String, Number> stockBarChart;

    private final StockDAO stockDAO = new StockDAO();
    private final AnalysisService analysisService = new AnalysisService();
    private final RiskService riskService = new RiskService();

    @Override
    protected String screenName() {
        return "Analysis";
    }

    @Override
    protected void onInit() {
        configureStockCombo();
        refreshScreen();
    }

    private void configureStockCombo() {
        stockCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Stock stock) {
                return stock == null ? "" : stock.getSymbol() + "  -  " + stock.getCompanyName();
            }

            @Override
            public Stock fromString(String text) {
                return null;                 // the ComboBox is not editable
            }
        });
        stockCombo.valueProperty().addListener((obs, old, now) -> {
            if (now != null) {
                showStockAnalysis(now);
            }
        });
    }

    @Override
    protected void refreshScreen() {
        List<Stock> stocks = stockDAO.findAll();
        Stock previouslySelected = stockCombo.getValue();

        stockCombo.setItems(FXCollections.observableArrayList(stocks));
        Stock toSelect = stocks.stream()
                .filter(s -> previouslySelected != null
                        && s.getStockId() == previouslySelected.getStockId())
                .findFirst()
                .orElse(stocks.isEmpty() ? null : stocks.get(0));

        if (toSelect != null) {
            stockCombo.setValue(toSelect);
            showStockAnalysis(toSelect);     // explicit, in case the value did not change
        }

        showRiskTab();
        refreshHeader();
    }

    // ---------------- tab 1 ----------------

    private void showStockAnalysis(Stock stock) {
        StockAnalysis a = analysisService.analyse(stock.getStockId(), simulationDay());

        stockPriceLabel.setText(Formats.money(a.getCurrentPrice()));
        sectorLabel.setText(a.getSector());

        setSigned(dailyChangeLabel, a.getDailyChangePercent());
        setSigned(sevenDayLabel, a.getSevenDayChangePercent());
        setSigned(thirtyDayLabel, a.getThirtyDayChangePercent());

        volatilityLabel.setText(a.getVolatilityLabel()
                + " (" + Formats.percent(a.getVolatilityPercent()) + ")");
        ma5Label.setText(Formats.money(a.getMovingAverage5()));
        ma20Label.setText(Formats.money(a.getMovingAverage20()));
        highLabel.setText(Formats.money(a.getWeek52High()));
        lowLabel.setText(Formats.money(a.getWeek52Low()));

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

        drawChangeChart(a);
        LOGGER.fine("Analysis shown for " + a.getSymbol());
    }

    private void drawChangeChart(StockAnalysis a) {
        changeChart.getData().clear();

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName(a.getSymbol());
        series.getData().add(new XYChart.Data<>("1 Day", a.getDailyChangePercent()));
        series.getData().add(new XYChart.Data<>("7 Days", a.getSevenDayChangePercent()));
        series.getData().add(new XYChart.Data<>("30 Days", a.getThirtyDayChangePercent()));

        changeChart.getData().add(series);
        changeChart.setTitle(a.getSymbol() + " percentage change on simulation day "
                + a.getSimulationDay());

        // colour each bar by whether that period was up or down
        for (XYChart.Data<String, Number> item : series.getData()) {
            double value = item.getYValue().doubleValue();
            if (item.getNode() != null) {
                item.getNode().setStyle("-fx-bar-fill: " + (value >= 0 ? "#4caf50" : "#f44336") + ";");
            }
        }
    }

    // ---------------- tab 2 ----------------

    private void showRiskTab() {
        Map<String, Double> sectors = riskService.getSectorAllocation(userId());
        Map<String, Double> stocks = riskService.getStockAllocation(userId());

        // pie chart of sector exposure
        sectorPieChart.setData(FXCollections.observableArrayList(
                sectors.entrySet().stream()
                       .map(e -> new PieChart.Data(e.getKey() + " " + Formats.percent(e.getValue()),
                                                   e.getValue()))
                       .toList()));
        sectorPieChart.setTitle(sectors.isEmpty() ? "No holdings yet" : "Share of invested value");

        // bar chart of per-stock weight
        stockBarChart.getData().clear();
        if (!stocks.isEmpty()) {
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName("% of portfolio");
            stocks.forEach((symbol, percent) ->
                    series.getData().add(new XYChart.Data<>(symbol, percent)));
            stockBarChart.getData().add(series);

            double threshold = riskService.getStockThreshold();
            for (XYChart.Data<String, Number> item : series.getData()) {
                if (item.getNode() != null) {
                    boolean concentrated = item.getYValue().doubleValue() > threshold;
                    item.getNode().setStyle("-fx-bar-fill: "
                            + (concentrated ? "#f44336" : "#4caf50") + ";");
                }
            }
        }

        Map.Entry<String, Double> largestHolding = riskService.getLargestHolding(userId());
        largestHoldingLabel.setText(largestHolding == null ? "-"
                : largestHolding.getKey() + "  " + Formats.percent(largestHolding.getValue()));

        Map.Entry<String, Double> largestSector = riskService.getLargestSector(userId());
        largestSectorLabel.setText(largestSector == null ? "-"
                : largestSector.getKey() + "  " + Formats.percent(largestSector.getValue()));

        String level = riskService.getDiversificationLevel(userId());
        diversificationLabel.setText(level);
        diversificationLabel.getStyleClass().removeAll("positive", "negative", "neutral");
        diversificationLabel.getStyleClass().add(switch (level) {
            case "Good" -> "positive";
            case "Poor" -> "negative";
            default -> "neutral";
        });

        showWarnings();
    }

    private void showWarnings() {
        List<String> warnings = riskService.getConcentrationWarnings(userId());
        warningBox.getChildren().clear();

        boolean hasWarnings = !warnings.isEmpty();
        warningBox.setVisible(hasWarnings);
        warningBox.setManaged(hasWarnings);
        if (!hasWarnings) {
            return;
        }

        Label title = new Label("Concentration risk");
        title.getStyleClass().add("section-title");
        warningBox.getChildren().add(title);

        for (String warning : warnings) {
            Label label = new Label("- " + warning);
            label.getStyleClass().add("negative");
            label.setWrapText(true);
            warningBox.getChildren().add(label);
        }
    }

    private void setSigned(Label label, double value) {
        label.setText(Formats.signedPercent(value));
        applySignStyle(label, value);
    }
}

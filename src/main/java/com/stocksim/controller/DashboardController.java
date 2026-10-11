package com.stocksim.controller;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.stocksim.dao.StockDAO;
import com.stocksim.dao.UserDAO;
import com.stocksim.model.MarketEvent;
import com.stocksim.model.PortfolioSummary;
import com.stocksim.model.SimulationState;
import com.stocksim.model.Stock;
import com.stocksim.service.AnalysisService;
import com.stocksim.service.PortfolioService;
import com.stocksim.service.StateService;
import com.stocksim.util.Formats;
import com.stocksim.util.SceneManager;
import com.stocksim.util.TableSupport;
import com.stocksim.util.UserSession;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;

/** Main dashboard: money summary, market overview, simulation controls and news. */
public class DashboardController extends BaseController {

    private static final Logger LOGGER = Logger.getLogger(DashboardController.class.getName());

    @FXML private Label cashLabel;
    @FXML private Label portfolioValueLabel;
    @FXML private Label investedLabel;
    @FXML private Label profitLossLabel;
    @FXML private Label profitLossPercentLabel;
    @FXML private Label returnLabel;
    @FXML private Label startingCapitalLabel;

    @FXML private TableView<Stock> marketTable;
    @FXML private TableColumn<Stock, String> symbolColumn;
    @FXML private TableColumn<Stock, String> companyColumn;
    @FXML private TableColumn<Stock, String> sectorColumn;
    @FXML private TableColumn<Stock, String> priceColumn;
    @FXML private TableColumn<Stock, String> changeColumn;

    @FXML private VBox newsBox;

    private final PortfolioService portfolioService = new PortfolioService();
    private final AnalysisService analysisService = new AnalysisService();
    private final StockDAO stockDAO = new StockDAO();
    private final UserDAO userDAO = new UserDAO();
    private final StateService stateService = new StateService();

    private final ObservableList<Stock> stocks = FXCollections.observableArrayList();

    @Override
    protected String screenName() {
        return "Dashboard";
    }

    @Override
    protected void onInit() {
        configureMarketTable();
        refreshScreen();
    }

    private void configureMarketTable() {
        TableSupport.text(symbolColumn, Stock::getSymbol);
        TableSupport.text(companyColumn, Stock::getCompanyName);
        TableSupport.text(sectorColumn, Stock::getSector);
        TableSupport.money(priceColumn, Stock::getCurrentPrice);
        TableSupport.signedPercent(changeColumn, Stock::getChangePercent);

        TableSupport.constrainColumns(marketTable);
        marketTable.setItems(stocks);
        marketTable.setPlaceholder(new Label("Loading market data..."));
        marketTable.setRowFactory(table -> {
            var row = new javafx.scene.control.TableRow<Stock>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    openStockDetails(row.getItem());
                }
            });
            return row;
        });
    }

    private void openStockDetails(Stock stock) {
        SceneManager.<StockDetailsController>switchTo("stock-details.fxml", stock.getSymbol(),
                controller -> controller.showStock(stock.getStockId()));
    }

    @Override
    protected void refreshScreen() {
        reloadSessionUser();
        loadSummary();
        loadMarket();
        loadNews();
        refreshHeader();
    }

    /** Re-reads the user so cash and simulation day come from the database, not stale memory. */
    private void reloadSessionUser() {
        userDAO.findById(userId()).ifPresent(u -> UserSession.getInstance().setCurrentUser(u));
    }

    private void loadSummary() {
        PortfolioSummary summary = portfolioService.getPortfolioSummary(userId());

        cashLabel.setText(Formats.money(summary.getCash()));
        portfolioValueLabel.setText(Formats.money(summary.getTotalValue()));
        investedLabel.setText("invested " + Formats.money(summary.getInvested())
                + " in " + summary.getHoldingCount() + " stock(s)");

        profitLossLabel.setText(Formats.signedMoney(summary.getProfitLoss()));
        applySignStyle(profitLossLabel, summary.getProfitLoss());

        profitLossPercentLabel.setText(Formats.signedPercent(summary.getProfitLossPercent())
                + " on invested");

        returnLabel.setText(Formats.signedPercent(summary.getTotalReturnPercent()));
        applySignStyle(returnLabel, summary.getTotalReturnPercent());

        startingCapitalLabel.setText("from " + Formats.money(summary.getStartingCapital()));
    }

    private void loadMarket() {
        int day = simulationDay();
        List<Stock> all = stockDAO.findAll();
        for (Stock stock : all) {
            stock.setChangePercent(analysisService.getDailyChange(stock.getStockId(), day));
        }
        stocks.setAll(all);
        marketTable.setPlaceholder(new Label("No stocks found - run database.sql to seed them."));
    }

    private void loadNews() {
        newsBox.getChildren().clear();
        List<MarketEvent> events = simulationService.getRecentEvents(userId(), 4);

        if (events.isEmpty()) {
            Label none = new Label("No market news yet. Advance the simulation to see events.");
            none.getStyleClass().add("subtext");
            newsBox.getChildren().add(none);
            return;
        }

        for (MarketEvent event : events) {
            VBox card = new VBox(3);
            card.getStyleClass().add("news-card");

            Label title = new Label(event.getTitle());
            title.getStyleClass().add("section-title");
            title.setWrapText(true);

            Label body = new Label(event.getDescription());
            body.getStyleClass().add("subtext");
            body.setWrapText(true);

            Label meta = new Label("Day " + event.getSimulationDay()
                    + "  |  " + event.getSector() + "  |  Impact: " + event.getImpact());
            meta.getStyleClass().add(switch (String.valueOf(event.getImpact())) {
                case "Positive" -> "positive";
                case "Negative" -> "negative";
                default -> "neutral";
            });

            card.getChildren().addAll(title, body, meta);
            newsBox.getChildren().add(card);
        }
    }

    // ---------------- serialization (Unit 3) ----------------

    @FXML
    private void handleSaveState() {
        try {
            var file = stateService.saveState(userId());
            SceneManager.info("State Saved", "Simulation snapshot written",
                    "Saved to:\n" + file.toAbsolutePath()
                            + "\n\nDay " + simulationDay()
                            + ", cash " + Formats.money(currentUser().getBalance()));
        } catch (IOException | RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Save state failed", e);
            SceneManager.error("Save Failed", "Could not write the simulation snapshot.",
                    e.getMessage());
        }
    }

    @FXML
    private void handleLoadState() {
        if (!stateService.stateExists()) {
            SceneManager.warn("No Snapshot", "There is nothing to load.",
                    "Use SAVE STATE first to create " + stateService.getStateFile() + ".");
            return;
        }
        if (!SceneManager.confirm("Load State", "Restore the saved snapshot?",
                "Your simulation day and cash balance will be set back to the saved values.")) {
            return;
        }

        stateService.loadState(userId()).ifPresentOrElse(state -> {
            refreshScreen();
            SceneManager.info("State Loaded", "Snapshot restored",
                    state.getLoadNote() + "\n\nSaved at: " + Formats.dateTime(state.getSavedAt()));
        }, () -> SceneManager.error("Load Failed", "The snapshot could not be read.",
                "The file may be missing or was written by an older version."));
    }

    /** Exposed for the tests: proves a snapshot round-trips through this screen. */
    SimulationState readSnapshotForTesting() {
        return stateService.readState().orElse(null);
    }
}

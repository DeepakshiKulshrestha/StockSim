package com.stocksim.controller;

import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.stocksim.dao.StockDAO;
import com.stocksim.dao.UserDAO;
import com.stocksim.model.Holding;
import com.stocksim.model.PortfolioSummary;
import com.stocksim.model.Stock;
import com.stocksim.service.PortfolioService;
import com.stocksim.service.ReportService;
import com.stocksim.service.RiskService;
import com.stocksim.util.Formats;
import com.stocksim.util.SceneManager;
import com.stocksim.util.TableSupport;
import com.stocksim.util.UserSession;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;

/** Holdings table, portfolio summary and the .txt export. */
public class PortfolioController extends BaseController {

    private static final Logger LOGGER = Logger.getLogger(PortfolioController.class.getName());

    @FXML private Label totalValueLabel;
    @FXML private Label cashLabel;
    @FXML private Label investedLabel;
    @FXML private Label holdingsValueLabel;
    @FXML private Label profitLossLabel;
    @FXML private Label profitLossPercentLabel;
    @FXML private Label returnLabel;
    @FXML private Label diversificationLabel;
    @FXML private Label exportPathLabel;

    @FXML private TableView<Holding> holdingsTable;
    @FXML private TableColumn<Holding, String> stockColumn;
    @FXML private TableColumn<Holding, String> sectorColumn;
    @FXML private TableColumn<Holding, String> quantityColumn;
    @FXML private TableColumn<Holding, String> avgBuyColumn;
    @FXML private TableColumn<Holding, String> currentColumn;
    @FXML private TableColumn<Holding, String> valueColumn;
    @FXML private TableColumn<Holding, String> plColumn;
    @FXML private TableColumn<Holding, String> plPercentColumn;

    private final PortfolioService portfolioService = new PortfolioService();
    private final RiskService riskService = new RiskService();
    private final ReportService reportService = new ReportService();
    private final UserDAO userDAO = new UserDAO();
    private final StockDAO stockDAO = new StockDAO();
    private final TradeDialog tradeDialog = new TradeDialog();

    private final ObservableList<Holding> holdings = FXCollections.observableArrayList();

    @Override
    protected String screenName() {
        return "Portfolio";
    }

    @Override
    protected void onInit() {
        configureTable();
        refreshScreen();
    }

    private void configureTable() {
        TableSupport.text(stockColumn, h -> h.getSymbol() + "  -  " + h.getCompanyName());
        TableSupport.text(sectorColumn, Holding::getSector);
        TableSupport.number(quantityColumn, h -> String.valueOf(h.getQuantity()));
        TableSupport.money(avgBuyColumn, Holding::getAverageBuyPrice);
        TableSupport.money(currentColumn, Holding::getCurrentPrice);
        TableSupport.money(valueColumn, Holding::calculateValue);
        TableSupport.signedMoney(plColumn, Holding::calculateProfitLoss);
        TableSupport.signedPercent(plPercentColumn, Holding::calculateProfitLossPercent);

        TableSupport.constrainColumns(holdingsTable);
        holdingsTable.setItems(holdings);
        holdingsTable.setPlaceholder(
                new Label("You do not own any shares yet. Open the Market to make your first trade."));

        holdingsTable.setRowFactory(table -> {
            TableRow<Holding> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    SceneManager.<StockDetailsController>switchTo("stock-details.fxml",
                            row.getItem().getSymbol(),
                            controller -> controller.showStock(row.getItem().getStockId()));
                }
            });
            return row;
        });
    }

    @Override
    protected void refreshScreen() {
        userDAO.findById(userId()).ifPresent(u -> UserSession.getInstance().setCurrentUser(u));

        holdings.setAll(portfolioService.getHoldings(userId()));
        PortfolioSummary summary = portfolioService.getPortfolioSummary(userId());

        totalValueLabel.setText(Formats.money(summary.getTotalValue()));
        cashLabel.setText("cash " + Formats.money(summary.getCash()));

        investedLabel.setText(Formats.money(summary.getInvested()));
        holdingsValueLabel.setText("now worth " + Formats.money(summary.getHoldingsValue()));

        profitLossLabel.setText(Formats.signedMoney(summary.getProfitLoss()));
        applySignStyle(profitLossLabel, summary.getProfitLoss());
        profitLossPercentLabel.setText(Formats.signedPercent(summary.getProfitLossPercent())
                + " on invested");

        returnLabel.setText(Formats.signedPercent(summary.getTotalReturnPercent()));
        applySignStyle(returnLabel, summary.getTotalReturnPercent());
        diversificationLabel.setText("Diversification: "
                + riskService.getDiversificationLevel(userId()));

        refreshHeader();
    }

    @FXML
    private void handleSellSelected() {
        Holding selected = holdingsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            SceneManager.warn("No Holding Selected", "Pick a holding first.",
                    "Click a row in the table, then press SELL SELECTED.");
            return;
        }
        Stock stock = stockDAO.findById(selected.getStockId()).orElse(null);
        if (stock == null) {
            return;
        }
        tradeDialog.showSell(stock, selected.getQuantity(), currentUser().getBalance())
                   .ifPresent(txn -> refreshScreen());
    }

    /** BufferedWriter export (Unit 3). */
    @FXML
    private void handleExport() {
        try {
            Path file = reportService.exportPortfolio(currentUser());
            exportPathLabel.setText("Last export: " + file.getFileName());
            SceneManager.info("Portfolio Exported", "Report saved",
                    file.toAbsolutePath() + "\n\n"
                            + holdings.size() + " holding(s) written as plain text.");
        } catch (IOException | RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Portfolio export failed", e);
            SceneManager.error("Export Failed", "Could not write the report file.", e.getMessage());
        }
    }
}

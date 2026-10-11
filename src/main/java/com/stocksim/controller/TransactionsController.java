package com.stocksim.controller;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.stocksim.dao.TransactionDAO;
import com.stocksim.model.Transaction;
import com.stocksim.service.PortfolioService;
import com.stocksim.service.ReportService;
import com.stocksim.util.Formats;
import com.stocksim.util.SceneManager;
import com.stocksim.util.TableSupport;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;

/** Full BUY/SELL log with ALL / BUY / SELL filtering and a .txt export. */
public class TransactionsController extends BaseController {

    private static final Logger LOGGER = Logger.getLogger(TransactionsController.class.getName());

    @FXML private ToggleGroup filterGroup;
    @FXML private ToggleButton allToggle;
    @FXML private ToggleButton buyToggle;
    @FXML private ToggleButton sellToggle;

    @FXML private Label summaryLabel;
    @FXML private Label totalBoughtLabel;
    @FXML private Label totalSoldLabel;
    @FXML private Label tradeStatsLabel;
    @FXML private Label exportPathLabel;

    @FXML private TableView<Transaction> transactionsTable;
    @FXML private TableColumn<Transaction, String> dateColumn;
    @FXML private TableColumn<Transaction, String> dayColumn;
    @FXML private TableColumn<Transaction, String> stockColumn;
    @FXML private TableColumn<Transaction, String> typeColumn;
    @FXML private TableColumn<Transaction, String> quantityColumn;
    @FXML private TableColumn<Transaction, String> priceColumn;
    @FXML private TableColumn<Transaction, String> totalColumn;

    private final TransactionDAO transactionDAO = new TransactionDAO();
    private final PortfolioService portfolioService = new PortfolioService();
    private final ReportService reportService = new ReportService();

    /** Everything the user has ever traded; the table shows a filtered view of this. */
    private final List<Transaction> allTransactions = new ArrayList<>();
    private final ObservableList<Transaction> visible = FXCollections.observableArrayList();

    @Override
    protected String screenName() {
        return "Transactions";
    }

    @Override
    protected void onInit() {
        configureTable();

        allToggle.setUserData("ALL");
        buyToggle.setUserData("BUY");
        sellToggle.setUserData("SELL");
        filterGroup.selectedToggleProperty().addListener((obs, old, now) -> {
            if (now == null) {
                filterGroup.selectToggle(old);      // one filter is always active
            } else {
                applyFilter();
            }
        });

        refreshScreen();
    }

    private void configureTable() {
        TableSupport.text(dateColumn, t -> Formats.dateTime(t.getTransactionDate()));
        TableSupport.number(dayColumn, t -> String.valueOf(t.getSimulationDay()));
        TableSupport.text(stockColumn, t -> t.getSymbol() + "  -  " + t.getCompanyName());
        TableSupport.tradeType(typeColumn, Transaction::getTypeName);
        TableSupport.number(quantityColumn, t -> String.valueOf(t.getQuantity()));
        TableSupport.money(priceColumn, Transaction::getPrice);
        TableSupport.money(totalColumn, Transaction::getTotalAmount);

        TableSupport.constrainColumns(transactionsTable);
        transactionsTable.setItems(visible);
        transactionsTable.setPlaceholder(
                new Label("No transactions yet. Buy a stock from the Market to get started."));
    }

    @Override
    protected void refreshScreen() {
        allTransactions.clear();
        allTransactions.addAll(transactionDAO.findByUserId(userId()));

        applyFilter();
        showTotals();
        refreshHeader();
    }

    /** ArrayList-based filtering (Unit 2) - no extra database round trip. */
    private void applyFilter() {
        Toggle selected = filterGroup.getSelectedToggle();
        String filter = selected == null ? "ALL" : String.valueOf(selected.getUserData());

        List<Transaction> filtered = new ArrayList<>();
        for (Transaction txn : allTransactions) {
            if ("ALL".equals(filter) || filter.equals(txn.getTypeName())) {
                filtered.add(txn);
            }
        }
        visible.setAll(filtered);
        summaryLabel.setText(filtered.size() + " of " + allTransactions.size() + " transactions");
    }

    private void showTotals() {
        double bought = 0.0;
        double sold = 0.0;
        for (Transaction txn : allTransactions) {
            if (txn.isBuy()) {
                bought += txn.getTotalAmount();
            } else {
                sold += txn.getTotalAmount();
            }
        }
        totalBoughtLabel.setText(Formats.money(bought));
        totalSoldLabel.setText(Formats.money(sold));

        int[] stats = portfolioService.getProfitableAndLossMakingTrades(userId());
        tradeStatsLabel.setText(allTransactions.size() + " total  |  "
                + stats[0] + " profitable  |  " + stats[1] + " loss-making");
    }

    /** BufferedWriter export (Unit 3) - writes exactly what the filter is showing. */
    @FXML
    private void handleExport() {
        try {
            Path file = reportService.exportTransactions(currentUser(), new ArrayList<>(visible));
            exportPathLabel.setText("Last export: " + file.getFileName());
            SceneManager.info("Transactions Exported", "Report saved",
                    file.toAbsolutePath() + "\n\n"
                            + visible.size() + " transaction(s) written as plain text.");
        } catch (IOException | RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Transaction export failed", e);
            SceneManager.error("Export Failed", "Could not write the report file.", e.getMessage());
        }
    }
}

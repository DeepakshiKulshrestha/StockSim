package com.stocksim.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.stocksim.dao.StockDAO;
import com.stocksim.exception.InvalidQuantityException;
import com.stocksim.model.PriceAlert;
import com.stocksim.model.Stock;
import com.stocksim.service.AlertService;
import com.stocksim.util.Formats;
import com.stocksim.util.SceneManager;
import com.stocksim.util.TableSupport;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;

/** Set, list and delete price alerts; alerts fire when simulated time advances. */
public class AlertsController extends BaseController {

    private static final Logger LOGGER = Logger.getLogger(AlertsController.class.getName());

    @FXML private ComboBox<Stock> stockCombo;
    @FXML private Label currentPriceLabel;
    @FXML private RadioButton aboveRadio;
    @FXML private RadioButton belowRadio;
    @FXML private TextField targetPriceField;
    @FXML private Label formErrorLabel;
    @FXML private Label alertCountLabel;

    @FXML private TableView<PriceAlert> alertsTable;
    @FXML private TableColumn<PriceAlert, String> stockColumn;
    @FXML private TableColumn<PriceAlert, String> conditionColumn;
    @FXML private TableColumn<PriceAlert, String> targetColumn;
    @FXML private TableColumn<PriceAlert, String> currentColumn;
    @FXML private TableColumn<PriceAlert, String> statusColumn;

    private final AlertService alertService = new AlertService();
    private final StockDAO stockDAO = new StockDAO();

    private final ObservableList<PriceAlert> alerts = FXCollections.observableArrayList();
    /** stockId -> live price, so the table can show how far an alert is from firing. */
    private final Map<Integer, Double> priceByStockId = new HashMap<>();

    @Override
    protected String screenName() {
        return "Alerts";
    }

    @Override
    protected void onInit() {
        configureCombo();
        configureTable();
        refreshScreen();
    }

    private void configureCombo() {
        stockCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Stock stock) {
                return stock == null ? ""
                        : stock.getSymbol() + "  -  " + Formats.money(stock.getCurrentPrice());
            }

            @Override
            public Stock fromString(String text) {
                return null;
            }
        });
        stockCombo.valueProperty().addListener((obs, old, now) ->
                currentPriceLabel.setText(now == null ? ""
                        : "Trading at " + Formats.money(now.getCurrentPrice())));

        targetPriceField.textProperty().addListener((obs, old, now) -> hideError());
    }

    private void configureTable() {
        TableSupport.text(stockColumn, a -> a.getSymbol() + "  -  " + a.getCompanyName());
        TableSupport.text(conditionColumn, a -> "Goes " + a.getConditionName());
        TableSupport.money(targetColumn, PriceAlert::getTargetPrice);

        currentColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                Formats.money(priceByStockId.getOrDefault(cell.getValue().getStockId(), 0.0))));
        currentColumn.setStyle("-fx-alignment: CENTER-RIGHT;");

        statusColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().isTriggered() ? "Triggered" : "Active"));
        statusColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                getStyleClass().removeAll("positive", "neutral");
                if (empty || value == null) {
                    setText(null);
                } else {
                    setText("Triggered".equals(value) ? "Triggered" : "Active");
                    getStyleClass().add("Triggered".equals(value) ? "positive" : "neutral");
                }
            }
        });

        TableSupport.constrainColumns(alertsTable);
        alertsTable.setItems(alerts);
        alertsTable.setPlaceholder(new Label(
                "No alerts yet. Pick a stock, choose ABOVE or BELOW, and set a target price."));
    }

    @Override
    protected void refreshScreen() {
        List<Stock> stocks = stockDAO.findAll();
        priceByStockId.clear();
        for (Stock stock : stocks) {
            priceByStockId.put(stock.getStockId(), stock.getCurrentPrice());
        }

        Stock previous = stockCombo.getValue();
        stockCombo.setItems(FXCollections.observableArrayList(stocks));
        stockCombo.setValue(stocks.stream()
                .filter(s -> previous != null && s.getStockId() == previous.getStockId())
                .findFirst()
                .orElse(stocks.isEmpty() ? null : stocks.get(0)));

        alerts.setAll(alertService.getAlerts(userId()));
        long active = alerts.stream().filter(a -> !a.isTriggered()).count();
        alertCountLabel.setText(alerts.size() + " alert(s), " + active + " still active");

        alertsTable.refresh();
        refreshHeader();
    }

    @FXML
    private void handleSetAlert() {
        hideError();

        Stock stock = stockCombo.getValue();
        if (stock == null) {
            showError("Please select a stock.");
            return;
        }

        PriceAlert.Condition condition = aboveRadio.isSelected()
                ? PriceAlert.Condition.ABOVE
                : PriceAlert.Condition.BELOW;

        try {
            PriceAlert alert = alertService.createAlert(
                    userId(), stock.getStockId(), condition, targetPriceField.getText());

            targetPriceField.clear();
            refreshScreen();

            LOGGER.info("Alert set: " + alert);
            SceneManager.info("Alert Set",
                    stock.getSymbol() + " " + condition.name() + " "
                            + Formats.money(alert.getTargetPrice()),
                    "It will fire the next time you advance simulated time and the condition is met.\n"
                            + stock.getSymbol() + " is currently at "
                            + Formats.money(stock.getCurrentPrice()) + ".");

        } catch (InvalidQuantityException e) {
            showError(e.getMessage());
        } catch (RuntimeException e) {
            SceneManager.error("Could Not Set Alert", "The alert was not saved.", e.getMessage());
        }
    }

    /** Checks the alerts against today's prices without moving the simulation. */
    @FXML
    private void handleCheckNow() {
        List<PriceAlert> fired = alertService.checkAlerts(userId(), simulationDay());
        refreshScreen();

        if (fired.isEmpty()) {
            SceneManager.info("No Alerts Triggered", "Nothing fired on day " + simulationDay(),
                    "All your active alerts are still waiting for their target price.");
            return;
        }

        StringBuilder message = new StringBuilder();
        for (PriceAlert alert : fired) {
            message.append(String.format("%s went %s %s (now %s)%n",
                    alert.getSymbol(), alert.getConditionName(),
                    Formats.money(alert.getTargetPrice()),
                    Formats.money(priceByStockId.getOrDefault(alert.getStockId(), 0.0))));
        }
        SceneManager.info("Alerts Triggered", fired.size() + " alert(s) fired", message.toString());
    }

    @FXML
    private void handleDelete() {
        PriceAlert selected = alertsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            SceneManager.warn("No Alert Selected", "Pick an alert first.",
                    "Click a row in the table, then press DELETE SELECTED.");
            return;
        }
        if (!SceneManager.confirm("Delete Alert", "Delete this alert?", selected.toString())) {
            return;
        }
        if (alertService.deleteAlert(selected.getAlertId())) {
            refreshScreen();
        } else {
            SceneManager.error("Delete Failed", "The alert could not be deleted.", "Please try again.");
        }
    }

    private void showError(String message) {
        formErrorLabel.setText(message);
        formErrorLabel.setVisible(true);
        formErrorLabel.setManaged(true);
    }

    private void hideError() {
        formErrorLabel.setVisible(false);
        formErrorLabel.setManaged(false);
    }
}

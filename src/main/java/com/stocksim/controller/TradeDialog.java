package com.stocksim.controller;

import java.util.Optional;
import java.util.logging.Logger;

import com.stocksim.exception.InsufficientBalanceException;
import com.stocksim.exception.InsufficientSharesException;
import com.stocksim.exception.InvalidQuantityException;
import com.stocksim.exception.UserNotFoundException;
import com.stocksim.model.Stock;
import com.stocksim.model.Transaction;
import com.stocksim.service.TradingService;
import com.stocksim.util.Formats;
import com.stocksim.util.SceneManager;
import com.stocksim.util.UserSession;
import com.stocksim.util.Validator;

import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/**
 * The BUY and SELL dialogs. Both show the cost updating live as the user types,
 * then hand the trade to {@link TradingService} and turn any custom exception
 * into a JavaFX Alert.
 */
public final class TradeDialog {

    private static final Logger LOGGER = Logger.getLogger(TradeDialog.class.getName());

    private final TradingService tradingService = new TradingService();

    /**
     * @return the recorded transaction when the trade went through, otherwise empty
     */
    public Optional<Transaction> showBuy(Stock stock, double cashBalance) {
        int maxAffordable = (int) Math.floor(cashBalance / Math.max(0.01, stock.getCurrentPrice()));

        Dialog<Integer> dialog = buildDialog(
                "Buy " + stock.getSymbol(),
                stock.getCompanyName() + " at " + Formats.money(stock.getCurrentPrice()),
                "CONFIRM BUY",
                stock, cashBalance, 0, maxAffordable, true);

        Optional<Integer> quantity = dialog.showAndWait();
        if (quantity.isEmpty()) {
            return Optional.empty();
        }

        try {
            Transaction txn = tradingService.buyStock(
                    UserSession.getInstance().getUserId(), stock.getStockId(), quantity.get());

            SceneManager.info("Purchase Complete",
                    "Purchased " + txn.getQuantity() + " share(s) of " + stock.getSymbol(),
                    "Price     : " + Formats.money(txn.getPrice())
                            + "\nTotal cost: " + Formats.money(txn.getTotalAmount()));
            return Optional.of(txn);

        } catch (InsufficientBalanceException e) {
            SceneManager.error("Insufficient Balance", "You cannot afford this trade.", e.getMessage());
        } catch (InvalidQuantityException e) {
            SceneManager.error("Invalid Quantity", "Please check the quantity.", e.getMessage());
        } catch (UserNotFoundException e) {
            SceneManager.error("Session Error", "Your account could not be found.", e.getMessage());
        } catch (RuntimeException e) {
            LOGGER.severe("Buy failed: " + e.getMessage());
            SceneManager.error("Trade Failed", "The purchase was rolled back.", e.getMessage());
        }
        return Optional.empty();
    }

    public Optional<Transaction> showSell(Stock stock, int sharesOwned, double cashBalance) {
        if (sharesOwned <= 0) {
            SceneManager.warn("Nothing to Sell",
                    "You do not own any shares of " + stock.getSymbol() + ".",
                    "Buy some shares first, then you can sell them.");
            return Optional.empty();
        }

        Dialog<Integer> dialog = buildDialog(
                "Sell " + stock.getSymbol(),
                "You own " + sharesOwned + " share(s) at "
                        + Formats.money(stock.getCurrentPrice()) + " each",
                "CONFIRM SELL",
                stock, cashBalance, sharesOwned, sharesOwned, false);

        Optional<Integer> quantity = dialog.showAndWait();
        if (quantity.isEmpty()) {
            return Optional.empty();
        }

        try {
            Transaction txn = tradingService.sellStock(
                    UserSession.getInstance().getUserId(), stock.getStockId(), quantity.get());

            SceneManager.info("Sale Complete",
                    "Sold " + txn.getQuantity() + " share(s) of " + stock.getSymbol(),
                    "Price      : " + Formats.money(txn.getPrice())
                            + "\nSale amount: " + Formats.money(txn.getTotalAmount()));
            return Optional.of(txn);

        } catch (InsufficientSharesException e) {
            SceneManager.error("Insufficient Shares", "You do not own that many shares.", e.getMessage());
        } catch (InvalidQuantityException e) {
            SceneManager.error("Invalid Quantity", "Please check the quantity.", e.getMessage());
        } catch (UserNotFoundException e) {
            SceneManager.error("Session Error", "Your account could not be found.", e.getMessage());
        } catch (RuntimeException e) {
            LOGGER.severe("Sell failed: " + e.getMessage());
            SceneManager.error("Trade Failed", "The sale was rolled back.", e.getMessage());
        }
        return Optional.empty();
    }

    // ---------------------------------------------------------------

    /**
     * Shared dialog body. {@code isBuy} switches the wording and which limit is shown;
     * the OK button stays disabled until the typed quantity is valid.
     */
    private Dialog<Integer> buildDialog(String title, String subtitle, String confirmText,
                                        Stock stock, double cashBalance,
                                        int sharesOwned, int maxQuantity, boolean isBuy) {

        Dialog<Integer> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(subtitle);
        SceneManager.applyStylesheet(dialog);
        if (SceneManager.getPrimaryStage() != null) {
            dialog.initOwner(SceneManager.getPrimaryStage());
        }

        ButtonType confirmType = new ButtonType(confirmText, ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(confirmType, ButtonType.CANCEL);

        TextField quantityField = new TextField();
        quantityField.setPromptText("Number of shares");

        Label totalLabel = new Label("-");
        Label cashAfterLabel = new Label("-");
        Label limitLabel = new Label(isBuy
                ? "Most you can afford: " + maxQuantity + " share(s)"
                : "Shares owned: " + sharesOwned);
        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("error-label");
        limitLabel.getStyleClass().add("subtext");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(9);
        grid.setPadding(new Insets(16));
        grid.addRow(0, new Label("Current price"), new Label(Formats.money(stock.getCurrentPrice())));
        grid.addRow(1, new Label("Your cash"), new Label(Formats.money(cashBalance)));
        grid.addRow(2, new Label("Quantity"), quantityField);
        grid.addRow(3, new Label(isBuy ? "Total cost" : "Sale amount"), totalLabel);
        grid.addRow(4, new Label("Cash after"), cashAfterLabel);

        VBox content = new VBox(8, grid, limitLabel, errorLabel);
        content.setPadding(new Insets(0, 16, 12, 16));
        dialog.getDialogPane().setContent(content);

        var confirmButton = dialog.getDialogPane().lookupButton(confirmType);
        confirmButton.setDisable(true);

        // live recalculation as the user types
        quantityField.textProperty().addListener((obs, old, text) -> {
            errorLabel.setText("");

            if (!Validator.isPositiveInteger(text)) {
                totalLabel.setText("-");
                cashAfterLabel.setText("-");
                confirmButton.setDisable(true);
                if (text != null && !text.isBlank()) {
                    errorLabel.setText("Enter a whole number greater than zero.");
                }
                return;
            }

            int quantity = Validator.parseIntOrZero(text);
            double total = quantity * stock.getCurrentPrice();
            double cashAfter = isBuy ? cashBalance - total : cashBalance + total;

            totalLabel.setText(Formats.money(total));
            cashAfterLabel.setText(Formats.money(cashAfter));

            boolean withinLimit = quantity <= maxQuantity;
            confirmButton.setDisable(!withinLimit);
            if (!withinLimit) {
                errorLabel.setText(isBuy
                        ? "You can only afford " + maxQuantity + " share(s)."
                        : "You only own " + sharesOwned + " share(s).");
            }
        });

        dialog.setResultConverter(button ->
                button == confirmType ? Validator.parseIntOrZero(quantityField.getText()) : null);

        javafx.application.Platform.runLater(quantityField::requestFocus);
        return dialog;
    }
}

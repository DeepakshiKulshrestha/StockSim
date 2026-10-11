package com.stocksim.util;

import java.util.function.Function;
import java.util.function.ToDoubleFunction;

import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;

/**
 * Cell factories shared by the market, portfolio and transaction tables, so every
 * rupee amount and every green/red percentage is rendered the same way.
 */
public final class TableSupport {

    private TableSupport() {
    }

    /**
     * Makes the columns share the table's width instead of using fixed pixel widths,
     * each column keeping its prefWidth as a proportion. Without this, raising the
     * application text size pushes the right-hand columns off the edge of the table.
     *
     * Set in code rather than in FXML because FXMLLoader cannot coerce the policy
     * name in a plain {@code columnResizePolicy="..."} attribute into a Callback.
     */
    public static void constrainColumns(javafx.scene.control.TableView<?> table) {
        table.setColumnResizePolicy(
                javafx.scene.control.TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
    }

    /** Plain text column driven by a getter. */
    public static <S> void text(TableColumn<S, String> column, Function<S, String> getter) {
        column.setCellValueFactory(cell -> new SimpleStringProperty(getter.apply(cell.getValue())));
    }

    /** Right-aligned rupee amount. */
    public static <S> void money(TableColumn<S, String> column, ToDoubleFunction<S> getter) {
        column.setCellValueFactory(cell ->
                new SimpleStringProperty(Formats.money(getter.applyAsDouble(cell.getValue()))));
        column.setStyle("-fx-alignment: CENTER-RIGHT;");
    }

    /** Right-aligned whole number. */
    public static <S> void number(TableColumn<S, String> column, Function<S, String> getter) {
        text(column, getter);
        column.setStyle("-fx-alignment: CENTER-RIGHT;");
    }

    /**
     * Right-aligned signed percentage, coloured green when positive and red when negative.
     * This is the only place the colouring rule lives.
     */
    public static <S> void signedPercent(TableColumn<S, String> column, ToDoubleFunction<S> getter) {
        column.setCellValueFactory(cell ->
                new SimpleStringProperty(Formats.signedPercent(getter.applyAsDouble(cell.getValue()))));
        colourBySign(column, getter);
    }

    /** Right-aligned signed rupee amount, coloured green/red. */
    public static <S> void signedMoney(TableColumn<S, String> column, ToDoubleFunction<S> getter) {
        column.setCellValueFactory(cell ->
                new SimpleStringProperty(Formats.signedMoney(getter.applyAsDouble(cell.getValue()))));
        colourBySign(column, getter);
    }

    /** BUY in green, SELL in red. */
    public static <S> void tradeType(TableColumn<S, String> column, Function<S, String> getter) {
        text(column, getter);
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                getStyleClass().removeAll("positive", "negative");
                if (empty || value == null) {
                    setText(null);
                } else {
                    setText(value);
                    getStyleClass().add("BUY".equals(value) ? "positive" : "negative");
                }
            }
        });
        column.setStyle("-fx-alignment: CENTER;");
    }

    private static <S> void colourBySign(TableColumn<S, String> column, ToDoubleFunction<S> getter) {
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                getStyleClass().removeAll("positive", "negative", "neutral");
                if (empty || value == null || getTableRow() == null || getTableRow().getItem() == null) {
                    setText(null);
                } else {
                    setText(value);
                    @SuppressWarnings("unchecked")
                    S row = (S) getTableRow().getItem();
                    getStyleClass().add(Formats.signClass(getter.applyAsDouble(row)));
                }
            }
        });
        column.setStyle("-fx-alignment: CENTER-RIGHT;");
    }
}

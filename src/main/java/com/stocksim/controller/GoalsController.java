package com.stocksim.controller;

import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import com.stocksim.dao.GoalDAO;
import com.stocksim.model.Goal;
import com.stocksim.service.PortfolioService;
import com.stocksim.util.Formats;
import com.stocksim.util.SceneManager;
import com.stocksim.util.Validator;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Investment goals, each shown as a card with a JavaFX ProgressBar. */
public class GoalsController extends BaseController {

    private static final Logger LOGGER = Logger.getLogger(GoalsController.class.getName());

    @FXML private Label portfolioValueLabel;
    @FXML private Label goalCountLabel;
    @FXML private VBox goalsBox;

    private final GoalDAO goalDAO = new GoalDAO();
    private final PortfolioService portfolioService = new PortfolioService();

    @Override
    protected String screenName() {
        return "Goals";
    }

    @Override
    protected void onInit() {
        refreshScreen();
    }

    @Override
    protected void refreshScreen() {
        double portfolioValue = portfolioService.getPortfolioValue(userId());
        portfolioValueLabel.setText(Formats.money(portfolioValue));

        List<Goal> goals = goalDAO.findByUserId(userId());
        goalCountLabel.setText(goals.size() + " goal(s)");

        goalsBox.getChildren().clear();
        if (goals.isEmpty()) {
            Label empty = new Label(
                    "No goals yet. Press + ADD GOAL to set a target such as \"Reach 1,50,000\".");
            empty.getStyleClass().add("subtext");
            goalsBox.getChildren().add(empty);
        } else {
            for (Goal goal : goals) {
                goalsBox.getChildren().add(buildGoalCard(goal, portfolioValue));
            }
        }
        refreshHeader();
    }

    /** One goal card: name, amounts, ProgressBar, percentage and a delete button. */
    private VBox buildGoalCard(Goal goal, double portfolioValue) {
        VBox card = new VBox(7);
        card.getStyleClass().add("card");

        boolean achieved = goal.isAchieved(portfolioValue);

        Label name = new Label(goal.getGoalName() + (achieved ? "   [ACHIEVED]" : ""));
        name.getStyleClass().addAll("card-value", achieved ? "positive" : "neutral");

        Label amounts = new Label(Formats.money(portfolioValue)
                + "  of  " + Formats.money(goal.getTargetAmount()));
        amounts.getStyleClass().add("subtext");

        ProgressBar progress = new ProgressBar(goal.getProgress(portfolioValue));
        progress.setPrefHeight(16);
        progress.setMaxWidth(Double.MAX_VALUE);

        double percent = goal.getProgressPercent(portfolioValue);
        Label percentLabel = new Label(Formats.percent(percent) + " complete");
        percentLabel.getStyleClass().add(achieved ? "positive" : "subtext");

        double remaining = goal.getTargetAmount() - portfolioValue;
        Label remainingLabel = new Label(achieved
                ? "Target reached - well done."
                : Formats.money(remaining) + " still to go");
        remainingLabel.getStyleClass().add("subtext");

        Button delete = new Button("DELETE");
        delete.getStyleClass().add("sell-button");
        delete.setOnAction(e -> handleDelete(goal));

        HBox footer = new HBox(10, percentLabel, new Region(), remainingLabel);
        HBox.setHgrow(footer.getChildren().get(1), javafx.scene.layout.Priority.ALWAYS);
        footer.setAlignment(Pos.CENTER_LEFT);

        HBox header = new HBox(10, name, new Region(), delete);
        HBox.setHgrow(header.getChildren().get(1), javafx.scene.layout.Priority.ALWAYS);
        header.setAlignment(Pos.CENTER_LEFT);

        card.getChildren().addAll(header, amounts, progress, footer, remainingLabel);
        return card;
    }

    @FXML
    private void handleAddGoal() {
        Optional<Goal> created = showGoalDialog();
        created.ifPresent(goal -> {
            if (goalDAO.save(goal)) {
                LOGGER.info("Goal created: " + goal);
                refreshScreen();
                SceneManager.info("Goal Added", goal.getGoalName(),
                        "Target: " + Formats.money(goal.getTargetAmount())
                                + "\nProgress updates automatically as your portfolio changes.");
            } else {
                SceneManager.error("Could Not Save", "The goal was not saved.",
                        "Please try again.");
            }
        });
    }

    private void handleDelete(Goal goal) {
        if (!SceneManager.confirm("Delete Goal", "Delete \"" + goal.getGoalName() + "\"?",
                "This only removes the goal. Your portfolio is untouched.")) {
            return;
        }
        if (goalDAO.delete(goal.getGoalId())) {
            refreshScreen();
        } else {
            SceneManager.error("Delete Failed", "The goal could not be deleted.", "Please try again.");
        }
    }

    private Optional<Goal> showGoalDialog() {
        Dialog<Goal> dialog = new Dialog<>();
        dialog.setTitle("Add Investment Goal");
        dialog.setHeaderText("Set a portfolio value to aim for");
        SceneManager.applyStylesheet(dialog);
        if (SceneManager.getPrimaryStage() != null) {
            dialog.initOwner(SceneManager.getPrimaryStage());
        }

        ButtonType saveType = new ButtonType("SAVE", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        TextField nameField = new TextField();
        nameField.setPromptText("e.g. Reach 1,50,000");
        TextField amountField = new TextField();
        amountField.setPromptText("e.g. 150000");

        Label error = new Label();
        error.getStyleClass().add("error-label");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(9);
        grid.setPadding(new Insets(16));
        grid.addRow(0, new Label("Goal name"), nameField);
        grid.addRow(1, new Label("Target amount"), amountField);
        grid.add(error, 1, 2);
        dialog.getDialogPane().setContent(grid);

        var saveButton = dialog.getDialogPane().lookupButton(saveType);
        saveButton.setDisable(true);

        Runnable validate = () -> {
            boolean nameOk = Validator.isNotEmpty(nameField.getText());
            boolean amountOk = Validator.isPositiveDouble(amountField.getText());
            saveButton.setDisable(!(nameOk && amountOk));

            if (!amountOk && Validator.isNotEmpty(amountField.getText())) {
                error.setText("Target must be a number greater than zero.");
            } else if (!nameOk && Validator.isNotEmpty(amountField.getText())) {
                error.setText("Please name the goal.");
            } else {
                error.setText("");
            }
        };
        nameField.textProperty().addListener((obs, old, now) -> validate.run());
        amountField.textProperty().addListener((obs, old, now) -> validate.run());

        dialog.setResultConverter(button -> button == saveType
                ? new Goal(userId(), nameField.getText().trim(),
                           Validator.parseDoubleOrZero(amountField.getText()))
                : null);

        javafx.application.Platform.runLater(nameField::requestFocus);
        return dialog.showAndWait();
    }
}

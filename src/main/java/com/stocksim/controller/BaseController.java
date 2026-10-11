package com.stocksim.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.stocksim.model.MarketEvent;
import com.stocksim.model.PriceAlert;
import com.stocksim.model.User;
import com.stocksim.service.SimulationService;
import com.stocksim.util.Formats;
import com.stocksim.util.SceneManager;
import com.stocksim.util.UserSession;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.fxml.FXML;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Shared behaviour for every screen behind the login: it builds the left navigation
 * sidebar, keeps the "Simulation Day" header in step, and owns the simulation
 * controls so NEXT DAY / WEEK / MONTH behave identically wherever they appear.
 *
 * Subclasses implement {@link #onInit()} instead of {@code initialize()}.
 */
public abstract class BaseController {

    private static final Logger LOGGER = Logger.getLogger(BaseController.class.getName());

    /** label -> fxml file, in the order they appear in the sidebar. */
    private static final Map<String, String> NAV_ITEMS = new LinkedHashMap<>();
    static {
        NAV_ITEMS.put("Dashboard",    "dashboard.fxml");
        NAV_ITEMS.put("Market",       "market.fxml");
        NAV_ITEMS.put("Portfolio",    "portfolio.fxml");
        NAV_ITEMS.put("Analysis",     "analysis.fxml");
        NAV_ITEMS.put("Performance",  "performance.fxml");
        NAV_ITEMS.put("Goals",        "goals.fxml");
        NAV_ITEMS.put("Alerts",       "alerts.fxml");
        NAV_ITEMS.put("Transactions", "transactions.fxml");
    }

    @FXML protected VBox sidebar;
    @FXML protected Label simDayLabel;
    @FXML protected Label sessionUserLabel;

    protected final SimulationService simulationService = new SimulationService();

    /** Every screen behind the login needs a user; the sidebar is built around it. */
    protected User currentUser() {
        return UserSession.getInstance().getCurrentUser();
    }

    protected int userId() {
        return UserSession.getInstance().getUserId();
    }

    protected int simulationDay() {
        return UserSession.getInstance().getSimulationDay();
    }

    /** Called by FXMLLoader. Subclasses override {@link #onInit()}, not this. */
    @FXML
    public final void initialize() {
        if (currentUser() == null) {
            LOGGER.warning("No user in session - returning to the welcome screen.");
            SceneManager.switchTo("welcome.fxml", "Welcome");
            return;
        }
        buildSidebar();
        refreshHeader();
        onInit();
    }

    /** Screen-specific setup. */
    protected abstract void onInit();

    /** Name of this screen, matching a key of {@link #NAV_ITEMS}, so it can be highlighted. */
    protected abstract String screenName();

    /** Re-reads data after simulated time moves. */
    protected abstract void refreshScreen();

    // ---------------- sidebar ----------------

    private void buildSidebar() {
        if (sidebar == null) {
            return;                       // welcome/login/register have no sidebar
        }
        sidebar.getChildren().clear();
        sidebar.setSpacing(4);

        Label header = new Label("StockSim");
        header.getStyleClass().add("sidebar-header");
        VBox.setMargin(header, new javafx.geometry.Insets(4, 6, 18, 10));
        sidebar.getChildren().add(header);

        for (Map.Entry<String, String> entry : NAV_ITEMS.entrySet()) {
            Button button = new Button(entry.getKey());
            button.getStyleClass().add("nav-button");
            button.setMaxWidth(Double.MAX_VALUE);
            if (entry.getKey().equals(screenName())) {
                button.getStyleClass().add("active");
            }
            button.setOnAction(e -> SceneManager.switchTo(entry.getValue(), entry.getKey()));
            sidebar.getChildren().add(button);
        }

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        sidebar.getChildren().add(spacer);

        Label who = new Label(currentUser().getName());
        who.getStyleClass().add("subtext");
        Label handle = new Label("@" + currentUser().getUsername());
        handle.getStyleClass().add("subtext");
        VBox.setMargin(who, new javafx.geometry.Insets(0, 6, 0, 10));
        VBox.setMargin(handle, new javafx.geometry.Insets(0, 6, 10, 10));

        Button logout = new Button("Logout");
        logout.getStyleClass().add("nav-button");
        logout.setMaxWidth(Double.MAX_VALUE);
        logout.setOnAction(e -> handleLogout());

        sidebar.getChildren().addAll(who, handle, logout);
        sidebar.setAlignment(Pos.TOP_LEFT);
    }

    protected void refreshHeader() {
        if (simDayLabel != null) {
            simDayLabel.setText("Simulation Day: " + simulationDay()
                    + "  /  " + simulationService.getLastAvailableDay());
        }
        if (sessionUserLabel != null) {
            sessionUserLabel.setText(currentUser().getName()
                    + "   |   Cash: " + Formats.money(currentUser().getBalance()));
        }
    }

    protected void handleLogout() {
        if (SceneManager.confirm("Logout", "Sign out of StockSim?",
                "Your portfolio and simulation day are saved in the database.")) {
            LOGGER.info("Logout: " + UserSession.getInstance().getUsername());
            UserSession.getInstance().logout();
            SceneManager.switchTo("welcome.fxml", "Welcome");
        }
    }

    // ---------------- simulation controls ----------------

    @FXML protected void handleNextDay()   { advance(1, "day"); }
    @FXML protected void handleNextWeek()  { advance(7, "week"); }
    @FXML protected void handleNextMonth() { advance(30, "month"); }

    /**
     * Advances simulated time, refreshes the in-memory session, then lets the screen
     * redraw and reports any market events and triggered alerts.
     */
    protected void advance(int days, String unitName) {
        SimulationService.AdvanceResult result = simulationService.advance(userId(), days);

        if (result.getDaysAdvanced() == 0) {
            SceneManager.info("End of data", "The simulation is already on the last day.",
                    "Historical prices run to simulation day "
                            + simulationService.getLastAvailableDay() + ".");
            return;
        }

        UserSession.getInstance().setSimulationDay(result.getNewDay());
        refreshHeader();
        refreshScreen();

        announce(result, unitName);
    }

    private void announce(SimulationService.AdvanceResult result, String unitName) {
        StringBuilder message = new StringBuilder();
        message.append("Advanced one ").append(unitName)
               .append(": day ").append(result.getPreviousDay())
               .append(" to day ").append(result.getNewDay()).append(".\n");

        List<MarketEvent> events = result.getEvents();
        if (!events.isEmpty()) {
            message.append("\nMarket news:\n");
            for (MarketEvent event : events) {
                message.append("  - [Day ").append(event.getSimulationDay()).append("] ")
                       .append(event.getTitle())
                       .append(" (").append(event.getImpact()).append(")\n");
            }
        }

        List<PriceAlert> alerts = result.getTriggeredAlerts();
        if (!alerts.isEmpty()) {
            message.append("\nPrice alerts triggered:\n");
            for (PriceAlert alert : alerts) {
                message.append(String.format("  - %s went %s %s%n",
                        alert.getSymbol(), alert.getConditionName(),
                        Formats.money(alert.getTargetPrice())));
            }
        }

        if (result.isReachedEnd()) {
            message.append("\nThis is the last day with historical price data.");
        }

        String header = alerts.isEmpty()
                ? "Simulation advanced"
                : alerts.size() + " price alert(s) triggered";
        SceneManager.info("Market Update", header, message.toString());
    }

    /** Helper for screens that colour a label green/red by the sign of a number. */
    protected static void applySignStyle(Label label, double value) {
        label.getStyleClass().removeAll("positive", "negative", "neutral");
        label.getStyleClass().add(Formats.signClass(value));
    }
}

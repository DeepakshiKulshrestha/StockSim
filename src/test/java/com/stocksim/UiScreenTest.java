package com.stocksim;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.stocksim.controller.AlertsController;
import com.stocksim.controller.AnalysisController;
import com.stocksim.controller.DashboardController;
import com.stocksim.controller.GoalsController;
import com.stocksim.controller.LoginController;
import com.stocksim.controller.MarketController;
import com.stocksim.controller.PerformanceController;
import com.stocksim.controller.PortfolioController;
import com.stocksim.controller.RegisterController;
import com.stocksim.controller.StockDetailsController;
import com.stocksim.controller.TransactionsController;
import com.stocksim.controller.WelcomeController;
import com.stocksim.dao.GoalDAO;
import com.stocksim.model.Goal;
import com.stocksim.model.PriceAlert;
import com.stocksim.model.Stock;
import com.stocksim.model.User;
import com.stocksim.service.AlertService;
import com.stocksim.service.TradingService;
import com.stocksim.util.SceneManager;
import com.stocksim.util.UserSession;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.Chart;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.stage.Stage;

/**
 * Loads every FXML screen against a real JavaFX toolkit.
 *
 * This is the only way to catch the mistakes the compiler cannot see: a misspelled
 * fx:id, an onAction pointing at a method that does not exist, a missing import in
 * the FXML, or a controller that throws while wiring itself up.
 */
@DisplayName("JavaFX screens load and wire up")
class UiScreenTest {

    private static boolean toolkitStarted;
    private static User user;

    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyRunning) {
            latch.countDown();
        }
        assertTrue(latch.await(30, TimeUnit.SECONDS), "the JavaFX toolkit must start");
        Platform.setImplicitExit(false);
        toolkitStarted = true;

        TestSupport.resetMarketToDayOne();
        user = TestSupport.createTestUser();
        UserSession.getInstance().setCurrentUser(user);

        // give the test user some history so every screen has real data to render
        TradingService trading = new TradingService();
        trading.buyStock(user.getUserId(), TestSupport.stock("TCS").getStockId(), 20);
        trading.buyStock(user.getUserId(), TestSupport.stock("HDFCBANK").getStockId(), 30);
        trading.buyStock(user.getUserId(), TestSupport.stock("ITC").getStockId(), 40);
        trading.sellStock(user.getUserId(), TestSupport.stock("ITC").getStockId(), 10);

        // move the simulation on, so the charts have real history and the P/L is non-zero
        new com.stocksim.service.SimulationService().advance(user.getUserId(), 200);
        user = new com.stocksim.dao.UserDAO().findById(user.getUserId()).orElseThrow();
        UserSession.getInstance().setCurrentUser(user);

        new GoalDAO().save(new Goal(user.getUserId(), "Reach 1,50,000", 150000.0));
        new AlertService().createAlert(user.getUserId(),
                TestSupport.stock("TCS").getStockId(), PriceAlert.Condition.ABOVE, "99999");

        // a real (never shown) stage, so dialogs and navigation have an owner
        runOnFxThread(() -> SceneManager.setPrimaryStage(new Stage()));
    }

    @AfterAll
    static void cleanUp() {
        UserSession.getInstance().logout();
        TestSupport.deleteTestUser(user);
        TestSupport.resetMarketToDayOne();
    }

    // ---------------- every screen loads ----------------

    @Test
    void welcomeScreenLoads() {
        assertInstanceOf(WelcomeController.class, loadScreen("welcome.fxml").controller);
    }

    @Test
    void loginScreenLoads() {
        Loaded loaded = loadScreen("login.fxml");
        assertInstanceOf(LoginController.class, loaded.controller);
        assertNotNull(find(loaded, "identifierField"));
        assertNotNull(find(loaded, "passwordField"));
        assertNotNull(find(loaded, "errorLabel"));
    }

    @Test
    void registerScreenLoadsWithEveryField() {
        Loaded loaded = loadScreen("register.fxml");
        assertInstanceOf(RegisterController.class, loaded.controller);
        for (String id : List.of("nameField", "usernameField", "emailField",
                                 "passwordField", "confirmPasswordField",
                                 "nameError", "usernameError", "emailError",
                                 "passwordError", "confirmError")) {
            assertNotNull(find(loaded, id), id + " is missing from register.fxml");
        }
    }

    @Test
    void dashboardLoadsAndShowsRealNumbers() {
        Loaded loaded = loadScreen("dashboard.fxml");
        assertInstanceOf(DashboardController.class, loaded.controller);

        Label cash = (Label) find(loaded, "cashLabel");
        assertNotNull(cash);
        assertNotEquals("-", cash.getText(), "the cash card must be populated on load");
        assertTrue(cash.getText().contains("₹"), "amounts are shown in rupees");

        TableView<?> market = (TableView<?>) find(loaded, "marketTable");
        assertNotNull(market);
        assertEquals(10, market.getItems().size(), "all 10 stocks appear in the overview");

        assertNotNull(find(loaded, "newsBox"));
        assertSidebarBuilt(loaded);
    }

    @Test
    void marketScreenLoadsAndFilters() {
        Loaded loaded = loadScreen("market.fxml");
        assertInstanceOf(MarketController.class, loaded.controller);

        TableView<?> table = (TableView<?>) find(loaded, "marketTable");
        assertEquals(10, table.getItems().size());

        // typing in the search box must filter live, with no database round trip
        javafx.scene.control.TextField search =
                (javafx.scene.control.TextField) find(loaded, "searchField");
        runOnFxThread(() -> search.setText("tata"));
        assertEquals(2, table.getItems().size(),
                "TCS (Tata Consultancy) and TATAMOTORS both match 'tata'");

        runOnFxThread(() -> search.setText("zzzz"));
        assertEquals(0, table.getItems().size());

        runOnFxThread(() -> search.setText(""));
        assertEquals(10, table.getItems().size());

        assertSidebarBuilt(loaded);
    }

    @Test
    void stockDetailsLoadsAndDrawsTheHistoricalChart() {
        Stock tcs = TestSupport.stock("TCS");

        AtomicReference<Loaded> ref = new AtomicReference<>();
        runOnFxThread(() -> {
            FXMLLoader loader = SceneManager.loader("stock-details.fxml");
            try {
                Parent root = loader.load();
                StockDetailsController controller = loader.getController();
                controller.showStock(tcs.getStockId());
                new Scene(root);
                root.applyCss();
                root.layout();
                ref.set(new Loaded(root, controller));
            } catch (Exception e) {
                throw new IllegalStateException("stock-details.fxml failed to load", e);
            }
        });

        Loaded loaded = ref.get();
        Label company = (Label) find(loaded, "companyLabel");
        assertEquals("Tata Consultancy Services", company.getText());

        Label price = (Label) find(loaded, "priceLabel");
        assertTrue(price.getText().contains("₹"));

        Chart chart = (Chart) find(loaded, "priceChart");
        javafx.scene.chart.LineChart<?, ?> lineChart = (javafx.scene.chart.LineChart<?, ?>) chart;
        assertEquals(1, lineChart.getData().size(), "one price series must be plotted");
        assertEquals(30, lineChart.getData().get(0).getData().size(),
                "the 1M default period shows 30 trading days");

        // metrics must all be filled in, not left as placeholders
        for (String id : List.of("highLabel", "lowLabel", "volatilityLabel", "sevenDayLabel",
                                 "thirtyDayLabel", "movingAverageLabel", "trendLabel",
                                 "momentumLabel", "riskLabel")) {
            Label label = (Label) find(loaded, id);
            assertNotNull(label, id + " is missing");
            assertNotEquals("-", label.getText(), id + " was never populated");
        }
    }

    @Test
    void portfolioScreenShowsTheHoldings() {
        Loaded loaded = loadScreen("portfolio.fxml");
        assertInstanceOf(PortfolioController.class, loaded.controller);

        TableView<?> table = (TableView<?>) find(loaded, "holdingsTable");
        assertEquals(3, table.getItems().size(), "TCS, HDFCBANK and the remaining ITC shares");

        Label total = (Label) find(loaded, "totalValueLabel");
        assertTrue(total.getText().contains("₹"));
        assertNotEquals("-", ((Label) find(loaded, "investedLabel")).getText());
        assertSidebarBuilt(loaded);
    }

    @Test
    void analysisScreenLoadsBothTabsWithCharts() {
        Loaded loaded = loadScreen("analysis.fxml");
        assertInstanceOf(AnalysisController.class, loaded.controller);

        javafx.scene.chart.BarChart<?, ?> changeChart =
                (javafx.scene.chart.BarChart<?, ?>) find(loaded, "changeChart");
        assertEquals(1, changeChart.getData().size());
        assertEquals(3, changeChart.getData().get(0).getData().size(), "1D / 7D / 30D bars");

        javafx.scene.chart.PieChart pie =
                (javafx.scene.chart.PieChart) find(loaded, "sectorPieChart");
        assertEquals(3, pie.getData().size(), "IT, Banking and FMCG are all held");

        javafx.scene.chart.BarChart<?, ?> stockChart =
                (javafx.scene.chart.BarChart<?, ?>) find(loaded, "stockBarChart");
        assertEquals(1, stockChart.getData().size());
        assertEquals(3, stockChart.getData().get(0).getData().size());

        assertNotEquals("-", ((Label) find(loaded, "diversificationLabel")).getText());
        assertNotEquals("-", ((Label) find(loaded, "largestHoldingLabel")).getText());
    }

    @Test
    void performanceScreenBuildsItsThreeCharts() {
        Loaded loaded = loadScreen("performance.fxml");
        assertInstanceOf(PerformanceController.class, loaded.controller);

        javafx.scene.chart.LineChart<?, ?> valueChart =
                (javafx.scene.chart.LineChart<?, ?>) find(loaded, "valueChart");
        assertEquals(1, valueChart.getData().size());
        assertFalse(valueChart.getData().get(0).getData().isEmpty());

        assertFalse(((javafx.scene.chart.PieChart) find(loaded, "sectorPieChart"))
                .getData().isEmpty());
        assertEquals(1, ((javafx.scene.chart.BarChart<?, ?>) find(loaded, "plChart"))
                .getData().size());

        assertNotEquals("-", ((Label) find(loaded, "totalReturnLabel")).getText());
        assertEquals("4", ((Label) find(loaded, "totalTradesLabel")).getText(),
                "three buys and one sell were made in setup");
        assertNotEquals("-", ((Label) find(loaded, "bestLabel")).getText());
    }

    @Test
    void goalsScreenRendersAProgressBarPerGoal() {
        Loaded loaded = loadScreen("goals.fxml");
        assertInstanceOf(GoalsController.class, loaded.controller);

        javafx.scene.layout.VBox goalsBox =
                (javafx.scene.layout.VBox) find(loaded, "goalsBox");
        assertEquals(1, goalsBox.getChildren().size(), "one goal was created in setup");

        long progressBars = goalsBox.lookupAll(".progress-bar").size();
        assertEquals(1, progressBars, "each goal card carries a ProgressBar");

        Label value = (Label) find(loaded, "portfolioValueLabel");
        assertTrue(value.getText().contains("₹"));
    }

    @Test
    void alertsScreenListsTheAlert() {
        Loaded loaded = loadScreen("alerts.fxml");
        assertInstanceOf(AlertsController.class, loaded.controller);

        TableView<?> table = (TableView<?>) find(loaded, "alertsTable");
        assertEquals(1, table.getItems().size());

        assertNotNull(find(loaded, "aboveRadio"));
        assertNotNull(find(loaded, "belowRadio"));
        assertNotNull(find(loaded, "targetPriceField"));
        assertTrue(((Label) find(loaded, "alertCountLabel")).getText().contains("1 alert"));
    }

    @Test
    void transactionsScreenFiltersByType() {
        Loaded loaded = loadScreen("transactions.fxml");
        assertInstanceOf(TransactionsController.class, loaded.controller);

        TableView<?> table = (TableView<?>) find(loaded, "transactionsTable");
        assertEquals(4, table.getItems().size(), "three buys plus one sell");

        javafx.scene.control.ToggleButton buyToggle =
                (javafx.scene.control.ToggleButton) find(loaded, "buyToggle");
        runOnFxThread(buyToggle::fire);
        assertEquals(3, table.getItems().size(), "BUY filter shows only the three purchases");

        javafx.scene.control.ToggleButton sellToggle =
                (javafx.scene.control.ToggleButton) find(loaded, "sellToggle");
        runOnFxThread(sellToggle::fire);
        assertEquals(1, table.getItems().size());

        javafx.scene.control.ToggleButton allToggle =
                (javafx.scene.control.ToggleButton) find(loaded, "allToggle");
        runOnFxThread(allToggle::fire);
        assertEquals(4, table.getItems().size());

        assertNotEquals("-", ((Label) find(loaded, "totalBoughtLabel")).getText());
    }

    @Test
    void theStylesheetIsOnTheClasspath() {
        assertNotNull(SceneManager.class.getResource("/styles/main.css"),
                "main.css must ship in the jar for the dark theme to apply");
    }

    // ---------------- helpers ----------------

    private record Loaded(Parent root, Object controller) {
    }

    /** Loads one FXML on the JavaFX thread and puts it in a Scene, as the app would. */
    private Loaded loadScreen(String fxmlName) {
        assertTrue(toolkitStarted);
        AtomicReference<Loaded> ref = new AtomicReference<>();
        runOnFxThread(() -> {
            FXMLLoader loader = SceneManager.loader(fxmlName);
            try {
                Parent root = loader.load();
                new Scene(root);
                root.applyCss();
                root.layout();          // builds control skins, so CSS errors surface here
                ref.set(new Loaded(root, loader.getController()));
            } catch (Exception e) {
                throw new IllegalStateException(fxmlName + " failed to load", e);
            }
        });
        assertNotNull(ref.get(), fxmlName + " produced no root node");
        return ref.get();
    }

    /**
     * Returns the node the FXMLLoader injected into the controller field called {@code fxId}.
     *
     * Reading the field rather than calling {@code root.lookup("#id")} is deliberate: it
     * asserts the thing that actually matters (the fx:id matched a controller field) and
     * it still works for nodes a ScrollPane or an unselected Tab has not realised yet.
     */
    private static Node find(Loaded loaded, String fxId) {
        Class<?> type = loaded.controller().getClass();
        while (type != null && type != Object.class) {
            try {
                java.lang.reflect.Field field = type.getDeclaredField(fxId);
                field.setAccessible(true);
                return (Node) field.get(loaded.controller());
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new AssertionError("Could not read field " + fxId, e);
            }
        }
        return null;
    }

    /** The sidebar is built in code, so an empty one means BaseController never ran. */
    private static void assertSidebarBuilt(Loaded loaded) {
        javafx.scene.layout.VBox sidebar = (javafx.scene.layout.VBox) find(loaded, "sidebar");
        assertNotNull(sidebar, "every screen behind the login has a sidebar");
        assertTrue(sidebar.getChildren().size() >= 10,
                "sidebar should hold a header, 8 nav buttons and the logout block");
        assertEquals(1, sidebar.lookupAll(".nav-button.active").size(),
                "exactly one nav button is highlighted as the current screen");
    }

    /** Runs the action on the FX thread and rethrows anything it threw. */
    private static void runOnFxThread(Runnable action) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                latch.countDown();
            }
        });
        try {
            assertTrue(latch.await(30, TimeUnit.SECONDS), "FX thread action timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("interrupted while waiting for the FX thread");
        }
        if (failure.get() != null) {
            throw new AssertionError("FX thread action failed: " + failure.get(), failure.get());
        }
    }
}

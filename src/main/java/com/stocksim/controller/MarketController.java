package com.stocksim.controller;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import com.stocksim.dao.HoldingDAO;
import com.stocksim.dao.StockDAO;
import com.stocksim.model.Holding;
import com.stocksim.model.Stock;
import com.stocksim.service.AnalysisService;
import com.stocksim.util.DataCache;
import com.stocksim.util.SceneManager;
import com.stocksim.util.TableSupport;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

/**
 * Market browser: live search, sector filter and sorting over the 10 stocks.
 *
 * Demonstrates Unit 2 collections: a {@link DataCache} of stocks so filtering never
 * hits the database, a {@link HashSet} of unique sectors, and {@link Comparator}
 * lambdas for the sort options.
 */
public class MarketController extends BaseController {

    private static final Logger LOGGER = Logger.getLogger(MarketController.class.getName());

    private static final String ALL_SECTORS = "All Sectors";

    @FXML private TextField searchField;
    @FXML private ComboBox<String> sectorCombo;
    @FXML private ComboBox<String> sortCombo;
    @FXML private Label resultCountLabel;

    @FXML private TableView<Stock> marketTable;
    @FXML private TableColumn<Stock, String> companyColumn;
    @FXML private TableColumn<Stock, String> symbolColumn;
    @FXML private TableColumn<Stock, String> sectorColumn;
    @FXML private TableColumn<Stock, String> priceColumn;
    @FXML private TableColumn<Stock, String> changeColumn;
    @FXML private TableColumn<Stock, String> ownedColumn;

    private final StockDAO stockDAO = new StockDAO();
    private final HoldingDAO holdingDAO = new HoldingDAO();
    private final AnalysisService analysisService = new AnalysisService();

    /** Generic cache (Unit 2) - the stock list is loaded once per visit to this screen. */
    private final DataCache<Stock> stockCache = new DataCache<>();

    private final ObservableList<Stock> allStocks = FXCollections.observableArrayList();
    private FilteredList<Stock> filteredStocks;

    /** symbol -> shares owned, so the table can show a "You Own" column. */
    private final java.util.Map<String, Integer> ownedBySymbol = new java.util.HashMap<>();

    @Override
    protected String screenName() {
        return "Market";
    }

    @Override
    protected void onInit() {
        configureTable();
        configureFilters();
        refreshScreen();
    }

    private void configureTable() {
        TableSupport.text(companyColumn, Stock::getCompanyName);
        TableSupport.text(symbolColumn, Stock::getSymbol);
        TableSupport.text(sectorColumn, Stock::getSector);
        TableSupport.money(priceColumn, Stock::getCurrentPrice);
        TableSupport.signedPercent(changeColumn, Stock::getChangePercent);

        ownedColumn.setCellValueFactory(cell -> {
            int quantity = ownedBySymbol.getOrDefault(cell.getValue().getSymbol(), 0);
            return new SimpleStringProperty(quantity == 0 ? "-" : String.valueOf(quantity));
        });
        ownedColumn.setStyle("-fx-alignment: CENTER-RIGHT;");

        filteredStocks = new FilteredList<>(allStocks, stock -> true);
        TableSupport.constrainColumns(marketTable);
        marketTable.setItems(filteredStocks);
        marketTable.setPlaceholder(new Label("No stocks match your search."));

        marketTable.setRowFactory(table -> {
            TableRow<Stock> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    openStock(row.getItem());
                }
            });
            return row;
        });
    }

    private void configureFilters() {
        // HashSet (Unit 2): collect the distinct sectors present in the data
        Set<String> sectors = new HashSet<>(stockDAO.findAllSectors());
        List<String> options = new java.util.ArrayList<>(sectors);
        options.sort(Comparator.naturalOrder());
        options.add(0, ALL_SECTORS);

        sectorCombo.setItems(FXCollections.observableArrayList(options));
        sectorCombo.getSelectionModel().selectFirst();
        sectorCombo.valueProperty().addListener((obs, old, now) -> applyFilter());

        sortCombo.setItems(FXCollections.observableArrayList(
                "Symbol (A-Z)", "Company (A-Z)", "Price (high to low)",
                "Price (low to high)", "Change % (best first)", "Change % (worst first)"));
        sortCombo.getSelectionModel().selectFirst();
        sortCombo.valueProperty().addListener((obs, old, now) -> applySort());

        // live filtering as the user types
        searchField.textProperty().addListener((obs, old, now) -> applyFilter());
    }

    @Override
    protected void refreshScreen() {
        loadStocks();
        applyFilter();
        applySort();
        refreshHeader();
    }

    private void loadStocks() {
        int day = simulationDay();

        stockCache.clear();
        for (Stock stock : stockDAO.findAll()) {
            stock.setChangePercent(analysisService.getDailyChange(stock.getStockId(), day));
            stockCache.put(stock.getSymbol(), stock);
        }

        ownedBySymbol.clear();
        for (Holding holding : holdingDAO.findByUserId(userId())) {
            ownedBySymbol.put(holding.getSymbol(), holding.getQuantity());
        }

        List<Stock> cached = stockCache.getAll();
        cached.sort(Comparator.comparing(Stock::getSymbol));
        allStocks.setAll(cached);

        LOGGER.fine("Market loaded: " + cached.size() + " stocks on day " + day);
    }

    /** Search text and sector filter combined - no database round trip. */
    private void applyFilter() {
        String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
        String sector = sectorCombo.getValue();

        filteredStocks.setPredicate(stock -> {
            boolean matchesSector = sector == null || ALL_SECTORS.equals(sector)
                    || sector.equals(stock.getSector());
            boolean matchesQuery = query.isEmpty()
                    || stock.getSymbol().toLowerCase().contains(query)
                    || stock.getCompanyName().toLowerCase().contains(query);
            return matchesSector && matchesQuery;
        });

        resultCountLabel.setText(filteredStocks.size() + " of " + allStocks.size() + " stocks");
    }

    /** Comparator lambdas (Unit 2). */
    private void applySort() {
        String choice = sortCombo.getValue();
        if (choice == null) {
            return;
        }
        Comparator<Stock> comparator = switch (choice) {
            case "Company (A-Z)"          -> Comparator.comparing(Stock::getCompanyName);
            case "Price (high to low)"    -> Comparator.comparingDouble(Stock::getCurrentPrice).reversed();
            case "Price (low to high)"    -> Comparator.comparingDouble(Stock::getCurrentPrice);
            case "Change % (best first)"  -> Comparator.comparingDouble(Stock::getChangePercent).reversed();
            case "Change % (worst first)" -> Comparator.comparingDouble(Stock::getChangePercent);
            default                       -> Comparator.comparing(Stock::getSymbol);
        };
        FXCollections.sort(allStocks, comparator);
        applyFilter();
    }

    @FXML
    private void handleReset() {
        searchField.clear();
        sectorCombo.getSelectionModel().selectFirst();
        sortCombo.getSelectionModel().selectFirst();
        applyFilter();
        applySort();
    }

    @FXML
    private void handleOpenSelected() {
        Stock selected = marketTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            SceneManager.warn("No Stock Selected", "Pick a stock first.",
                    "Click a row in the table, then press OPEN STOCK.");
            return;
        }
        openStock(selected);
    }

    private void openStock(Stock stock) {
        SceneManager.<StockDetailsController>switchTo("stock-details.fxml", stock.getSymbol(),
                controller -> controller.showStock(stock.getStockId()));
    }
}

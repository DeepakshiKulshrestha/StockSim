package com.stocksim.util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One-time import of the Kaggle "India Stock Market" CSV into MySQL.
 *
 * Runs on the very first launch only: if {@code stock_history} already has rows
 * the CSV is never opened again and every price afterwards comes from MySQL.
 *
 * Demonstrates (Unit 3): BufferedReader file handling, and (Unit 4) JDBC batch inserts.
 *
 * Why the dates are intersected: the 10 chosen stocks do not all start trading on the
 * same date (HINDUNILVR only from 2008, TCS from 2004, the rest from the 1990s).
 * If simulation_day were just "row number per stock", day 100 would mean a different
 * calendar date for every stock and the portfolio would be nonsense. So the seeder keeps
 * only the trading dates that ALL 10 stocks share, sorts them, and numbers those
 * 1..N. Day N is therefore the same real date for every stock.
 */
public final class DataSeeder {

    private static final Logger LOGGER = Logger.getLogger(DataSeeder.class.getName());

    /** executeBatch() is flushed every this many rows. */
    private static final int BATCH_SIZE = 1000;

    private DataSeeder() {
    }

    /** Entry point called from Main.init(). Does nothing if data is already present. */
    public static void seedIfEmpty() {
        try (Connection conn = DBConnection.getInstance().newConnection()) {
            if (countHistoryRows(conn) > 0) {
                LOGGER.info("stock_history already populated - skipping CSV import.");
                return;
            }
            LOGGER.info("stock_history is empty - importing prices from the Kaggle CSV.");
            seed(conn);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Seeding failed: " + e.getMessage(), e);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Could not read the CSV file: " + e.getMessage(), e);
        }
    }

    /** Forces a re-import (drops existing history first). Used by the test suite. */
    public static void reseed() throws SQLException, IOException {
        try (Connection conn = DBConnection.getInstance().newConnection();
             Statement st = conn.createStatement()) {
            st.executeUpdate("DELETE FROM stock_history");
            seed(conn);
        }
    }

    public static int countHistoryRows(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM stock_history")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    // ---------------------------------------------------------------

    private static void seed(Connection conn) throws SQLException, IOException {
        long start = System.currentTimeMillis();

        Map<String, Integer> stockIds = loadStockIds(conn);
        if (stockIds.isEmpty()) {
            throw new SQLException("The stocks table is empty - run database.sql first.");
        }

        Path csv = resolveCsvPath();
        Map<String, TreeMap<LocalDate, Double>> prices = readCsv(csv, stockIds.keySet());

        List<LocalDate> timeline = buildCommonTimeline(prices);
        if (timeline.isEmpty()) {
            throw new SQLException("No trading dates are common to all selected stocks.");
        }
        LOGGER.info(String.format("Common timeline: %d trading days (%s to %s)",
                timeline.size(), timeline.get(0), timeline.get(timeline.size() - 1)));

        int inserted = insertHistory(conn, stockIds, prices, timeline);
        updateCurrentPrices(conn, stockIds, prices, timeline.get(0));

        LOGGER.info(String.format("Seeding complete: %d rows inserted in %.1f s",
                inserted, (System.currentTimeMillis() - start) / 1000.0));
    }

    private static Path resolveCsvPath() throws IOException {
        String configured = ConfigLoader.get("csv.path");
        if (configured == null || configured.isBlank()) {
            throw new IOException("csv.path is not set in application.properties");
        }
        Path path = Path.of(configured.trim());
        if (!Files.isReadable(path)) {
            throw new IOException("CSV file not readable at: " + path);
        }
        return path;
    }

    private static Map<String, Integer> loadStockIds(Connection conn) throws SQLException {
        Set<String> wanted = new HashSet<>(List.of(ConfigLoader.getList("sim.stocks")));
        Map<String, Integer> ids = new LinkedHashMap<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT stock_id, symbol FROM stocks ORDER BY stock_id")) {
            while (rs.next()) {
                String symbol = rs.getString("symbol");
                if (wanted.isEmpty() || wanted.contains(symbol)) {
                    ids.put(symbol, rs.getInt("stock_id"));
                }
            }
        }
        return ids;
    }

    /**
     * Single streaming pass over the 228 MB CSV, keeping only the close price of the
     * wanted symbols. Columns: Date,Stock,Open,High,Low,Close,Volume,Change Pct
     */
    private static Map<String, TreeMap<LocalDate, Double>> readCsv(Path csv, Set<String> wanted)
            throws IOException {

        Map<String, TreeMap<LocalDate, Double>> prices = new HashMap<>();
        for (String symbol : wanted) {
            prices.put(symbol, new TreeMap<>());
        }

        long lines = 0;
        long kept = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(csv.toFile()), 1 << 20)) {
            String header = reader.readLine();          // skip the header row
            if (header == null) {
                throw new IOException("CSV file is empty: " + csv);
            }

            String line;
            while ((line = reader.readLine()) != null) {
                lines++;

                int c1 = line.indexOf(',');
                if (c1 < 0) continue;
                int c2 = line.indexOf(',', c1 + 1);
                if (c2 < 0) continue;

                String symbol = line.substring(c1 + 1, c2);
                TreeMap<LocalDate, Double> series = prices.get(symbol);
                if (series == null) {
                    continue;                            // not one of our 10 stocks
                }

                // Close is the 6th column -> skip past Open, High, Low
                int c3 = line.indexOf(',', c2 + 1);
                int c4 = c3 < 0 ? -1 : line.indexOf(',', c3 + 1);
                int c5 = c4 < 0 ? -1 : line.indexOf(',', c4 + 1);
                int c6 = c5 < 0 ? -1 : line.indexOf(',', c5 + 1);
                if (c5 < 0) continue;

                String closeText = (c6 < 0) ? line.substring(c5 + 1) : line.substring(c5 + 1, c6);
                try {
                    double close = Double.parseDouble(closeText);
                    if (close <= 0 || Double.isNaN(close)) {
                        continue;                        // ignore bad rows
                    }
                    LocalDate date = LocalDate.parse(line.substring(0, c1));
                    series.put(date, close);
                    kept++;
                } catch (NumberFormatException | DateTimeParseException e) {
                    // malformed row - skip it and keep importing
                }
            }
        }

        LOGGER.info(String.format("Read %,d CSV rows, kept %,d rows for %d stocks.",
                lines, kept, prices.size()));
        return prices;
    }

    /** Trading dates present for every stock, oldest first. Index + 1 = simulation_day. */
    private static List<LocalDate> buildCommonTimeline(Map<String, TreeMap<LocalDate, Double>> prices) {
        Set<LocalDate> common = null;
        for (TreeMap<LocalDate, Double> series : prices.values()) {
            if (common == null) {
                common = new HashSet<>(series.keySet());
            } else {
                common.retainAll(series.keySet());
            }
        }
        List<LocalDate> timeline = new ArrayList<>(common == null ? Set.<LocalDate>of() : common);
        timeline.sort(LocalDate::compareTo);
        return timeline;
    }

    private static int insertHistory(Connection conn,
                                     Map<String, Integer> stockIds,
                                     Map<String, TreeMap<LocalDate, Double>> prices,
                                     List<LocalDate> timeline) throws SQLException {

        String sql = "INSERT INTO stock_history (stock_id, price, recorded_at, simulation_day) "
                   + "VALUES (?, ?, ?, ?)";

        boolean originalAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        int total = 0;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Map.Entry<String, Integer> entry : stockIds.entrySet()) {
                TreeMap<LocalDate, Double> series = prices.get(entry.getKey());
                if (series == null) {
                    continue;
                }
                int pending = 0;
                for (int day = 1; day <= timeline.size(); day++) {
                    LocalDate date = timeline.get(day - 1);
                    Double price = series.get(date);
                    if (price == null) {
                        continue;                        // cannot happen for a common date
                    }
                    ps.setInt(1, entry.getValue());
                    ps.setDouble(2, price);
                    ps.setDate(3, java.sql.Date.valueOf(date));
                    ps.setInt(4, day);
                    ps.addBatch();

                    if (++pending % BATCH_SIZE == 0) {
                        total += sum(ps.executeBatch());
                    }
                }
                total += sum(ps.executeBatch());
                LOGGER.info("Imported " + entry.getKey() + " (" + timeline.size() + " days)");
            }
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            LOGGER.log(Level.SEVERE, "Batch insert failed - rolled back", e);
            throw e;
        } finally {
            conn.setAutoCommit(originalAutoCommit);
        }
        return total;
    }

    /** Sets stocks.current_price to the price on simulation day 1, where every user starts. */
    private static void updateCurrentPrices(Connection conn,
                                            Map<String, Integer> stockIds,
                                            Map<String, TreeMap<LocalDate, Double>> prices,
                                            LocalDate firstDate) throws SQLException {

        String sql = "UPDATE stocks SET current_price = ? WHERE stock_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Map.Entry<String, Integer> entry : stockIds.entrySet()) {
                Double price = prices.get(entry.getKey()).get(firstDate);
                if (price == null) {
                    continue;
                }
                ps.setDouble(1, price);
                ps.setInt(2, entry.getValue());
                ps.addBatch();
            }
            ps.executeBatch();
        }
        LOGGER.info("stocks.current_price set to day 1 prices (" + firstDate + ").");
    }

    private static int sum(int[] counts) {
        int total = 0;
        for (int c : counts) {
            if (c > 0) {
                total += c;
            }
        }
        return total;
    }
}

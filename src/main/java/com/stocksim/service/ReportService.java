package com.stocksim.service;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.stocksim.model.Holding;
import com.stocksim.model.PortfolioSummary;
import com.stocksim.model.Transaction;
import com.stocksim.model.User;
import com.stocksim.util.ConfigLoader;
import com.stocksim.util.Formats;

/**
 * Writes the .txt reports the user can export.
 * Demonstrates Unit 3 file handling: FileWriter wrapped in a BufferedWriter.
 */
public class ReportService {

    private static final Logger LOGGER = Logger.getLogger(ReportService.class.getName());

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final String LINE = "=".repeat(78);
    private static final String THIN = "-".repeat(78);

    private final PortfolioService portfolioService;
    private final RiskService riskService;

    public ReportService() {
        this(new PortfolioService(), new RiskService());
    }

    public ReportService(PortfolioService portfolioService, RiskService riskService) {
        this.portfolioService = portfolioService;
        this.riskService = riskService;
    }

    /** Directory reports are written to, created on first use. */
    public Path getExportDirectory() throws IOException {
        Path dir = Path.of(ConfigLoader.get("export.dir", "reports"));
        Files.createDirectories(dir);
        return dir;
    }

    // ---------------- portfolio report ----------------

    public Path exportPortfolio(User user) throws IOException {
        Path file = getExportDirectory()
                .resolve("portfolio_report_" + user.getUsername() + "_"
                        + LocalDateTime.now().format(STAMP) + ".txt");

        PortfolioSummary summary = portfolioService.getPortfolioSummary(user.getUserId());
        List<Holding> holdings = portfolioService.getHoldings(user.getUserId());
        Map<String, Double> sectors = riskService.getSectorAllocation(user.getUserId());

        // BufferedWriter wrapping a FileWriter - Unit 3 requirement
        try (BufferedWriter writer = newWriter(file)) {
            header(writer, "STOCKSIM - PORTFOLIO REPORT", user);

            writer.write("SUMMARY");
            writer.newLine();
            writer.write(THIN);
            writer.newLine();
            kv(writer, "Cash balance",        Formats.plain(summary.getCash()));
            kv(writer, "Invested amount",     Formats.plain(summary.getInvested()));
            kv(writer, "Holdings value",      Formats.plain(summary.getHoldingsValue()));
            kv(writer, "Total portfolio",     Formats.plain(summary.getTotalValue()));
            kv(writer, "Unrealised P/L",      Formats.plain(summary.getProfitLoss())
                    + "  (" + Formats.signedPercent(summary.getProfitLossPercent()) + ")");
            kv(writer, "Starting capital",    Formats.plain(summary.getStartingCapital()));
            kv(writer, "Overall return",      Formats.signedPercent(summary.getTotalReturnPercent()));
            writer.newLine();

            writer.write("HOLDINGS (" + holdings.size() + ")");
            writer.newLine();
            writer.write(THIN);
            writer.newLine();

            if (holdings.isEmpty()) {
                writer.write("  You do not own any shares yet.");
                writer.newLine();
            } else {
                writer.write(String.format("%-12s %8s %13s %13s %15s %15s %9s",
                        "STOCK", "QTY", "AVG BUY", "CURRENT", "VALUE", "P/L", "P/L %"));
                writer.newLine();
                for (Holding h : holdings) {
                    writer.write(String.format("%-12s %8d %13s %13s %15s %15s %8s%%",
                            h.getSymbol(),
                            h.getQuantity(),
                            Formats.plain(h.getAverageBuyPrice()),
                            Formats.plain(h.getCurrentPrice()),
                            Formats.plain(h.calculateValue()),
                            Formats.plain(h.calculateProfitLoss()),
                            String.format("%+.2f", h.calculateProfitLossPercent())));
                    writer.newLine();
                }
            }
            writer.newLine();

            writer.write("SECTOR ALLOCATION");
            writer.newLine();
            writer.write(THIN);
            writer.newLine();
            if (sectors.isEmpty()) {
                writer.write("  No sector exposure yet.");
                writer.newLine();
            } else {
                for (Map.Entry<String, Double> entry : sectors.entrySet()) {
                    writer.write(String.format("  %-14s %6.2f%%  %s",
                            entry.getKey(), entry.getValue(), bar(entry.getValue())));
                    writer.newLine();
                }
                writer.newLine();
                kv(writer, "Diversification", riskService.getDiversificationLevel(user.getUserId()));
                List<String> warnings = riskService.getConcentrationWarnings(user.getUserId());
                for (String warning : warnings) {
                    writer.write("  ! " + warning);
                    writer.newLine();
                }
            }

            footer(writer);
        }

        LOGGER.info("Portfolio report written to " + file.toAbsolutePath());
        return file;
    }

    // ---------------- transactions report ----------------

    public Path exportTransactions(User user, List<Transaction> transactions) throws IOException {
        Path file = getExportDirectory()
                .resolve("transactions_report_" + user.getUsername() + "_"
                        + LocalDateTime.now().format(STAMP) + ".txt");

        try (BufferedWriter writer = newWriter(file)) {
            header(writer, "STOCKSIM - TRANSACTION HISTORY", user);

            writer.write("TRANSACTIONS (" + transactions.size() + ")");
            writer.newLine();
            writer.write(THIN);
            writer.newLine();

            if (transactions.isEmpty()) {
                writer.write("  No transactions recorded yet.");
                writer.newLine();
            } else {
                writer.write(String.format("%-20s %-12s %-6s %8s %13s %16s %6s",
                        "DATE", "STOCK", "TYPE", "QTY", "PRICE", "TOTAL", "DAY"));
                writer.newLine();

                double bought = 0.0;
                double sold = 0.0;
                for (Transaction txn : transactions) {
                    writer.write(String.format("%-20s %-12s %-6s %8d %13s %16s %6d",
                            Formats.dateTime(txn.getTransactionDate()),
                            txn.getSymbol(),
                            txn.getTypeName(),
                            txn.getQuantity(),
                            Formats.plain(txn.getPrice()),
                            Formats.plain(txn.getTotalAmount()),
                            txn.getSimulationDay()));
                    writer.newLine();

                    if (txn.isBuy()) {
                        bought += txn.getTotalAmount();
                    } else {
                        sold += txn.getTotalAmount();
                    }
                }
                writer.newLine();
                kv(writer, "Total bought", Formats.plain(bought));
                kv(writer, "Total sold",   Formats.plain(sold));
                kv(writer, "Net invested", Formats.plain(bought - sold));
            }

            footer(writer);
        }

        LOGGER.info("Transaction report written to " + file.toAbsolutePath());
        return file;
    }

    // ---------------------------------------------------------------

    private BufferedWriter newWriter(Path file) throws IOException {
        // FileWriter with an explicit charset so rupee amounts and names survive round-trips
        return new BufferedWriter(new FileWriter(file.toFile(), StandardCharsets.UTF_8));
    }

    private void header(Writer w, String title, User user) throws IOException {
        write(w, LINE);
        write(w, centre(title));
        write(w, LINE);
        write(w, "User            : " + user.getName() + " (" + user.getUsername() + ")");
        write(w, "Email           : " + user.getEmail());
        write(w, "Simulation day  : " + user.getSimDay());
        write(w, "Generated       : " + Formats.dateTime(LocalDateTime.now()));
        write(w, "Currency        : INR (all amounts are virtual money)");
        write(w, LINE);
        w.write(System.lineSeparator());
    }

    private void footer(Writer w) throws IOException {
        w.write(System.lineSeparator());
        write(w, LINE);
        write(w, centre("StockSim is an educational simulator. No real money is involved."));
        write(w, LINE);
    }

    private void kv(Writer w, String key, String value) throws IOException {
        write(w, String.format("  %-20s : %s", key, value));
    }

    private void write(Writer w, String text) throws IOException {
        w.write(text);
        w.write(System.lineSeparator());
    }

    private String centre(String text) {
        int pad = Math.max(0, (78 - text.length()) / 2);
        return " ".repeat(pad) + text;
    }

    /** Tiny ASCII bar so the sector breakdown is readable in a plain text file. */
    private String bar(double percent) {
        int blocks = (int) Math.round(percent / 4.0);
        return "#".repeat(Math.max(0, Math.min(25, blocks)));
    }

    /** Best-effort delete used by the test suite to clean up generated reports. */
    public static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Could not delete " + file, e);
        }
    }
}

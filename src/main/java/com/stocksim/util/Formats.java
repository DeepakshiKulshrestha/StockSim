package com.stocksim.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Shared number/date formatting so every screen shows rupees the same way. */
public final class Formats {

    public static final String RUPEE = "₹";

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("dd MMM yyyy");

    private Formats() {
    }

    /** e.g. "₹1,00,000.00" - Indian grouping (lakh/crore). */
    public static String money(double amount) {
        return RUPEE + indianGrouping(amount);
    }

    /** Same as {@link #money} but with an explicit + or - sign, for P/L figures. */
    public static String signedMoney(double amount) {
        String sign = amount >= 0 ? "+" : "-";
        return sign + RUPEE + indianGrouping(Math.abs(amount));
    }

    public static String percent(double value) {
        return String.format("%.2f%%", value);
    }

    public static String signedPercent(double value) {
        return String.format("%s%.2f%%", value >= 0 ? "+" : "", value);
    }

    /** Plain number, no currency symbol - used inside .txt exports. */
    public static String plain(double amount) {
        return indianGrouping(amount);
    }

    public static String dateTime(LocalDateTime value) {
        return value == null ? "-" : value.format(DATE_TIME);
    }

    public static String date(LocalDate value) {
        return value == null ? "-" : value.format(DATE);
    }

    /** CSS style class matching the sign of a value. */
    public static String signClass(double value) {
        if (value > 0) return "positive";
        if (value < 0) return "negative";
        return "neutral";
    }

    /**
     * Indian digit grouping: the last three digits, then pairs.
     * 100000 -> 1,00,000.00
     */
    private static String indianGrouping(double amount) {
        String formatted = String.format("%.2f", Math.abs(amount));
        int dot = formatted.indexOf('.');
        String whole = formatted.substring(0, dot);
        String decimals = formatted.substring(dot);

        StringBuilder out = new StringBuilder();
        int len = whole.length();
        if (len <= 3) {
            out.append(whole);
        } else {
            String lastThree = whole.substring(len - 3);
            String rest = whole.substring(0, len - 3);
            StringBuilder restGrouped = new StringBuilder();
            int count = 0;
            for (int i = rest.length() - 1; i >= 0; i--) {
                restGrouped.append(rest.charAt(i));
                count++;
                if (count % 2 == 0 && i > 0) {
                    restGrouped.append(',');
                }
            }
            out.append(restGrouped.reverse()).append(',').append(lastThree);
        }
        String result = out + decimals;
        return amount < 0 ? "-" + result : result;
    }
}

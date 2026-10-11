package com.stocksim.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads application.properties from the classpath exactly once and exposes
 * typed lookups. Demonstrates: static members, Properties, classpath IO.
 */
public final class ConfigLoader {

    private static final Logger LOGGER = Logger.getLogger(ConfigLoader.class.getName());
    private static final String FILE_NAME = "application.properties";
    private static final Properties PROPS = new Properties();

    static {
        try (InputStream in = ConfigLoader.class.getClassLoader().getResourceAsStream(FILE_NAME)) {
            if (in == null) {
                throw new IOException(FILE_NAME + " not found on classpath");
            }
            PROPS.load(in);
            LOGGER.info("Configuration loaded from " + FILE_NAME);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Could not load " + FILE_NAME, e);
            throw new ExceptionInInitializerError(e);
        }
    }

    private ConfigLoader() {
        // utility class - no instances
    }

    public static String get(String key) {
        String value = PROPS.getProperty(key);
        if (value == null) {
            LOGGER.warning("Missing configuration key: " + key);
        }
        return value;
    }

    public static String get(String key, String defaultValue) {
        return PROPS.getProperty(key, defaultValue);
    }

    public static double getDouble(String key, double defaultValue) {
        try {
            return Double.parseDouble(PROPS.getProperty(key, String.valueOf(defaultValue)).trim());
        } catch (NumberFormatException e) {
            LOGGER.warning("Invalid double for key " + key + ", using default " + defaultValue);
            return defaultValue;
        }
    }

    public static int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(PROPS.getProperty(key, String.valueOf(defaultValue)).trim());
        } catch (NumberFormatException e) {
            LOGGER.warning("Invalid int for key " + key + ", using default " + defaultValue);
            return defaultValue;
        }
    }

    /** Comma-separated value such as sim.stocks -> trimmed array. */
    public static String[] getList(String key) {
        String raw = PROPS.getProperty(key, "");
        if (raw.isBlank()) {
            return new String[0];
        }
        String[] parts = raw.split(",");
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim();
        }
        return parts;
    }
}

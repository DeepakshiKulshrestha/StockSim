package com.stocksim.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Small generic in-memory cache, used as {@code DataCache<Stock>} in MarketController
 * so the stock list is not re-fetched from MySQL on every keystroke of the search box.
 * Demonstrates: generics, HashMap, ArrayList (Unit 2).
 *
 * @param <T> type of object being cached
 */
public class DataCache<T> {

    private final Map<String, T> cache = new HashMap<>();

    public void put(String key, T value) {
        cache.put(key, value);
    }

    public T get(String key) {
        return cache.get(key);
    }

    public boolean contains(String key) {
        return cache.containsKey(key);
    }

    public List<T> getAll() {
        return new ArrayList<>(cache.values());
    }

    public void putAll(Map<String, T> values) {
        cache.putAll(values);
    }

    public void remove(String key) {
        cache.remove(key);
    }

    public void clear() {
        cache.clear();
    }

    public int size() {
        return cache.size();
    }

    public boolean isEmpty() {
        return cache.isEmpty();
    }
}

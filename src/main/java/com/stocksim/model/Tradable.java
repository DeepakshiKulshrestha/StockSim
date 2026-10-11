package com.stocksim.model;

import com.stocksim.exception.InsufficientBalanceException;
import com.stocksim.exception.InsufficientSharesException;
import com.stocksim.exception.InvalidQuantityException;

/**
 * Contract for anything the user can trade. Implemented by {@link Holding}
 * so a holding can add/remove shares while keeping its weighted average cost correct.
 * Demonstrates: interfaces, checked exceptions in a contract.
 */
public interface Tradable {

    void buy(int quantity, double price) throws InsufficientBalanceException, InvalidQuantityException;

    void sell(int quantity) throws InsufficientSharesException, InvalidQuantityException;
}

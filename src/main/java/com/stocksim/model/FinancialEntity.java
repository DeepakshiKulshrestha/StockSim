package com.stocksim.model;

import java.io.Serializable;

/**
 * Abstract base for anything in the app that carries a rupee value the user cares
 * about (a holding, a goal). Demonstrates: abstract classes, inheritance,
 * protected members, template behaviour via a concrete method built on abstract ones.
 */
public abstract class FinancialEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Owning user - every financial entity in StockSim belongs to someone. */
    protected int userId;

    protected FinancialEntity() {
    }

    protected FinancialEntity(int userId) {
        this.userId = userId;
    }

    /** Current rupee value of this entity. */
    public abstract double getValue();

    /** Human readable one-line summary, used in exports and list cells. */
    public abstract String getDescription();

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    /** Concrete method that builds on the abstract ones - shared by all subclasses. */
    public String getFormattedValue() {
        return String.format("Rs %,.2f", getValue());
    }
}

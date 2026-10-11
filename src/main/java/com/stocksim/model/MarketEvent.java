package com.stocksim.model;

import java.io.Serializable;

/** A scripted news item that fires on a given simulation day. */
public class MarketEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private int eventId;
    private String title;
    private String description;
    private String sector;
    private int simulationDay;
    private String impact;

    public MarketEvent() {
    }

    public MarketEvent(String title, String description, String sector, int simulationDay, String impact) {
        this.title = title;
        this.description = description;
        this.sector = sector;
        this.simulationDay = simulationDay;
        this.impact = impact;
    }

    public int getEventId() { return eventId; }
    public void setEventId(int eventId) { this.eventId = eventId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getSector() { return sector; }
    public void setSector(String sector) { this.sector = sector; }

    public int getSimulationDay() { return simulationDay; }
    public void setSimulationDay(int simulationDay) { this.simulationDay = simulationDay; }

    public String getImpact() { return impact; }
    public void setImpact(String impact) { this.impact = impact; }

    @Override
    public String toString() {
        return "[Day " + simulationDay + "] " + title;
    }
}

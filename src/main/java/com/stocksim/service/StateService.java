package com.stocksim.service;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.stocksim.dao.StockDAO;
import com.stocksim.dao.UserDAO;
import com.stocksim.model.SimulationState;
import com.stocksim.model.Stock;
import com.stocksim.model.User;
import com.stocksim.util.ConfigLoader;

/**
 * Saves and restores a simulation snapshot with Java serialization.
 * Demonstrates Unit 3: ObjectOutputStream / ObjectInputStream.
 */
public class StateService {

    private static final Logger LOGGER = Logger.getLogger(StateService.class.getName());

    private final UserDAO userDAO;
    private final StockDAO stockDAO;
    private final SimulationService simulationService;

    public StateService() {
        this(new UserDAO(), new StockDAO(), new SimulationService());
    }

    public StateService(UserDAO userDAO, StockDAO stockDAO, SimulationService simulationService) {
        this.userDAO = userDAO;
        this.stockDAO = stockDAO;
        this.simulationService = simulationService;
    }

    public Path getStateFile() {
        return Path.of(ConfigLoader.get("serialization.file", "simulation_state.ser"));
    }

    public boolean stateExists() {
        return Files.isReadable(getStateFile());
    }

    /** Writes the user's current day, balance and every stock price to simulation_state.ser. */
    public Path saveState(int userId) throws IOException {
        User user = userDAO.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("No user with id " + userId));

        SimulationState state = new SimulationState(
                user.getUserId(), user.getUsername(), user.getSimDay(), user.getBalance());

        for (Stock stock : stockDAO.findAll()) {
            state.getStockPrices().put(stock.getStockId(), stock.getCurrentPrice());
        }

        Path file = getStateFile();
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(file.toFile()))) {
            out.writeObject(state);
        }
        LOGGER.info("Simulation state saved: " + state + " -> " + file.toAbsolutePath());
        return file;
    }

    /** Reads simulation_state.ser back into a SimulationState object. */
    public Optional<SimulationState> readState() {
        Path file = getStateFile();
        if (!Files.isReadable(file)) {
            LOGGER.warning("No saved state found at " + file.toAbsolutePath());
            return Optional.empty();
        }
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(file.toFile()))) {
            SimulationState state = (SimulationState) in.readObject();
            LOGGER.info("Simulation state read: " + state);
            return Optional.of(state);
        } catch (IOException | ClassNotFoundException e) {
            LOGGER.log(Level.SEVERE, "Could not read the saved simulation state", e);
            return Optional.empty();
        }
    }

    /**
     * Restores a saved snapshot: the user's simulation day is moved back and every
     * stock price is repointed at that day.
     *
     * @return the restored state, or empty when there was nothing to restore
     */
    public Optional<SimulationState> loadState(int userId) {
        Optional<SimulationState> maybeState = readState();
        if (maybeState.isEmpty()) {
            return Optional.empty();
        }

        SimulationState state = maybeState.get();
        if (state.getUserId() != userId) {
            state.setLoadNote("This snapshot belongs to another account ("
                    + state.getUsername() + ") and was not applied.");
            LOGGER.warning(state.getLoadNote());
            return Optional.of(state);
        }

        simulationService.resetToDay(userId, state.getSimulationDay());
        userDAO.updateBalance(userId, state.getBalance());
        state.setLoadNote("Restored to simulation day " + state.getSimulationDay() + ".");
        LOGGER.info("Simulation state restored for user " + userId + ": " + state.getLoadNote());
        return Optional.of(state);
    }

    public boolean deleteState() {
        try {
            return Files.deleteIfExists(getStateFile());
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not delete the saved state file", e);
            return false;
        }
    }
}

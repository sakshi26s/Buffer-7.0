package evcharge.engine;

import evcharge.model.ChargingStation;

import java.util.List;
import java.util.function.Consumer;

/**
 * Monitors station health, toggles offline states, and provides failure prediction.
 */
public class MaintenanceAgent {
    private final List<ChargingStation> stations;
    private final Consumer<String> logger;

    public MaintenanceAgent(List<ChargingStation> stations, Consumer<String> logger) {
        this.stations = stations;
        this.logger = logger;
    }

    /**
     * Called per simulation tick. Iterates through stations.
     * If health < 20% AND the station is not already offline, the agent will take it offline for 5 ticks.
     */
    public void executeTick() {
        for (ChargingStation station : stations) {
            if (!station.isOffline() && station.getHealth() < 20.0) {
                station.setOfflineFor(5);
                if (logger != null) {
                    logger.accept("🔧 MaintenanceAgent: Taking '" + station.getName() + "' offline for 5 ticks due to low health (" + String.format("%.1f", station.getHealth()) + "%).");
                }
            }
        }
    }

    /**
     * Uses linear estimation to forecast ticks remaining until health hits the 20% threshold.
     */
    public int predictTicksToFailure(ChargingStation station) {
        if (station.getHealth() <= 20.0) {
            return 0;
        }
        double expectedHealthDropPerTick = station.getWearRate() * station.getMaxChargers();
        if (expectedHealthDropPerTick <= 0) {
            return Integer.MAX_VALUE;
        }
        return (int) Math.ceil((station.getHealth() - 20.0) / expectedHealthDropPerTick);
    }
}

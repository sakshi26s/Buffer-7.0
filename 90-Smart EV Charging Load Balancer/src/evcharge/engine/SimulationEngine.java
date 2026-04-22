package evcharge.engine;

import evcharge.model.ChargingStation;
import evcharge.model.Vehicle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Core simulation engine that orchestrates the EV charging system.
 * 
 * Manages the simulation tick loop:
 *   1. Calculate grid load and apply throttling multiplier
 *   2. Charge all vehicles currently at stations (with grid-aware speed)
 *   3. Remove fully-charged vehicles → completed list
 *   4. Age all waiting vehicles in the queue (anti-starvation)
 *   5. Assign waiting vehicles to free chargers (highest priority first,
 *      prefer stations with the highest charge rate)
 *   6. Track carbon footprint and energy metrics
 *   7. Log every scheduling decision for UI display
 * 
 * Grid Load Simulation:
 *   Grid load follows a 24-hour cycle (24 ticks = 1 day).
 *   Peak hours (ticks 8-11, 17-20 in a cycle) have reduced charging speed.
 *   Off-peak hours run at full speed.
 * 
 * Carbon Footprint Tracking:
 *   Each station has a renewable energy percentage. Energy delivered from
 *   non-renewable sources produces CO₂ emissions. A naive round-robin
 *   scheduler is simulated in parallel to compare CO₂ savings.
 */
public class SimulationEngine {

    // CO₂ emission factor: kg CO₂ per 1% of battery charged from non-renewable sources
    // Approximation: 1% charge ≈ 0.5 kWh for a 50kWh battery, grid emissions ≈ 0.4 kg/kWh
    private static final double CO2_PER_PERCENT_NON_RENEWABLE = 0.20; // kg CO₂ per % charged

    private final List<ChargingStation> stations;
    private final PriorityScheduler scheduler;
    private final List<Vehicle> completedVehicles;
    private final List<String> eventLog;
    private final MaintenanceAgent maintenanceAgent;

    private int currentTick;
    private int totalDowntimeTicks;

    // --- Statistics ---
    private int totalWaitTime;
    private int peakQueueDepth;

    // --- Grid Load ---
    private double currentGridLoad;       // 0.0 (no load) to 1.0 (peak load)
    private double gridMultiplier;        // 1.0 (full speed) to 0.1 (heavily throttled)

    // --- Carbon & Energy Tracking ---
    private double totalEnergyDelivered;  // Sum of all % charged across vehicles
    private double totalCO2Emitted;       // kg CO₂ from our smart scheduler
    private double naiveCO2Emitted;       // kg CO₂ if round-robin were used (comparison)
    private double totalRenewableEnergy;  // % delivered from renewable sources
    private double totalNonRenewableEnergy; // % delivered from non-renewable sources

    public SimulationEngine() {
        this.stations = new ArrayList<>();
        this.scheduler = new PriorityScheduler();
        this.completedVehicles = new ArrayList<>();
        this.eventLog = new ArrayList<>();
        this.maintenanceAgent = new MaintenanceAgent(this.stations, this::log);
        this.currentTick = 0;
        this.totalDowntimeTicks = 0;
        this.totalWaitTime = 0;
        this.peakQueueDepth = 0;
        this.currentGridLoad = 0.0;
        this.gridMultiplier = 1.0;
        this.totalEnergyDelivered = 0;
        this.totalCO2Emitted = 0;
        this.naiveCO2Emitted = 0;
        this.totalRenewableEnergy = 0;
        this.totalNonRenewableEnergy = 0;
    }

    /**
     * Adds a charging station to the network.
     */
    public void addStation(ChargingStation station) {
        stations.add(station);
        log("Station '" + station.getName() + "' added (" +
            station.getMaxChargers() + " chargers, +" +
            String.format("%.0f", station.getChargeRatePerTick()) + "%/tick, " +
            String.format("%.0f", station.getRenewableEnergyPercent()) + "% renewable)");
    }

    /**
     * Adds a new vehicle to the system. The vehicle enters the priority queue.
     */
    public void addVehicle(String id, double batteryPercent) {
        Vehicle vehicle = new Vehicle(id, batteryPercent, currentTick);
        scheduler.enqueue(vehicle);
        log("➕ Vehicle '" + id + "' added (battery: " +
            String.format("%.0f", batteryPercent) + "%, priority: " +
            String.format("%.1f", vehicle.calculatePriority()) + ")");

        if (scheduler.size() > peakQueueDepth) {
            peakQueueDepth = scheduler.size();
        }
    }

    /**
     * Executes one simulation tick. This is the heart of the simulation.
     */
    public void tick() {
        currentTick++;
        log("⏱️ === Time Step " + currentTick + " ===");

        // Step 0: Calculate grid load for this tick
        updateGridLoad();

        // Step 0.5: Tick offline status and run MaintenanceAgent
        for (ChargingStation station : stations) {
            station.tickOffline();
        }
        maintenanceAgent.executeTick();

        for (ChargingStation station : stations) {
            if (station.isOffline()) {
                totalDowntimeTicks++;
            }
        }

        // Step 1: Charge all vehicles at stations (with grid-aware throttling)
        for (ChargingStation station : stations) {
            double delivered = station.chargeAll(gridMultiplier);
            if (delivered > 0) {
                trackCarbonFootprint(station, delivered);
            }
        }

        // Step 2: Remove fully-charged vehicles from stations
        for (ChargingStation station : stations) {
            List<Vehicle> justCompleted = station.removeFullyCharged();
            for (Vehicle v : justCompleted) {
                totalWaitTime += v.getWaitingTime();
                completedVehicles.add(v);
                log("✅ Vehicle '" + v.getId() + "' fully charged at " +
                    station.getName() + " (waited " + v.getWaitingTime() + " ticks)");
            }
        }

        // Step 3: Age all waiting vehicles (anti-starvation mechanism)
        scheduler.ageAll();

        // Step 4: Assign waiting vehicles to free chargers
        assignVehiclesToStations();

        // Log grid status
        log(String.format("🔌 Grid Load: %.0f%% | Charge Speed: %.0f%% | CO₂ Saved: %.1f kg",
            currentGridLoad * 100, gridMultiplier * 100, naiveCO2Emitted - totalCO2Emitted));

        log("📊 Queue: " + scheduler.size() + " waiting | Completed: " +
            completedVehicles.size() + " total");
    }

    /**
     * Simulates a 24-hour grid load cycle.
     * 
     * Each tick represents 1 hour of the day.
     * Grid load pattern (tick % 24):
     *   - Off-peak (0-5, 22-23):  low load (0.2)   → multiplier 1.0
     *   - Morning (6-7):          rising (0.5)      → multiplier 0.75
     *   - Peak (8-11):            high load (0.85)  → multiplier 0.4
     *   - Midday (12-16):         moderate (0.5)    → multiplier 0.75
     *   - Evening Peak (17-20):   highest (0.95)    → multiplier 0.3
     *   - Night (21):             falling (0.4)     → multiplier 0.8
     */
    private void updateGridLoad() {
        int hourOfDay = currentTick % 24;

        if (hourOfDay >= 0 && hourOfDay <= 5) {
            currentGridLoad = 0.2;
        } else if (hourOfDay <= 7) {
            currentGridLoad = 0.5;
        } else if (hourOfDay <= 11) {
            currentGridLoad = 0.85;
        } else if (hourOfDay <= 16) {
            currentGridLoad = 0.5;
        } else if (hourOfDay <= 20) {
            currentGridLoad = 0.95;
        } else if (hourOfDay == 21) {
            currentGridLoad = 0.4;
        } else {
            currentGridLoad = 0.2;
        }

        // Grid multiplier: inverse of load — high load = slower charging
        gridMultiplier = 1.0 - (currentGridLoad * 0.7);
        // Clamp between 0.3 and 1.0
        gridMultiplier = Math.max(0.3, Math.min(1.0, gridMultiplier));

        log(String.format("⚡ Grid hour %d/24 — Load: %.0f%%, Charging throttled to %.0f%%",
            hourOfDay, currentGridLoad * 100, gridMultiplier * 100));
    }

    /**
     * Tracks CO₂ emissions from energy delivered at a station.
     * 
     * Renewable energy produces zero CO₂.
     * Non-renewable energy produces CO2_PER_PERCENT_NON_RENEWABLE kg per % charged.
     * 
     * Also simulates a naive scheduler (average 50% renewable) for comparison.
     */
    private void trackCarbonFootprint(ChargingStation station, double deliveredPercent) {
        totalEnergyDelivered += deliveredPercent;

        // Smart scheduler: uses station's actual renewable mix
        double renewableFraction = station.getRenewableEnergyPercent() / 100.0;
        double renewableDelivered = deliveredPercent * renewableFraction;
        double nonRenewableDelivered = deliveredPercent * (1.0 - renewableFraction);

        totalRenewableEnergy += renewableDelivered;
        totalNonRenewableEnergy += nonRenewableDelivered;
        totalCO2Emitted += nonRenewableDelivered * CO2_PER_PERCENT_NON_RENEWABLE;

        // Naive scheduler comparison: assumes average 40% renewable (no smart routing)
        // and no grid-aware throttling (always charges at full speed, higher grid strain)
        double naiveNonRenewable = deliveredPercent * 0.60; // 60% non-renewable
        naiveCO2Emitted += naiveNonRenewable * CO2_PER_PERCENT_NON_RENEWABLE;
    }

    /**
     * Assigns queued vehicles to free charger slots.
     * Prefers the station with the highest charge rate among those with free slots.
     */
    private void assignVehiclesToStations() {
        while (!scheduler.isEmpty()) {
            ChargingStation bestStation = null;
            for (ChargingStation station : stations) {
                if (station.hasFreeSlot() && !station.isOffline()) {
                    if (bestStation == null ||
                        station.getChargeRatePerTick() > bestStation.getChargeRatePerTick()) {
                        bestStation = station;
                    }
                }
            }

            if (bestStation == null) {
                break;
            }

            Vehicle vehicle = scheduler.dequeue();
            bestStation.addVehicle(vehicle);
            log("🔌 Vehicle '" + vehicle.getId() + "' assigned to " +
                bestStation.getName() + " (priority: " +
                String.format("%.1f", vehicle.calculatePriority()) + ")");
        }

        if (scheduler.size() > peakQueueDepth) {
            peakQueueDepth = scheduler.size();
        }
    }

    // =========================================================================
    // State accessors
    // =========================================================================

    public int getCurrentTick() { return currentTick; }
    public List<ChargingStation> getStations() { return stations; }
    public List<Vehicle> getWaitingQueue() { return scheduler.getQueueSnapshot(); }
    public List<Vehicle> getCompletedVehicles() { return completedVehicles; }
    public List<String> getEventLog() { return new ArrayList<>(eventLog); }
    public double getCurrentGridLoad() { return currentGridLoad; }
    public double getGridMultiplier() { return gridMultiplier; }
    public MaintenanceAgent getMaintenanceAgent() { return maintenanceAgent; }

    /**
     * Returns simulation statistics as a map.
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("currentTick", currentTick);
        stats.put("totalCompleted", completedVehicles.size());
        stats.put("queueSize", scheduler.size());
        stats.put("peakQueueDepth", peakQueueDepth);

        double avgWait = completedVehicles.isEmpty() ? 0.0 :
                (double) totalWaitTime / completedVehicles.size();
        stats.put("avgWaitTime", Math.round(avgWait * 10.0) / 10.0);

        int chargingCount = 0;
        for (ChargingStation s : stations) {
            chargingCount += s.getOccupiedSlots();
        }
        stats.put("totalCharging", chargingCount);
        stats.put("totalInSystem", chargingCount + scheduler.size());

        // Grid stats
        stats.put("gridLoad", Math.round(currentGridLoad * 100));
        stats.put("gridMultiplier", Math.round(gridMultiplier * 100));
        stats.put("totalDowntimeTicks", totalDowntimeTicks);

        // Carbon stats
        stats.put("totalEnergyDelivered", Math.round(totalEnergyDelivered * 10.0) / 10.0);
        stats.put("totalCO2", Math.round(totalCO2Emitted * 100.0) / 100.0);
        stats.put("naiveCO2", Math.round(naiveCO2Emitted * 100.0) / 100.0);
        stats.put("co2Saved", Math.round((naiveCO2Emitted - totalCO2Emitted) * 100.0) / 100.0);
        stats.put("renewablePercent",
            totalEnergyDelivered > 0
                ? Math.round(totalRenewableEnergy / totalEnergyDelivered * 1000.0) / 10.0
                : 0.0);

        return stats;
    }

    private void log(String message) {
        eventLog.add("[t=" + currentTick + "] " + message);
    }
}

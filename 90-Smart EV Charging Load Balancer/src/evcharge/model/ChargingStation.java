package evcharge.model;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Represents a Charging Station in the EV charging network.
 * 
 * Each station has a fixed number of charger slots and a specific charging speed.
 * For example, Station A might have 3 chargers at +10% per tick, while Station C
 * has 2 chargers at +30% per tick (faster but fewer slots).
 * 
 * The station manages its own list of currently-charging vehicles and handles
 * the charging logic each tick.
 */
public class ChargingStation {

    private final String name;
    private final int maxChargers;
    private final double chargeRatePerTick;       // Base percentage added per tick
    private final double renewableEnergyPercent;   // % of energy from renewable sources (0-100)
    private final List<Vehicle> currentVehicles;

    private double health = 100.0;
    private final double wearRate;
    private boolean offline = false;
    private int offlineTicksRemaining = 0;

    /**
     * Creates a new ChargingStation.
     *
     * @param name                   Station identifier (e.g., "Station A")
     * @param maxChargers            Maximum number of vehicles that can charge simultaneously
     * @param chargeRatePerTick      Battery percentage added to each vehicle per tick
     * @param renewableEnergyPercent Percentage of energy sourced from renewables (0-100)
     */
    public ChargingStation(String name, int maxChargers, double chargeRatePerTick,
                           double renewableEnergyPercent) {
        this.name = name;
        this.maxChargers = maxChargers;
        this.chargeRatePerTick = chargeRatePerTick;
        this.renewableEnergyPercent = Math.max(0, Math.min(100, renewableEnergyPercent));
        this.currentVehicles = new ArrayList<>();
        this.wearRate = chargeRatePerTick * 0.01;
    }

    /**
     * Returns true if there is at least one free charger slot.
     */
    public boolean hasFreeSlot() {
        return currentVehicles.size() < maxChargers;
    }

    /**
     * Returns the number of free charger slots.
     */
    public int freeSlots() {
        return maxChargers - currentVehicles.size();
    }

    /**
     * Adds a vehicle to this station for charging.
     * Sets the vehicle's state to CHARGING and assigns this station.
     *
     * @param vehicle The vehicle to add
     * @throws IllegalStateException if the station is full
     */
    public void addVehicle(Vehicle vehicle) {
        if (!hasFreeSlot()) {
            throw new IllegalStateException("Station " + name + " is full!");
        }
        vehicle.setState(Vehicle.State.CHARGING);
        vehicle.setAssignedStation(name);
        currentVehicles.add(vehicle);
    }

    /**
     * Charges all vehicles at this station by the station's charge rate.
     * Called once per simulation tick.
     */
    public void chargeAll() {
        chargeAll(1.0);
    }

    /**
     * Charges all vehicles with a grid-load multiplier applied.
     * During peak hours the multiplier is < 1.0, throttling charge speed
     * to reduce strain on the power grid.
     *
     * @param gridMultiplier 0.0–1.0 factor applied to chargeRatePerTick
     * @return total energy delivered this tick (in % units, summed across vehicles)
     */
    public double chargeAll(double gridMultiplier) {
        if (offline) {
            return 0.0;
        }
        double effectiveRate = chargeRatePerTick * Math.max(0.1, Math.min(1.0, gridMultiplier));
        double totalDelivered = 0;
        for (Vehicle v : currentVehicles) {
            double before = v.getBatteryPercent();
            v.charge(effectiveRate);
            totalDelivered += (v.getBatteryPercent() - before);
        }
        
        health -= wearRate * currentVehicles.size();
        if (health < 0) health = 0;
        
        return totalDelivered;
    }

    /**
     * Returns the effective charge rate after applying a grid multiplier.
     */
    public double getEffectiveRate(double gridMultiplier) {
        return chargeRatePerTick * Math.max(0.1, Math.min(1.0, gridMultiplier));
    }

    /**
     * Removes and returns all vehicles that have reached 100% charge.
     * Their state is set to COMPLETED by the Vehicle.charge() method.
     *
     * @return List of fully-charged vehicles that were removed
     */
    public List<Vehicle> removeFullyCharged() {
        List<Vehicle> completed = new ArrayList<>();
        Iterator<Vehicle> it = currentVehicles.iterator();
        while (it.hasNext()) {
            Vehicle v = it.next();
            if (v.isFullyCharged()) {
                v.setState(Vehicle.State.COMPLETED);
                completed.add(v);
                it.remove();
            }
        }
        return completed;
    }

    // --- Getters ---

    public String getName() {
        return name;
    }

    public int getMaxChargers() {
        return maxChargers;
    }

    public double getChargeRatePerTick() {
        return chargeRatePerTick;
    }

    public double getRenewableEnergyPercent() {
        return renewableEnergyPercent;
    }

    public List<Vehicle> getCurrentVehicles() {
        return new ArrayList<>(currentVehicles); // Defensive copy
    }

    public int getOccupiedSlots() {
        return currentVehicles.size();
    }

    public double getHealth() {
        return health;
    }

    public double getWearRate() {
        return wearRate;
    }

    public boolean isOffline() {
        return offline;
    }

    public void setOfflineFor(int ticks) {
        this.offline = true;
        this.offlineTicksRemaining = ticks;
    }

    public void tickOffline() {
        if (offline && offlineTicksRemaining > 0) {
            offlineTicksRemaining--;
            if (offlineTicksRemaining == 0) {
                offline = false;
                health = 100.0; // Restored health
            }
        }
    }

    @Override
    public String toString() {
        return String.format("Station{name='%s', chargers=%d/%d, rate=+%.0f%%/tick, renewable=%.0f%%}",
                name, currentVehicles.size(), maxChargers, chargeRatePerTick, renewableEnergyPercent);
    }
}

package evcharge.model;

/**
 * Represents an Electric Vehicle in the charging system.
 * 
 * Each vehicle has a unique ID, a current battery percentage, and a waiting time
 * that increases every simulation tick it spends in the queue (aging mechanism).
 * 
 * Priority is calculated as: (100 - batteryPercent) + waitingTime
 * Higher values mean higher urgency — vehicles with low battery or long waits
 * are scheduled first.
 */
public class Vehicle implements Comparable<Vehicle> {

    /**
     * Possible states a vehicle can be in during the simulation.
     */
    public enum State {
        WAITING,    // In the priority queue, waiting for a free charger
        CHARGING,   // Currently assigned to a station and charging
        COMPLETED   // Fully charged (battery >= 100%)
    }

    private final String id;
    private double batteryPercent;
    private int waitingTime;
    private State state;
    private String assignedStation; // Name of the station the vehicle is charging at
    private final int arrivalTick;  // The simulation tick when the vehicle was added

    /**
     * Creates a new Vehicle.
     *
     * @param id             Unique identifier (e.g., "EV-001")
     * @param batteryPercent Current battery level (0-99)
     * @param arrivalTick    The simulation tick at which this vehicle was added
     */
    public Vehicle(String id, double batteryPercent, int arrivalTick) {
        this.id = id;
        this.batteryPercent = Math.max(0, Math.min(99, batteryPercent));
        this.waitingTime = 0;
        this.state = State.WAITING;
        this.assignedStation = null;
        this.arrivalTick = arrivalTick;
    }

    /**
     * Calculates the scheduling priority for this vehicle.
     * Higher value = higher urgency = should be charged sooner.
     *
     * Formula: (100 - batteryPercent) + waitingTime
     *
     * The aging component (waitingTime) prevents starvation — even vehicles
     * with relatively high battery will eventually get priority if they've
     * been waiting long enough.
     */
    public double calculatePriority() {
        return (100.0 - batteryPercent) + waitingTime;
    }

    /**
     * Increments the waiting time by 1. Called every simulation tick
     * for vehicles still in the queue (aging mechanism).
     */
    public void incrementWaitingTime() {
        this.waitingTime++;
    }

    /**
     * Charges the vehicle by the given rate.
     *
     * @param chargeRate Percentage to add per tick (e.g., 20.0 means +20%)
     */
    public void charge(double chargeRate) {
        this.batteryPercent = Math.min(100.0, this.batteryPercent + chargeRate);
        if (this.batteryPercent >= 100.0) {
            this.state = State.COMPLETED;
        }
    }

    /**
     * Returns true if the vehicle is fully charged (>= 100%).
     */
    public boolean isFullyCharged() {
        return batteryPercent >= 100.0;
    }

    // --- Comparable: lower return value = higher priority in PriorityQueue (min-heap) ---
    @Override
    public int compareTo(Vehicle other) {
        // We want HIGHER priority value to come FIRST, so we reverse the comparison.
        return Double.compare(other.calculatePriority(), this.calculatePriority());
    }

    // --- Getters and Setters ---

    public String getId() {
        return id;
    }

    public double getBatteryPercent() {
        return batteryPercent;
    }

    public int getWaitingTime() {
        return waitingTime;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public String getAssignedStation() {
        return assignedStation;
    }

    public void setAssignedStation(String assignedStation) {
        this.assignedStation = assignedStation;
    }

    public int getArrivalTick() {
        return arrivalTick;
    }

    @Override
    public String toString() {
        return String.format("Vehicle{id='%s', battery=%.1f%%, wait=%d, state=%s, priority=%.1f}",
                id, batteryPercent, waitingTime, state, calculatePriority());
    }
}

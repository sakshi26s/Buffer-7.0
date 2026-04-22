package evcharge.engine;

import evcharge.model.Vehicle;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Priority-based scheduler for the EV charging system.
 * 
 * Uses a PriorityQueue (binary min-heap internally) to always select the
 * vehicle with the highest urgency. The priority formula is:
 * 
 *     priority = (100 - batteryPercent) + waitingTime
 * 
 * The "aging" mechanism increments the waitingTime of every queued vehicle
 * each tick, preventing starvation — even vehicles with relatively high
 * battery percentages will eventually rise to the top of the queue.
 * 
 * Data Structure: java.util.PriorityQueue
 * - Insertion: O(log n)
 * - Removal (poll): O(log n)
 * - Peek: O(1)
 * - Aging (incrementing all): O(n) — requires rebuild since priorities change
 */
public class PriorityScheduler {

    // PriorityQueue uses Vehicle's compareTo (Comparable) for ordering.
    // Since priorities change every tick (aging), we rebuild the queue after aging.
    private PriorityQueue<Vehicle> queue;

    public PriorityScheduler() {
        this.queue = new PriorityQueue<>();
    }

    /**
     * Adds a vehicle to the waiting queue.
     * O(log n) where n is the current queue size.
     *
     * @param vehicle The vehicle to enqueue
     */
    public void enqueue(Vehicle vehicle) {
        vehicle.setState(Vehicle.State.WAITING);
        queue.offer(vehicle);
    }

    /**
     * Removes and returns the highest-priority vehicle from the queue.
     * O(log n) for the heap removal.
     *
     * @return The vehicle with the highest priority, or null if empty
     */
    public Vehicle dequeue() {
        return queue.poll();
    }

    /**
     * Returns the highest-priority vehicle without removing it.
     * O(1) — the root of the min-heap.
     *
     * @return The top-priority vehicle, or null if empty
     */
    public Vehicle peek() {
        return queue.peek();
    }

    /**
     * Ages all vehicles in the queue by incrementing their waitingTime.
     * 
     * Since PriorityQueue doesn't automatically re-heapify when element
     * priorities change, we drain and rebuild the queue. This is O(n log n)
     * but ensures correct ordering.
     * 
     * This is a deliberate design choice: Java's PriorityQueue does not
     * support a "decrease-key" operation, so rebuilding is the safest approach.
     */
    public void ageAll() {
        List<Vehicle> vehicles = new ArrayList<>(queue);
        queue.clear();
        for (Vehicle v : vehicles) {
            v.incrementWaitingTime();
            queue.offer(v);
        }
    }

    /**
     * Returns a SNAPSHOT of the current queue contents, sorted by priority.
     * Does not modify the actual queue.
     *
     * @return Sorted list of waiting vehicles (highest priority first)
     */
    public List<Vehicle> getQueueSnapshot() {
        // PriorityQueue's iterator doesn't guarantee order, so we sort a copy.
        List<Vehicle> snapshot = new ArrayList<>(queue);
        snapshot.sort(null); // Uses Vehicle.compareTo
        return snapshot;
    }

    /**
     * Returns the number of vehicles currently waiting in the queue.
     */
    public int size() {
        return queue.size();
    }

    /**
     * Returns true if the queue is empty.
     */
    public boolean isEmpty() {
        return queue.isEmpty();
    }
}

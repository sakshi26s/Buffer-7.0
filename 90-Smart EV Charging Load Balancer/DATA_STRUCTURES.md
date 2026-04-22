# Data Structures Used — EV Charging Station Load Balancer

This document explains every key data structure used in the project, why it was chosen, and its time complexity characteristics.

---

## 1. PriorityQueue (Min-Heap)

**Location:** `PriorityScheduler.java`  
**Java Class:** `java.util.PriorityQueue<Vehicle>`

### Purpose
Maintains the waiting queue of vehicles ordered by **scheduling priority**. The vehicle with the highest urgency is always at the front.

### Priority Formula
```
priority = (100 - batteryPercent) + waitingTime
```
- Vehicles with **lower battery** have higher urgency
- **Aging** (incrementing `waitingTime` each tick) prevents starvation

### Why PriorityQueue?
| Operation | Time Complexity | Notes |
|-----------|----------------|-------|
| `offer()` (insert) | **O(log n)** | Binary heap sift-up |
| `poll()` (remove min) | **O(log n)** | Binary heap sift-down |
| `peek()` | **O(1)** | Direct root access |
| `size()` | **O(1)** | Maintained internally |

**Alternative considered:** Sorted `ArrayList` → O(n) insertion due to shifting. PriorityQueue wins for dynamic insert/remove workloads.

### Caveat: Queue Rebuild on Aging
Java's `PriorityQueue` does **not** support `decreaseKey()`. When priorities change (aging), we must **drain and rebuild** the queue:
```java
List<Vehicle> vehicles = new ArrayList<>(queue);
queue.clear();
for (Vehicle v : vehicles) {
    v.incrementWaitingTime();
    queue.offer(v);
}
```
**Cost:** O(n log n) per tick. Acceptable because:
- Queue sizes are small (typically < 100 vehicles)
- This runs once per tick, not in a tight loop

---

## 2. ArrayList

**Locations:**
- `ChargingStation.currentVehicles` — vehicles currently charging
- `SimulationEngine.completedVehicles` — finished vehicles
- `SimulationEngine.eventLog` — log messages

### Why ArrayList?
| Operation | Time Complexity |
|-----------|----------------|
| `add()` (append) | **O(1)** amortized |
| `get(index)` | **O(1)** |
| `remove(index)` | **O(n)** |
| `iterator().remove()` | **O(n)** |
| `size()` | **O(1)** |

Used for:
- **Station vehicles:** Small lists (2–5 chargers), fast iteration for charging, occasional removal of completed vehicles
- **Completed list:** Append-only, never searched — ideal for ArrayList
- **Event log:** Append-only with recent-slice access

**Alternative considered:** `LinkedList` → worse cache locality, no real benefit for small lists.

---

## 3. HashMap

**Location:** `SimulationEngine.getStatistics()`  
**Java Class:** `java.util.HashMap<String, Object>`

### Purpose
Returns simulation statistics as key-value pairs for JSON serialization.

| Operation | Time Complexity |
|-----------|----------------|
| `put()` | **O(1)** average |
| `get()` | **O(1)** average |
| `entrySet()` | **O(n)** |

### Why HashMap?
Statistics are accessed by key name in the API response builder. HashMap provides O(1) average lookup. The alternative `TreeMap` (sorted) is unnecessary since key order doesn't matter — JSON output order is irrelevant.

---

## 4. Comparable / Comparator (Ordering)

**Location:** `Vehicle implements Comparable<Vehicle>`

### Purpose
Defines the natural ordering for vehicles in the PriorityQueue:
```java
@Override
public int compareTo(Vehicle other) {
    return Double.compare(other.calculatePriority(), this.calculatePriority());
}
```

Note the **reversed comparison** — higher priority values should come first, but PriorityQueue is a min-heap, so we flip the order.

---

## 5. Enum (Vehicle.State)

**Location:** `Vehicle.State`

```java
public enum State { WAITING, CHARGING, COMPLETED }
```

### Why Enum?
- **Type safety:** Can't accidentally assign an invalid state
- **Memory efficient:** Single shared instances
- **Serializable:** `.name()` produces clean JSON output

---

## Summary Table

| Data Structure | Class | Location | Key Benefit |
|---------------|-------|----------|-------------|
| PriorityQueue | `java.util.PriorityQueue` | Scheduler | O(log n) insert/remove by priority |
| ArrayList | `java.util.ArrayList` | Station, Engine | O(1) append, fast iteration |
| HashMap | `java.util.HashMap` | Stats | O(1) key-value lookup |
| Comparable | Interface | Vehicle | Natural ordering for heap |
| Enum | `Vehicle.State` | Vehicle | Type-safe state machine |

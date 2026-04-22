# ⚡ EV Charging Station Load Balancer

A **time-based simulation** of a dynamic EV charging network with **priority-queue scheduling** and a real-time web dashboard. Vehicles arrive dynamically and are assigned to charging stations using a priority formula with an **aging mechanism** to prevent starvation.

![Java](https://img.shields.io/badge/Java-23-orange?style=flat-square)
![HTML/CSS/JS](https://img.shields.io/badge/UI-HTML%2FCSS%2FJS-blue?style=flat-square)
![License](https://img.shields.io/badge/License-MIT-green?style=flat-square)

---

## 🎯 Features

| Feature | Description |
|---------|-------------|
| **Priority Scheduling** | Vehicles prioritized by `(100 − battery%) + waitingTime` |
| **Aging Mechanism** | Prevents starvation — waiting vehicles gain priority each tick |
| **Multiple Stations** | Each station has its own charger count, speed, and renewable energy % |
| **⚡ Grid Load Awareness** | 24-hour power grid cycle — charges throttled during peak hours to reduce grid strain |
| **🌱 Carbon Footprint Tracker** | Tracks CO₂ emissions per station, compares smart vs naive scheduling |
| **Web Dashboard** | Real-time dark-themed visualization with battery bars and stats |
| **Statistics** | Avg wait time, throughput, peak queue, renewable energy %, CO₂ saved |
| **Event Log** | Scrolling log of every scheduling decision |
| **Zero Dependencies** | Uses Java's built-in `HttpServer` — no frameworks needed |

---

## 🏗️ Architecture

```
┌─────────────────────┐       REST API        ┌─────────────────────┐
│   Web Dashboard     │ ◄──────────────────►  │   Java Backend      │
│   (HTML/CSS/JS)     │   JSON over HTTP      │   (HttpServer)      │
│                     │                       │                     │
│ • Station cards     │  POST /api/vehicle    │ • Vehicle model     │
│ • Battery bars      │  POST /api/tick       │ • PriorityScheduler │
│ • Queue display     │  GET  /api/state      │ • SimulationEngine  │
│ • Event log         │                       │ • REST API handler  │
└─────────────────────┘                       └─────────────────────┘
```

---

## 📁 Project Structure

```
EV_CHARGE/
├── README.md                  # This file
├── DATA_STRUCTURES.md         # Data structures explanation
├── build.bat                  # Compile script
├── run.bat                    # Run script
├── src/
│   └── evcharge/
│       ├── App.java                      # Entry point
│       ├── model/
│       │   ├── Vehicle.java              # Vehicle entity
│       │   └── ChargingStation.java      # Station entity
│       ├── engine/
│       │   ├── PriorityScheduler.java    # Priority queue wrapper
│       │   └── SimulationEngine.java     # Simulation controller
│       └── server/
│           └── ApiServer.java            # HTTP server + REST API
└── web/
    ├── index.html             # Dashboard layout
    ├── style.css              # Dark theme styles
    └── app.js                 # Frontend logic
```

---

## 🚀 How to Build & Run

### Prerequisites
- **Java 17+** (tested with Java 23)

### Quick Start
```bash
# 1. Clone the repository
git clone https://github.com/YOUR_USERNAME/EV_CHARGE.git
cd EV_CHARGE

# 2. Build
build.bat

# 3. Run
run.bat

# 4. Open in browser
# Navigate to http://localhost:8080
```

### Manual Build
```bash
mkdir out
javac -d out src/evcharge/model/Vehicle.java src/evcharge/model/ChargingStation.java src/evcharge/engine/PriorityScheduler.java src/evcharge/engine/SimulationEngine.java src/evcharge/server/ApiServer.java src/evcharge/App.java
java -cp out evcharge.App
```

### Custom Port
```bash
run.bat 3000    # Runs on port 3000 instead of 8080
```

---

## 🎮 How to Use

1. **Open** `http://localhost:8080` in your browser
2. **Add vehicles** using the left panel (enter ID + battery %)
3. **Click "Next Time Step"** to advance the simulation
4. **Watch** vehicles get assigned to stations based on priority
5. **Use "Auto Play"** for continuous simulation

### Default Stations

| Station | Chargers | Speed | Renewable | Profile |
|---------|----------|-------|-----------|--------|
| Station A | 3 | +10%/tick | 80% solar | Eco-friendly, high capacity |
| Station B | 2 | +20%/tick | 50% mixed | Balanced |
| Station C | 2 | +30%/tick | 30% solar | Fast, limited slots |

---

## 📐 Scheduling Algorithm

### Priority Formula
```
priority = (100 − battery%) + waitingTime
```

### Tick Loop
Each simulation tick executes these steps in order:

1. **Grid Load** — Calculate current power grid load (24-hour cycle)
2. **Charge** — All vehicles at stations receive charge (throttled during peak hours)
3. **Track CO₂** — Record emissions based on each station's renewable energy mix
4. **Complete** — Remove vehicles that reached 100%
5. **Age** — Increment `waitingTime` for all queued vehicles
6. **Assign** — Place highest-priority vehicles into free charger slots (prefer fastest station)
7. **Log** — Record all decisions

### Grid Load Simulation
The power grid follows a 24-hour cycle (24 ticks = 1 day):
- **Off-Peak (0–5, 22–23):** Low load → full charging speed
- **Morning (6–7):** Rising load → 75% speed
- **Peak (8–11):** High load → 40% speed
- **Midday (12–16):** Moderate → 75% speed
- **Evening Peak (17–20):** Highest load → 30% speed

### Station Selection Strategy
When multiple stations have free slots, the **fastest station** (highest charge rate) is preferred. This greedy approach minimizes total charging time across the network.

---

## 📊 Data Structures

See [DATA_STRUCTURES.md](DATA_STRUCTURES.md) for detailed analysis including:
- PriorityQueue (min-heap) with aging
- ArrayList for station management
- HashMap for statistics
- Big-O complexity analysis

---

## 🧠 Key Concepts Demonstrated

- **Priority Queue** scheduling with dynamic priorities
- **Aging / anti-starvation** mechanism
- **Greedy algorithm** for station assignment
- **Grid load simulation** with time-of-day throttling
- **Carbon footprint tracking** with renewable energy optimization
- **REST API** design with Java's built-in HttpServer
- **MVC-style** separation (model / engine / server / UI)
- **Event-driven** web UI with fetch API

---

## 📄 License

MIT License — free to use, modify, and distribute.

/**
 * EV Charging Station Load Balancer — Frontend Logic
 * 
 * Communicates with the Java backend via REST API:
 *   GET  /api/state   → full simulation state
 *   POST /api/vehicle → add a vehicle
 *   POST /api/tick    → advance one time step
 */

// =========================================================================
// Global State
// =========================================================================
let autoPlayInterval = null;
let vehicleCounter = 1;
let previousLogLength = 0;

// =========================================================================
// API Communication
// =========================================================================

/**
 * Fetches the full simulation state from the backend.
 */
async function fetchState() {
    try {
        const res = await fetch('/api/state');
        const data = await res.json();
        renderState(data);
    } catch (err) {
        console.error('Failed to fetch state:', err);
    }
}

/**
 * Adds a vehicle via the API.
 */
async function addVehicle() {
    const idInput = document.getElementById('input-vehicle-id');
    const batteryInput = document.getElementById('input-battery');
    const feedback = document.getElementById('add-feedback');

    let id = idInput.value.trim();
    const battery = parseInt(batteryInput.value, 10);

    if (!id) {
        id = 'EV-' + String(vehicleCounter).padStart(3, '0');
    }

    try {
        const res = await fetch('/api/vehicle', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ id: id, battery: battery })
        });
        const data = await res.json();

        if (res.ok) {
            feedback.textContent = `✅ ${id} added (${battery}%)`;
            feedback.style.color = '#10b981';
            showToast(`Vehicle ${id} added to queue`, 'success');
            vehicleCounter++;
            idInput.value = '';
        } else {
            feedback.textContent = `❌ ${data.error || 'Failed'}`;
            feedback.style.color = '#ef4444';
        }

        // Clear feedback after 2s
        setTimeout(() => { feedback.textContent = ''; }, 2000);

        fetchState();
    } catch (err) {
        console.error('Failed to add vehicle:', err);
        feedback.textContent = '❌ Connection error';
        feedback.style.color = '#ef4444';
    }
}

/**
 * Quick-add a vehicle with a preset battery level.
 */
async function quickAdd(battery) {
    const id = 'EV-' + String(vehicleCounter).padStart(3, '0');

    try {
        await fetch('/api/vehicle', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ id: id, battery: battery })
        });
        vehicleCounter++;
        showToast(`${id} added (${battery}%)`, 'success');
        fetchState();
    } catch (err) {
        console.error('Quick add failed:', err);
    }
}

/**
 * Advances the simulation by one time step.
 */
async function advanceTick() {
    const btn = document.getElementById('btn-tick');
    btn.disabled = true;
    btn.style.opacity = '0.6';

    try {
        const res = await fetch('/api/tick', { method: 'POST' });
        const data = await res.json();
        renderState(data);
        showToast(`Time step ${data.currentTick} completed`, 'info');
    } catch (err) {
        console.error('Failed to advance tick:', err);
    } finally {
        btn.disabled = false;
        btn.style.opacity = '1';
    }
}

/**
 * Toggles auto-play mode (auto-advance every 1.5s).
 */
function toggleAutoPlay() {
    const btn = document.getElementById('btn-auto');
    const label = document.getElementById('auto-label');
    const icon = document.getElementById('auto-icon');

    if (autoPlayInterval) {
        clearInterval(autoPlayInterval);
        autoPlayInterval = null;
        btn.classList.remove('active');
        label.textContent = 'Auto Play';
        icon.textContent = '⏯';
    } else {
        autoPlayInterval = setInterval(advanceTick, 1500);
        btn.classList.add('active');
        label.textContent = 'Stop';
        icon.textContent = '⏸';
    }
}

// =========================================================================
// Rendering
// =========================================================================

/**
 * Renders the complete simulation state to the DOM.
 */
function renderState(state) {
    // Time
    document.getElementById('current-tick').textContent = state.currentTick;

    // Stats
    const stats = state.stats || {};
    document.getElementById('stat-charging').textContent = stats.totalCharging || 0;
    document.getElementById('stat-queue').textContent = stats.queueSize || 0;
    document.getElementById('stat-completed').textContent = stats.totalCompleted || 0;
    document.getElementById('stat-avg-wait').textContent = stats.avgWaitTime || '0.0';
    document.getElementById('stat-peak').textContent = stats.peakQueueDepth || 0;

    // Grid Load
    renderGridLoad(state);

    // Carbon Footprint
    renderCarbonFootprint(stats);

    // Network Health
    renderNetworkHealth(state);

    // Stations
    renderStations(state.stations || []);

    // Waiting Queue
    renderWaitingQueue(state.waitingQueue || []);

    // Completed
    renderCompleted(state.completedVehicles || []);

    // Event Log
    renderEventLog(state.eventLog || []);
}

/**
 * Renders the grid load indicator bar.
 */
function renderGridLoad(state) {
    const gridLoad = state.gridLoad || 0;
    const gridMult = state.gridMultiplier || 1;
    const hourOfDay = (state.currentTick || 0) % 24;
    const loadPct = Math.round(gridLoad * 100);
    const speedPct = Math.round(gridMult * 100);

    // Update meter bar
    const fill = document.getElementById('grid-load-fill');
    fill.style.width = loadPct + '%';
    fill.className = 'grid-load-fill' +
        (loadPct >= 80 ? ' high' : loadPct >= 50 ? ' medium' : '');

    // Update text values
    document.getElementById('grid-hour').textContent = `Hour ${hourOfDay}/24`;
    document.getElementById('grid-load-pct').textContent = loadPct + '%';
    document.getElementById('grid-speed-pct').textContent = speedPct + '%';

    // Grid status label
    const statusEl = document.getElementById('grid-status');
    if (loadPct >= 80) {
        statusEl.textContent = '🔴 Peak';
        statusEl.style.color = '#ef4444';
    } else if (loadPct >= 50) {
        statusEl.textContent = '🟡 Moderate';
        statusEl.style.color = '#f59e0b';
    } else {
        statusEl.textContent = '🟢 Off-Peak';
        statusEl.style.color = '#10b981';
    }
}

/**
 * Renders the carbon footprint tracking panel.
 */
function renderCarbonFootprint(stats) {
    const smartCO2 = stats.totalCO2 || 0;
    const naiveCO2 = stats.naiveCO2 || 0;
    const co2Saved = stats.co2Saved || 0;
    const renewablePct = stats.renewablePercent || 0;
    const totalEnergy = stats.totalEnergyDelivered || 0;

    document.getElementById('carbon-smart').textContent = smartCO2.toFixed(2) + ' kg';
    document.getElementById('carbon-naive').textContent = naiveCO2.toFixed(2) + ' kg';
    document.getElementById('carbon-saved').textContent = co2Saved.toFixed(2) + ' kg';
    document.getElementById('renewable-pct').textContent = renewablePct.toFixed(1) + '%';
    document.getElementById('total-energy').textContent = totalEnergy.toFixed(1) + ' units';

    // CO2 comparison bars — scale relative to the larger value
    const maxCO2 = Math.max(smartCO2, naiveCO2, 0.01);
    document.getElementById('co2-bar-smart').style.width = (smartCO2 / maxCO2 * 100) + '%';
    document.getElementById('co2-bar-naive').style.width = (naiveCO2 / maxCO2 * 100) + '%';
}

/**
 * Renders the Network Health summary bar.
 */
function renderNetworkHealth(state) {
    const stations = state.stations || [];
    let totalHealth = 0;
    let totalMaxCapacity = 0;
    let offlineCapacity = 0;

    stations.forEach(st => {
        const h = st.health !== undefined ? st.health : 100.0;
        totalHealth += h;
        totalMaxCapacity += st.maxChargers;
        if (st.offline) {
            offlineCapacity += st.maxChargers;
        }
    });

    const avgHealth = stations.length > 0 ? (totalHealth / stations.length) : 100.0;
    const offlinePct = totalMaxCapacity > 0 ? (offlineCapacity / totalMaxCapacity * 100) : 0;
    
    document.getElementById('avg-health-pct').textContent = avgHealth.toFixed(1) + '%';
    document.getElementById('val-downtime-ticks').textContent = (state.stats && state.stats.totalDowntimeTicks) || 0;
    document.getElementById('val-offline-capacity').textContent = offlinePct.toFixed(1) + '%';

    const alertEl = document.getElementById('capacity-alert');
    if (offlinePct > 50) {
        alertEl.style.display = 'inline-block';
    } else {
        alertEl.style.display = 'none';
    }
}

/**
 * Renders all charging station cards.
 */
function renderStations(stations) {
    const grid = document.getElementById('stations-grid');
    grid.innerHTML = '';

    stations.forEach((station, index) => {
        const card = document.createElement('div');
        const cssClass = ['station-a', 'station-b', 'station-c'][index] || 'station-a';
        card.className = `station-card ${cssClass}`;

        // Charger slot dots
        let slotsHtml = '<div class="charger-slots">';
        for (let i = 0; i < station.maxChargers; i++) {
            const occupied = i < station.occupiedSlots ? 'occupied' : '';
            slotsHtml += `<div class="slot-dot ${occupied}"></div>`;
        }
        slotsHtml += '</div>';

        // Vehicles
        let vehiclesHtml = '';
        if (station.vehicles && station.vehicles.length > 0) {
            station.vehicles.forEach(v => {
                vehiclesHtml += renderStationVehicle(v);
            });
        } else {
            vehiclesHtml = '<div class="station-empty">No vehicles charging</div>';
        }

        // Effective rate (after grid throttling)
        const effectiveRate = station.effectiveRate || station.chargeRate;
        const renewPct = station.renewablePercent || 0;
        const health = station.health !== undefined ? station.health : 100.0;
        const offline = station.offline || false;
        const ticksToFailure = (station.ticksToFailure === 2147483647) ? '∞' : station.ticksToFailure;

        if (offline) {
            cssClass += ' offline';
        }

        const getHealthColor = (h) => {
            if (h >= 80) return 'linear-gradient(90deg, #10b981, #34d399)';
            if (h >= 40) return 'linear-gradient(90deg, #eab308, #facc15)';
            return 'linear-gradient(90deg, #ef4444, #f87171)';
        };

        card.className = `station-card ${cssClass}`;

        card.innerHTML = `
            <div class="station-header">
                <span class="station-name">${station.name} ${offline ? '<span class="badge badge-error" style="margin-left:8px;">MAINTENANCE</span>' : ''}</span>
                <div class="station-meta">
                    <span class="station-tag tag-renewable">🌱${renewPct}%</span>
                    <span class="station-tag tag-speed">⚡${effectiveRate}%/tick</span>
                    <span class="station-tag tag-slots">${station.occupiedSlots}/${station.maxChargers}</span>
                </div>
            </div>
            <div class="health-bar-container">
                <div class="health-bar-fill" style="width:${health}%; background:${getHealthColor(health)};"></div>
            </div>
            <div class="station-failure-text">
                Estimated Ticks to Failure: <strong>${ticksToFailure}</strong>
            </div>
            ${slotsHtml}
            ${vehiclesHtml}
        `;

        grid.appendChild(card);
    });
}

/**
 * Renders a single vehicle inside a station card.
 */
function renderStationVehicle(vehicle) {
    const pct = Math.round(vehicle.battery);
    const color = getBatteryColor(pct);

    return `
        <div class="station-vehicle">
            <span class="vehicle-id-badge">${vehicle.id}</span>
            <div class="battery-bar-container">
                <div class="battery-bar-fill" style="width:${pct}%; background:${color};"></div>
            </div>
            <span class="battery-percent" style="color:${color}">${pct}%</span>
        </div>
    `;
}

/**
 * Renders the waiting queue.
 */
function renderWaitingQueue(queue) {
    const list = document.getElementById('waiting-queue-list');
    const count = document.getElementById('queue-count');
    count.textContent = queue.length;

    if (queue.length === 0) {
        list.innerHTML = '<div class="empty-state">No vehicles waiting</div>';
        return;
    }

    list.innerHTML = '';
    queue.forEach(v => {
        const item = document.createElement('div');
        item.className = 'queue-item';
        item.innerHTML = `
            <span class="vehicle-id-badge">${v.id}</span>
            <span class="priority-badge">P:${v.priority.toFixed(1)}</span>
            <span class="item-detail">🔋${Math.round(v.battery)}% · ⏳${v.waitingTime}t</span>
        `;
        list.appendChild(item);
    });
}

/**
 * Renders the completed vehicles list.
 */
function renderCompleted(completed) {
    const list = document.getElementById('completed-list');
    const count = document.getElementById('completed-count');
    count.textContent = completed.length;

    if (completed.length === 0) {
        list.innerHTML = '<div class="empty-state">No vehicles completed yet</div>';
        return;
    }

    list.innerHTML = '';
    // Show most recent first
    [...completed].reverse().forEach(v => {
        const item = document.createElement('div');
        item.className = 'completed-item';
        item.innerHTML = `
            <span class="vehicle-id-badge">${v.id}</span>
            <span class="item-detail">waited ${v.waitingTime}t · arr t=${v.arrivalTick}</span>
        `;
        list.appendChild(item);
    });
}

/**
 * Renders the event log (newest at top).
 */
function renderEventLog(log) {
    const logEl = document.getElementById('event-log');
    
    // Only update if log changed
    if (log.length === previousLogLength) return;
    previousLogLength = log.length;

    logEl.innerHTML = '';
    // Show newest first, limit to last 50
    const recent = log.slice(-50).reverse();
    recent.forEach(entry => {
        const div = document.createElement('div');
        div.className = 'log-entry';
        div.textContent = entry;
        logEl.appendChild(div);
    });
}

// =========================================================================
// Utilities
// =========================================================================

/**
 * Returns a gradient color string based on battery percentage.
 * Red (0%) → Orange (25%) → Yellow (50%) → Green (75-100%)
 */
function getBatteryColor(pct) {
    if (pct >= 80) return 'linear-gradient(90deg, #10b981, #34d399)';
    if (pct >= 60) return 'linear-gradient(90deg, #22c55e, #4ade80)';
    if (pct >= 40) return 'linear-gradient(90deg, #eab308, #facc15)';
    if (pct >= 20) return 'linear-gradient(90deg, #f97316, #fb923c)';
    return 'linear-gradient(90deg, #ef4444, #f87171)';
}

/**
 * Shows a toast notification.
 */
function showToast(message, type = 'info') {
    const container = document.getElementById('toast-container');
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.textContent = message;
    container.appendChild(toast);

    setTimeout(() => {
        toast.style.opacity = '0';
        toast.style.transform = 'translateX(30px)';
        toast.style.transition = 'all 0.3s ease-in';
        setTimeout(() => toast.remove(), 300);
    }, 2500);
}

/**
 * Handle Enter key on the vehicle ID input.
 */
document.addEventListener('DOMContentLoaded', () => {
    document.getElementById('input-vehicle-id').addEventListener('keypress', (e) => {
        if (e.key === 'Enter') addVehicle();
    });

    // Fetch initial state
    fetchState();
});

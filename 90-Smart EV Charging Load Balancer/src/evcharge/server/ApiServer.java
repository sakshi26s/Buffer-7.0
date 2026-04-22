package evcharge.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import evcharge.engine.SimulationEngine;
import evcharge.model.ChargingStation;
import evcharge.model.Vehicle;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.List;
import java.util.Map;

/**
 * Lightweight REST API server using Java's built-in HttpServer.
 * 
 * No external dependencies required — uses com.sun.net.httpserver which
 * ships with the JDK.
 * 
 * Endpoints:
 *   GET  /              → serves index.html
 *   GET  /style.css     → serves stylesheet
 *   GET  /app.js        → serves JavaScript
 *   GET  /api/state     → returns full simulation state as JSON
 *   POST /api/vehicle   → adds a vehicle (body: {"id":"...", "battery":30})
 *   POST /api/tick      → advances simulation by one time step
 */
public class ApiServer {

    private final SimulationEngine engine;
    private final int port;
    private final String webRoot;

    /**
     * @param engine  The simulation engine instance
     * @param port    Port to listen on (e.g., 8080)
     * @param webRoot Absolute path to the web/ directory
     */
    public ApiServer(SimulationEngine engine, int port, String webRoot) {
        this.engine = engine;
        this.port = port;
        this.webRoot = webRoot;
    }

    /**
     * Starts the HTTP server and registers all route handlers.
     */
    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // API endpoints
        server.createContext("/api/state", new StateHandler());
        server.createContext("/api/vehicle", new AddVehicleHandler());
        server.createContext("/api/tick", new TickHandler());

        // Static file serving (HTML, CSS, JS)
        server.createContext("/", new StaticFileHandler());

        server.setExecutor(null); // Use default executor
        server.start();

        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║   EV Charging Station Load Balancer          ║");
        System.out.println("║   Server running at http://localhost:" + port + "    ║");
        System.out.println("╚══════════════════════════════════════════════╝");
    }

    // =========================================================================
    // GET /api/state — Returns the full simulation state as JSON
    // =========================================================================
    private class StateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }
            setCorsHeaders(exchange);
            String json = buildStateJson();
            sendResponse(exchange, 200, json);
        }
    }

    // =========================================================================
    // POST /api/vehicle — Adds a new vehicle
    // Body: {"id": "EV-001", "battery": 30}
    // =========================================================================
    private class AddVehicleHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                setCorsHeaders(exchange);
                sendResponse(exchange, 204, "");
                return;
            }
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }
            setCorsHeaders(exchange);

            // Parse the JSON body manually (no external JSON library)
            String body = new String(exchange.getRequestBody().readAllBytes());
            String id = extractJsonString(body, "id");
            double battery = extractJsonNumber(body, "battery");

            if (id == null || id.isEmpty()) {
                sendResponse(exchange, 400, "{\"error\":\"Missing vehicle ID\"}");
                return;
            }

            synchronized (engine) {
                engine.addVehicle(id, battery);
            }

            sendResponse(exchange, 200, "{\"status\":\"ok\",\"message\":\"Vehicle " + id + " added\"}");
        }
    }

    // =========================================================================
    // POST /api/tick — Advances the simulation by one time step
    // =========================================================================
    private class TickHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                setCorsHeaders(exchange);
                sendResponse(exchange, 204, "");
                return;
            }
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }
            setCorsHeaders(exchange);

            synchronized (engine) {
                engine.tick();
            }

            String json = buildStateJson();
            sendResponse(exchange, 200, json);
        }
    }

    // =========================================================================
    // Static file handler — serves web/ directory
    // =========================================================================
    private class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if ("/".equals(path)) path = "/index.html";

            // Determine content type
            String contentType = "text/plain";
            if (path.endsWith(".html")) contentType = "text/html";
            else if (path.endsWith(".css")) contentType = "text/css";
            else if (path.endsWith(".js")) contentType = "application/javascript";
            else if (path.endsWith(".png")) contentType = "image/png";
            else if (path.endsWith(".svg")) contentType = "image/svg+xml";

            Path filePath = Paths.get(webRoot, path);
            if (Files.exists(filePath) && !Files.isDirectory(filePath)) {
                byte[] data = Files.readAllBytes(filePath);
                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.sendResponseHeaders(200, data.length);
                exchange.getResponseBody().write(data);
                exchange.getResponseBody().close();
            } else {
                sendResponse(exchange, 404, "File not found: " + path);
            }
        }
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    /**
     * Builds the complete simulation state as a JSON string.
     * Hand-built JSON to avoid any external library dependency.
     */
    private String buildStateJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");

        synchronized (engine) {
            // Current tick
            sb.append("\"currentTick\":").append(engine.getCurrentTick());

            // Grid load info
            sb.append(",\"gridLoad\":").append(String.format("%.2f", engine.getCurrentGridLoad()));
            sb.append(",\"gridMultiplier\":").append(String.format("%.2f", engine.getGridMultiplier()));

            // Stations
            sb.append(",\"stations\":[");
            List<ChargingStation> stations = engine.getStations();
            for (int i = 0; i < stations.size(); i++) {
                if (i > 0) sb.append(",");
                ChargingStation st = stations.get(i);
                sb.append("{\"name\":\"").append(escapeJson(st.getName())).append("\"");
                sb.append(",\"maxChargers\":").append(st.getMaxChargers());
                sb.append(",\"chargeRate\":").append(st.getChargeRatePerTick());
                sb.append(",\"occupiedSlots\":").append(st.getOccupiedSlots());
                sb.append(",\"freeSlots\":").append(st.freeSlots());
                sb.append(",\"renewablePercent\":").append(st.getRenewableEnergyPercent());
                sb.append(",\"effectiveRate\":").append(String.format("%.1f", st.getEffectiveRate(engine.getGridMultiplier())));
                sb.append(",\"health\":").append(String.format("%.1f", st.getHealth()));
                sb.append(",\"offline\":").append(st.isOffline());
                sb.append(",\"ticksToFailure\":").append(engine.getMaintenanceAgent().predictTicksToFailure(st));

                // Vehicles at this station
                sb.append(",\"vehicles\":[");
                List<Vehicle> vehicles = st.getCurrentVehicles();
                for (int j = 0; j < vehicles.size(); j++) {
                    if (j > 0) sb.append(",");
                    appendVehicleJson(sb, vehicles.get(j));
                }
                sb.append("]}");
            }
            sb.append("]");

            // Waiting queue
            sb.append(",\"waitingQueue\":[");
            List<Vehicle> queue = engine.getWaitingQueue();
            for (int i = 0; i < queue.size(); i++) {
                if (i > 0) sb.append(",");
                appendVehicleJson(sb, queue.get(i));
            }
            sb.append("]");

            // Completed vehicles
            sb.append(",\"completedVehicles\":[");
            List<Vehicle> completed = engine.getCompletedVehicles();
            for (int i = 0; i < completed.size(); i++) {
                if (i > 0) sb.append(",");
                appendVehicleJson(sb, completed.get(i));
            }
            sb.append("]");

            // Event log (last 100 entries)
            sb.append(",\"eventLog\":[");
            List<String> log = engine.getEventLog();
            int start = Math.max(0, log.size() - 100);
            for (int i = start; i < log.size(); i++) {
                if (i > start) sb.append(",");
                sb.append("\"").append(escapeJson(log.get(i))).append("\"");
            }
            sb.append("]");

            // Statistics
            sb.append(",\"stats\":");
            Map<String, Object> stats = engine.getStatistics();
            sb.append("{");
            int idx = 0;
            for (Map.Entry<String, Object> e : stats.entrySet()) {
                if (idx > 0) sb.append(",");
                sb.append("\"").append(e.getKey()).append("\":");
                if (e.getValue() instanceof String) {
                    sb.append("\"").append(escapeJson(e.getValue().toString())).append("\"");
                } else {
                    sb.append(e.getValue());
                }
                idx++;
            }
            sb.append("}");
        }

        sb.append("}");
        return sb.toString();
    }

    private void appendVehicleJson(StringBuilder sb, Vehicle v) {
        sb.append("{\"id\":\"").append(escapeJson(v.getId())).append("\"");
        sb.append(",\"battery\":").append(String.format("%.1f", v.getBatteryPercent()));
        sb.append(",\"waitingTime\":").append(v.getWaitingTime());
        sb.append(",\"state\":\"").append(v.getState().name()).append("\"");
        sb.append(",\"priority\":").append(String.format("%.1f", v.calculatePriority()));
        if (v.getAssignedStation() != null) {
            sb.append(",\"assignedStation\":\"").append(escapeJson(v.getAssignedStation())).append("\"");
        }
        sb.append(",\"arrivalTick\":").append(v.getArrivalTick());
        sb.append("}");
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Extracts a string value from a simple JSON object.
     * Example: extractJsonString("{\"id\":\"EV-001\"}", "id") → "EV-001"
     */
    private String extractJsonString(String json, String key) {
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx == -1) return null;
        idx = json.indexOf(":", idx);
        if (idx == -1) return null;
        idx = json.indexOf("\"", idx + 1);
        if (idx == -1) return null;
        int end = json.indexOf("\"", idx + 1);
        if (end == -1) return null;
        return json.substring(idx + 1, end);
    }

    /**
     * Extracts a numeric value from a simple JSON object.
     * Example: extractJsonNumber("{\"battery\":30}", "battery") → 30.0
     */
    private double extractJsonNumber(String json, String key) {
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx == -1) return 0;
        idx = json.indexOf(":", idx);
        if (idx == -1) return 0;
        // Skip whitespace
        int start = idx + 1;
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '\t')) {
            start++;
        }
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '.' || json.charAt(end) == '-')) {
            end++;
        }
        try {
            return Double.parseDouble(json.substring(start, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void setCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] bytes = response.getBytes();
        exchange.sendResponseHeaders(statusCode, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }
}

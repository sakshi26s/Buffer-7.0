package evcharge;

import evcharge.engine.SimulationEngine;
import evcharge.model.ChargingStation;
import evcharge.server.ApiServer;

import java.io.File;

/**
 * Application entry point.
 * 
 * Sets up the default charging stations and starts the HTTP server.
 * Open http://localhost:8080 in your browser to interact with the simulation.
 */
public class App {

    public static void main(String[] args) throws Exception {
        // --- Create the simulation engine ---
        SimulationEngine engine = new SimulationEngine();

        // --- Configure default charging stations ---
        // Station A: 3 chargers, slow speed (+10%/tick), 80% solar     — eco-friendly, high capacity
        // Station B: 2 chargers, medium speed (+20%/tick), 50% mixed   — balanced
        // Station C: 2 chargers, fast speed (+30%/tick), 30% solar     — fast but less green
        engine.addStation(new ChargingStation("Station A", 3, 10.0, 80.0));
        engine.addStation(new ChargingStation("Station B", 2, 20.0, 50.0));
        engine.addStation(new ChargingStation("Station C", 2, 30.0, 30.0));

        // --- Determine the web/ directory path ---
        // Works both when running from project root and from build output
        String webRoot = findWebRoot();
        System.out.println("Serving web files from: " + webRoot);

        // --- Start the API server ---
        int port = 8080;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number, using default 8080");
            }
        }

        ApiServer server = new ApiServer(engine, port, webRoot);
        server.start();
    }

    /**
     * Finds the web/ directory relative to the project root.
     * Searches upward from the current working directory.
     */
    private static String findWebRoot() {
        // Try current directory first
        File webDir = new File("web");
        if (webDir.exists() && webDir.isDirectory()) {
            return webDir.getAbsolutePath();
        }

        // Try parent directory (in case running from src/ or out/)
        webDir = new File("../web");
        if (webDir.exists() && webDir.isDirectory()) {
            return webDir.getAbsolutePath();
        }

        // Fallback: use current directory
        System.err.println("WARNING: 'web/' directory not found. Static files won't be served.");
        return new File(".").getAbsolutePath();
    }
}

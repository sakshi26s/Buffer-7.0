@echo off
REM =========================================
REM  EV Charging Station Load Balancer
REM  Build Script — Compiles all Java sources
REM =========================================

echo.
echo [BUILD] Compiling EV Charging Station Load Balancer...
echo.

if not exist "out" mkdir out

@REM javac -d out src/evcharge/model/Vehicle.java ^
@REM               src/evcharge/model/ChargingStation.java ^
@REM               src/evcharge/engine/PriorityScheduler.java ^
@REM               src/evcharge/engine/SimulationEngine.java ^
@REM               src/evcharge/server/ApiServer.java ^
@REM               src/evcharge/App.java

javac -d out src/evcharge/model/*.java src/evcharge/engine/*.java src/evcharge/server/*.java src/evcharge/App.java
if %ERRORLEVEL% EQU 0 (
    echo.
    echo [BUILD] ✅ Compilation successful!
    echo [BUILD] Output directory: out/
    echo [BUILD] Run 'run.bat' to start the server.
    echo.
) else (
    echo.
    echo [BUILD] ❌ Compilation FAILED. See errors above.
    echo.
    exit /b 1
)

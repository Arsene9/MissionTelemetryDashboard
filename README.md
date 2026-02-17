# Mission Telemetry Dashboard

A Java Swing application for monitoring telemetry from lunar, Mars, and aerospace missions. Supports real-time and historical views with live data from ISS, aircraft (ADS-B), and ships (AIS).

![Java](https://img.shields.io/badge/Java-8+-orange)
![Swing](https://img.shields.io/badge/UI-Swing-4A90D9)

## Features

- **Vehicle selection** — ISS, OpenSky aircraft, AIS ships, simulated demo, and more
- **Data sources** — ISS TLE (Celestrak), OpenSky Network, AIS Hub, simulated data
- **Real-time view** — Live metric tiles (Battery, Temperature, Signal, Velocity)
- **Historical view** — Rolling-month graphs with time axis, tooltips, and Y-axis labels
- **CSV export** — Export historical telemetry data
- **Dark theme** — Modern UI with rounded corners and hover effects
- **Status alerts** — Visual indicators for critical conditions

## Screenshots

The dashboard displays metrics in card-style tiles with a responsive layout. Switch between Real-Time and Historical modes to view live values or trend graphs.

## Requirements

- Java 8 or later
- NetBeans IDE (optional, for development)

## Building

### NetBeans
1. Open the project in NetBeans.
2. Choose **Run** → **Clean and Build**.

### Command line (Ant)
```bash
ant clean jar
```

## Running

### From NetBeans
Right-click the project → **Run**.

### Standalone JAR
```bash
java -jar dist/MissionTelemetryDashboard.jar
```

Or use the batch launcher (Windows):
```
RunMissionTelemetryDashboard.bat
```

## Project Structure

```
MissionTelemetryDashboard/
├── src/missiontelemetrydashboard/
│   ├── MissionTelemetryDashboard.java  # Main application
│   ├── MissionTelemetryDashboardApp.java  # Entry point
│   └── icons/
│       └── refresh.png
├── nbproject/           # NetBeans project config
├── build.xml            # Ant build script
├── manifest.mf          # JAR manifest
├── RunMissionTelemetryDashboard.bat
└── README.md
```

## Data Sources

| Source        | Type     | Description                    |
|---------------|----------|--------------------------------|
| ISS TLE       | Real-time| Celestrak Two-Line Element sets|
| OpenSky       | Real-time| ADS-B aircraft state vectors   |
| AIS Hub       | Real-time| Ship position data (API key)   |
| Simulated     | Both     | Demo data for testing          |

## License

See repository for license details.

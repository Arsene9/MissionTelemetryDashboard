package missiontelemetrydashboard;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.text.DecimalFormat;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.Timer;

public class MissionTelemetryDashboard extends JFrame {
    // Formatting and history sampling.
    private static final DecimalFormat FORMAT = new DecimalFormat("0.00");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final long HISTORY_STEP_MS = 60L * 60L * 1000L;
    // Optional AIS Hub username; leave blank to disable AIS polling.
    private static String AIS_USERNAME = "";

    // UI controls.
    private final Random random = new Random();
    private final JComboBox<VehicleOption> vehicleSelect = new JComboBox<>();
    private final JComboBox<DataSource> sourceSelect = new JComboBox<>();
    private final JButton refreshButton = new JButton("Refresh");
    private final JButton historicalButton = new JButton("Historical");
    private final JButton realtimeButton = new JButton("Real-Time");
    private final JButton exportButton = new JButton("Export CSV");
    private final JLabel titleLabel = new JLabel("Lunar/Mars Mission Control");
    private final JLabel dataModeLabel = new JLabel("Real-Time Data");
    private final JLabel batteryValue = new JLabel();
    private final JLabel tempValue = new JLabel();
    private final JLabel signalValue = new JLabel();
    private final JLabel velocityValue = new JLabel();
    private final JLabel statusValue = new JLabel();
    private final JTextArea alertsArea = new JTextArea();

    // Live telemetry values (used by real-time tiles and history updates).
    private double battery = 100.0;
    private double temp = 22.0;
    private double signal = 35.0;
    private double velocity = 1200.0;

    // Historical series (one point per hour, rolling month).
    private final TelemetrySeries batteryHistory = new TelemetrySeries(720, HISTORY_STEP_MS);
    private final TelemetrySeries tempHistory = new TelemetrySeries(720, HISTORY_STEP_MS);
    private final TelemetrySeries signalHistory = new TelemetrySeries(720, HISTORY_STEP_MS);
    private final TelemetrySeries velocityHistory = new TelemetrySeries(720, HISTORY_STEP_MS);

    // Graph panels for historical mode.
    private final GraphPanel batteryGraph = new GraphPanel("Battery Voltage (V)", batteryHistory);
    private final GraphPanel tempGraph = new GraphPanel("Thermal Temp (C)", tempHistory);
    private final GraphPanel signalGraph = new GraphPanel("Signal Strength (dB)", signalHistory);
    private final GraphPanel velocityGraph = new GraphPanel("Velocity (m/s)", velocityHistory);

    // Center panel swaps between tiles and graphs.
    private final CardLayout centerLayout = new CardLayout();
    private final JPanel centerPanel = new JPanel(centerLayout);
    private final JPanel metricsPanel = new JPanel(new GridLayout(2, 2, 12, 12));
    private final JPanel graphsPanel = new JPanel(new GridLayout(2, 2, 12, 12));

    // Current mode and data source state.
    private DataMode currentMode = DataMode.REALTIME;
    private DataSource currentSource = DataSource.SIMULATED;
    private final ScheduledExecutorService dataExecutor = Executors.newSingleThreadScheduledExecutor();
    private volatile DataSnapshot latestSnapshot;

    private boolean batteryCritical;
    private boolean tempHigh;
    private boolean signalLow;

    public MissionTelemetryDashboard() {
        super("Mission Telemetry Dashboard");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(12, 12));
        add(buildHeader(), BorderLayout.NORTH);
        add(buildCenterPanel(), BorderLayout.CENTER);
        add(buildAlerts(), BorderLayout.EAST);
        setMinimumSize(new Dimension(900, 520));
        setLocationRelativeTo(null);
        seedHistory();
        configureSources();
        configureVehicles();
        startTelemetryStream();
        startDataPolling();
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(12, 12));
        header.setBorder(BorderFactory.createEmptyBorder(12, 12, 0, 12));

        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 22));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);

        statusValue.setFont(new Font("SansSerif", Font.BOLD, 16));
        statusValue.setHorizontalAlignment(SwingConstants.CENTER);
        statusValue.setText("STATUS: NOMINAL");
        statusValue.setForeground(new Color(0, 128, 0));

        dataModeLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        dataModeLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel titleStack = new JPanel(new GridLayout(3, 1, 0, 4));
        titleStack.add(titleLabel);
        titleStack.add(dataModeLabel);
        titleStack.add(statusValue);

        JPanel center = new JPanel(new BorderLayout(12, 12));
        center.add(buildTopControls(), BorderLayout.NORTH);
        center.add(titleStack, BorderLayout.CENTER);

        header.add(center, BorderLayout.CENTER);
        return header;
    }

    private JPanel buildTopControls() {
        JPanel controls = new JPanel(new BorderLayout(8, 8));

        JPanel leftControls = new JPanel(new GridLayout(1, 3, 8, 8));
        vehicleSelect.setPrototypeDisplayValue(new VehicleOption("Select Vehicle", "Type", DataAvailability.BOTH));
        sourceSelect.setPrototypeDisplayValue(DataSource.SIMULATED);
        JLabel vehicleLabel = new JLabel("Vehicle");
        JLabel sourceLabel = new JLabel("Sources");
        vehicleLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        sourceLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));

        JPanel vehiclePanelBox = new JPanel(new BorderLayout(0, 0));
        vehiclePanelBox.add(vehicleLabel, BorderLayout.WEST);
        vehiclePanelBox.add(vehicleSelect, BorderLayout.CENTER);

        JPanel sourcePanelBox = new JPanel(new BorderLayout(0, 0));
        sourcePanelBox.add(sourceLabel, BorderLayout.WEST);
        sourcePanelBox.add(sourceSelect, BorderLayout.CENTER);

        leftControls.add(vehiclePanelBox);
        leftControls.add(refreshButton);
        leftControls.add(sourcePanelBox);

        JPanel rightControls = new JPanel(new GridLayout(1, 3, 8, 8));
        rightControls.add(historicalButton);
        rightControls.add(realtimeButton);
        rightControls.add(exportButton);

        controls.add(leftControls, BorderLayout.CENTER);
        controls.add(rightControls, BorderLayout.EAST);
        return controls;
    }

    private JPanel buildCenterPanel() {
        metricsPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        graphsPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        metricsPanel.add(buildMetricPanel("Battery Voltage (V)", batteryValue));
        metricsPanel.add(buildMetricPanel("Thermal Temp (C)", tempValue));
        metricsPanel.add(buildMetricPanel("Signal Strength (dB)", signalValue));
        metricsPanel.add(buildMetricPanel("Velocity (m/s)", velocityValue));

        graphsPanel.add(batteryGraph);
        graphsPanel.add(tempGraph);
        graphsPanel.add(signalGraph);
        graphsPanel.add(velocityGraph);

        updateMetricLabels();
        centerPanel.add(metricsPanel, DataMode.REALTIME.name());
        centerPanel.add(graphsPanel, DataMode.HISTORICAL.name());
        centerLayout.show(centerPanel, DataMode.REALTIME.name());
        return centerPanel;
    }

    private JPanel buildMetricPanel(String label, JLabel valueLabel) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(210, 210, 210)),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)
        ));

        JLabel title = new JLabel(label);
        title.setFont(new Font("SansSerif", Font.PLAIN, 14));

        valueLabel.setFont(new Font("SansSerif", Font.BOLD, 28));
        valueLabel.setHorizontalAlignment(SwingConstants.CENTER);

        panel.add(title, BorderLayout.NORTH);
        panel.add(valueLabel, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildAlerts() {
        JPanel alerts = new JPanel(new BorderLayout());
        alerts.setBorder(BorderFactory.createEmptyBorder(12, 0, 12, 12));
        alerts.setPreferredSize(new Dimension(280, 0));

        JLabel title = new JLabel("Alerts");
        title.setFont(new Font("SansSerif", Font.BOLD, 16));

        alertsArea.setEditable(false);
        alertsArea.setLineWrap(true);
        alertsArea.setWrapStyleWord(true);
        alertsArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        alertsArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JScrollPane scrollPane = new JScrollPane(alertsArea);

        alerts.add(title, BorderLayout.NORTH);
        alerts.add(scrollPane, BorderLayout.CENTER);
        return alerts;
    }

    private void startTelemetryStream() {
        // UI update timer; simulates telemetry ticks and refreshes views.
        Timer timer = new Timer(1000, new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                updateTelemetry();
            }
        });
        timer.start();
    }

    private void updateTelemetry() {
        battery = clamp(battery - random.nextDouble() * 0.4, 0, 100);
        if (battery < 5 && random.nextDouble() < 0.3) {
            battery = 95;
        }

        temp = clamp(temp + random.nextGaussian() * 1.2, -40, 95);
        signal = clamp(signal + random.nextGaussian() * 1.5, 0, 60);
        velocity = clamp(velocity + random.nextGaussian() * 20, 800, 2400);

        if (currentMode == DataMode.REALTIME) {
            applySnapshotIfPresent();
        }
        updateMetricLabels();
        updateAlertsAndStatus();
        appendHistoryPoint();
        if (currentMode == DataMode.HISTORICAL) {
            graphsPanel.repaint();
        }
    }

    private void updateMetricLabels() {
        batteryValue.setText(FORMAT.format(battery));
        tempValue.setText(FORMAT.format(temp));
        signalValue.setText(FORMAT.format(signal));
        velocityValue.setText(FORMAT.format(velocity));
    }

    private void updateAlertsAndStatus() {
        boolean newBatteryCritical = battery < 25;
        boolean newTempHigh = temp > 70;
        boolean newSignalLow = signal < 10;

        if (newBatteryCritical && !batteryCritical) {
            appendAlert("CRITICAL: Battery voltage low (" + FORMAT.format(battery) + " V)");
        }
        if (newTempHigh && !tempHigh) {
            appendAlert("WARN: Thermal spike detected (" + FORMAT.format(temp) + " C)");
        }
        if (newSignalLow && !signalLow) {
            appendAlert("WARN: Signal strength low (" + FORMAT.format(signal) + " dB)");
        }

        batteryCritical = newBatteryCritical;
        tempHigh = newTempHigh;
        signalLow = newSignalLow;

        if (batteryCritical || tempHigh || signalLow) {
            statusValue.setText("STATUS: ATTENTION REQUIRED");
            statusValue.setForeground(new Color(176, 94, 0));
        } else {
            statusValue.setText("STATUS: NOMINAL");
            statusValue.setForeground(new Color(0, 128, 0));
        }
    }

    private void appendAlert(String message) {
        String timestamp = LocalTime.now().format(TIME_FORMAT);
        alertsArea.insert("[" + timestamp + "] " + message + "\n", 0);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private void configureSources() {
        for (DataSource source : DataSource.values()) {
            sourceSelect.addItem(source);
        }
        sourceSelect.setSelectedItem(DataSource.SIMULATED);
        sourceSelect.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                DataSource source = (DataSource) sourceSelect.getSelectedItem();
                if (source == null) {
                    return;
                }
                currentSource = source;
                latestSnapshot = null;
                refreshVehicleList();
            }
        });
    }

    private void configureVehicles() {
        refreshVehicleList();
        refreshButton.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                refreshVehicleList();
            }
        });

        vehicleSelect.addActionListener(new VehicleSelectionListener());

        historicalButton.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                setDataMode(DataMode.HISTORICAL);
            }
        });
        realtimeButton.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                setDataMode(DataMode.REALTIME);
            }
        });
        exportButton.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                exportHistoricalCsv();
            }
        });
    }

    private void refreshVehicleList() {
        VehicleOption selectedOption = (VehicleOption) vehicleSelect.getSelectedItem();
        vehicleSelect.removeAllItems();
        for (VehicleOption option : VehicleOption.forSource(currentSource)) {
            vehicleSelect.addItem(option);
        }
        if (selectedOption != null) {
            vehicleSelect.setSelectedItem(selectedOption);
        } else {
            vehicleSelect.setSelectedIndex(0);
        }
        VehicleOption currentOption = (VehicleOption) vehicleSelect.getSelectedItem();
        if (currentOption != null) {
            titleLabel.setText(currentOption.name);
            updateModeAvailability(currentOption.availability);
        }
    }

    private void handleVehicleSelection() {
        VehicleOption selectedOption = (VehicleOption) vehicleSelect.getSelectedItem();
        if (selectedOption == null) {
            return;
        }
        titleLabel.setText(selectedOption.name);
        updateModeAvailability(selectedOption.availability);
        DataMode defaultMode = selectedOption.availability.supportsRealtime()
                ? DataMode.REALTIME
                : DataMode.HISTORICAL;
        setDataMode(defaultMode);
        JOptionPane.showMessageDialog(
                this,
                selectedOption.availability.message(),
                "Data Availability",
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    private class VehicleSelectionListener implements java.awt.event.ActionListener {
        @Override
        public void actionPerformed(java.awt.event.ActionEvent event) {
            handleVehicleSelection();
        }
    }

    private void updateModeAvailability(DataAvailability availability) {
        historicalButton.setEnabled(availability.supportsHistorical());
        realtimeButton.setEnabled(availability.supportsRealtime());
        exportButton.setEnabled(availability.supportsHistorical());
    }

    private void setDataMode(DataMode mode) {
        currentMode = mode;
        dataModeLabel.setText(mode == DataMode.REALTIME ? "Real-Time Data" : "Historical Data");
        centerLayout.show(centerPanel, mode.name());
    }

    private void seedHistory() {
        long now = System.currentTimeMillis();
        long start = now - (batteryHistory.getMaxPoints() - 1L) * HISTORY_STEP_MS;
        batteryHistory.seed(100.0, 0.3, 40.0, 100.0, start);
        tempHistory.seed(22.0, 1.2, -40.0, 95.0, start);
        signalHistory.seed(35.0, 1.5, 0.0, 60.0, start);
        velocityHistory.seed(1200.0, 20.0, 800.0, 2400.0, start);
    }

    private void appendHistoryPoint() {
        long now = System.currentTimeMillis();
        batteryHistory.addOrUpdate(battery, now);
        tempHistory.addOrUpdate(temp, now);
        signalHistory.addOrUpdate(signal, now);
        velocityHistory.addOrUpdate(velocity, now);
    }

    private void exportHistoricalCsv() {
        if (!exportButton.isEnabled()) {
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export Historical Data");
        int choice = chooser.showSaveDialog(this);
        if (choice != JFileChooser.APPROVE_OPTION) {
            return;
        }

        StringBuilder builder = new StringBuilder();
        builder.append("timestamp,battery,temperature,signal,velocity\n");
        int size = batteryHistory.size();
        for (int i = 0; i < size; i++) {
            long timestamp = batteryHistory.getTimestamp(i);
            builder.append(java.time.Instant.ofEpochMilli(timestamp)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDateTime()
                    .format(DATE_TIME_FORMAT));
            builder.append(',');
            builder.append(FORMAT.format(batteryHistory.getValue(i))).append(',');
            builder.append(FORMAT.format(tempHistory.getValue(i))).append(',');
            builder.append(FORMAT.format(signalHistory.getValue(i))).append(',');
            builder.append(FORMAT.format(velocityHistory.getValue(i))).append('\n');
        }

        try {
            java.nio.file.Files.write(chooser.getSelectedFile().toPath(), builder.toString().getBytes());
        } catch (java.io.IOException ex) {
            JOptionPane.showMessageDialog(this, "Failed to export CSV: " + ex.getMessage(),
                    "Export Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void startDataPolling() {
        // Background polling for external telemetry sources.
        dataExecutor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                DataSnapshot snapshot = fetchSnapshot(currentSource);
                if (snapshot != null) {
                    latestSnapshot = snapshot;
                }
            }
        }, 0, 15, TimeUnit.SECONDS);
    }

    private DataSnapshot fetchSnapshot(DataSource source) {
        try {
            if (source == DataSource.ISS_TLE) {
                return fetchIssTle();
            }
            if (source == DataSource.OPENSKY) {
                return fetchOpenSky();
            }
            if (source == DataSource.AISHUB) {
                return fetchAisHub();
            }
        } catch (Exception ex) {
            return null;
        }
        return null;
    }

    private DataSnapshot fetchIssTle() throws Exception {
        String url = "https://celestrak.org/NORAD/elements/gp.php?NAME=ISS%20(ZARYA)&FORMAT=TLE";
        String data = fetchUrlString(url);
        String[] lines = data.split("\\R");
        if (lines.length < 3) {
            return null;
        }
        String line2 = lines[2].trim();
        if (line2.length() < 63) {
            return null;
        }
        double meanMotion = Double.parseDouble(line2.substring(52, 63).trim());
        double periodSeconds = 86400.0 / meanMotion;
        double earthRadiusKm = 6371.0;
        double altitudeKm = 420.0;
        double radiusKm = earthRadiusKm + altitudeKm;
        double speedMs = (2.0 * Math.PI * radiusKm * 1000.0) / periodSeconds;
        return new DataSnapshot(speedMs, -10.0, clamp(60.0 - altitudeKm * 0.08, 5.0, 60.0));
    }

    private DataSnapshot fetchOpenSky() throws Exception {
        String url = "https://opensky-network.org/api/states/all";
        String data = fetchUrlString(url);
        List<String> state = parseFirstOpenSkyState(data);
        if (state == null || state.size() < 14) {
            return null;
        }
        Double velocityMs = parseNullableDouble(state.get(9));
        Double altitude = parseNullableDouble(state.get(7));
        if (velocityMs == null) {
            return null;
        }
        double tempC = altitude == null ? temp : clamp(15.0 - (altitude / 1000.0) * 6.5, -60.0, 40.0);
        double signalDb = altitude == null ? signal : clamp(60.0 - altitude / 1000.0, 5.0, 60.0);
        return new DataSnapshot(velocityMs, tempC, signalDb);
    }

    private DataSnapshot fetchAisHub() throws Exception {
        if (AIS_USERNAME == null || AIS_USERNAME.trim().isEmpty()) {
            return null;
        }
        String url = "https://data.aishub.net/ws.php?username=" + AIS_USERNAME + "&format=1&output=json";
        String data = fetchUrlString(url);
        Pattern speedPattern = Pattern.compile("\"SPEED\"\\s*:\\s*([0-9.]+)");
        Matcher matcher = speedPattern.matcher(data);
        if (!matcher.find()) {
            return null;
        }
        double knots = Double.parseDouble(matcher.group(1));
        double speedMs = knots * 0.514444;
        return new DataSnapshot(speedMs, temp, signal);
    }

    private List<String> parseFirstOpenSkyState(String json) {
        int statesIndex = json.indexOf("\"states\"");
        if (statesIndex == -1) {
            return null;
        }
        int arrayStart = json.indexOf('[', statesIndex);
        if (arrayStart == -1) {
            return null;
        }
        int firstStateStart = json.indexOf('[', arrayStart + 1);
        if (firstStateStart == -1) {
            return null;
        }
        int firstStateEnd = findMatchingBracket(json, firstStateStart);
        if (firstStateEnd == -1) {
            return null;
        }
        String stateArray = json.substring(firstStateStart + 1, firstStateEnd);
        return splitJsonArray(stateArray);
    }

    private int findMatchingBracket(String json, int start) {
        int depth = 0;
        boolean inString = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"' && (i == 0 || json.charAt(i - 1) != '\\')) {
                inString = !inString;
            }
            if (inString) {
                continue;
            }
            if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private List<String> splitJsonArray(String arrayContent) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inString = false;
        for (int i = 0; i < arrayContent.length(); i++) {
            char c = arrayContent.charAt(i);
            if (c == '"' && (i == 0 || arrayContent.charAt(i - 1) != '\\')) {
                inString = !inString;
            }
            if (c == ',' && !inString) {
                values.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            values.add(current.toString().trim());
        }
        return values;
    }

    private Double parseNullableDouble(String value) {
        if (value == null || value.isEmpty() || value.equals("null")) {
            return null;
        }
        return Double.parseDouble(value.replace("\"", "").trim());
    }

    private String fetchUrlString(String urlString) throws Exception {
        // Simple Java 8 HTTP fetch helper.
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        connection.setRequestMethod("GET");
        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) {
            throw new IllegalStateException("HTTP " + status + " for " + urlString);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
            return builder.toString();
        } finally {
            connection.disconnect();
        }
    }

    private void applySnapshotIfPresent() {
        DataSnapshot snapshot = latestSnapshot;
        if (snapshot == null) {
            return;
        }
        if (snapshot.velocityMs != null) {
            velocity = clamp(snapshot.velocityMs, 0, 2400);
        }
        if (snapshot.tempC != null) {
            temp = clamp(snapshot.tempC, -40, 95);
        }
        if (snapshot.signalDb != null) {
            signal = clamp(snapshot.signalDb, 0, 60);
        }
    }

    private enum DataMode {
        REALTIME,
        HISTORICAL
    }

    private enum DataAvailability {
        REALTIME_ONLY,
        HISTORICAL_ONLY,
        BOTH;

        boolean supportsRealtime() {
            return this == REALTIME_ONLY || this == BOTH;
        }

        boolean supportsHistorical() {
            return this == HISTORICAL_ONLY || this == BOTH;
        }

        String message() {
            if (this == BOTH) {
                return "This vehicle supports Real-Time and Historical data.";
            }
            if (this == REALTIME_ONLY) {
                return "This vehicle supports Real-Time data only.";
            }
            return "This vehicle supports Historical data only.";
        }
    }

    private static class VehicleOption {
        private final String name;
        private final String type;
        private final DataAvailability availability;

        VehicleOption(String name, String type, DataAvailability availability) {
            this.name = name;
            this.type = type;
            this.availability = availability;
        }

        static List<VehicleOption> forSource(DataSource source) {
            List<VehicleOption> options = new ArrayList<>();
            if (source == DataSource.ISS_TLE) {
                options.add(new VehicleOption("ISS (International Space Station)", "Satellite",
                        DataAvailability.BOTH));
            } else if (source == DataSource.OPENSKY) {
                options.add(new VehicleOption("Commercial Aircraft (ADS-B)", "Aircraft",
                        DataAvailability.BOTH));
            } else if (source == DataSource.AISHUB) {
                options.add(new VehicleOption("Cargo Vessel (AIS)", "Ship",
                        DataAvailability.BOTH));
            } else {
                options.add(new VehicleOption("ISS (International Space Station)", "Satellite",
                        DataAvailability.BOTH));
                options.add(new VehicleOption("Commercial Aircraft (ADS-B)", "Aircraft",
                        DataAvailability.BOTH));
                options.add(new VehicleOption("Cargo Vessel (AIS)", "Ship",
                        DataAvailability.BOTH));
                options.add(new VehicleOption("Mars InSight Lander", "Planetary Lander",
                        DataAvailability.HISTORICAL_ONLY));
                options.add(new VehicleOption("Deep-Space Probe (Public Archive)", "Spacecraft",
                        DataAvailability.HISTORICAL_ONLY));
            }
            return options;
        }

        @Override
        public String toString() {
            return name + " - " + type;
        }
    }

    private static class TelemetrySeries {
        private final List<Double> values = new ArrayList<>();
        private final List<Long> timestamps = new ArrayList<>();
        private final int maxPoints;
        private final long stepMs;
        private final Random localRandom = new Random();

        TelemetrySeries(int maxPoints, long stepMs) {
            this.maxPoints = maxPoints;
            this.stepMs = stepMs;
        }

        void seed(double start, double variance, double min, double max, long startTimeMs) {
            values.clear();
            timestamps.clear();
            double current = start;
            for (int i = 0; i < maxPoints; i++) {
                current = clampValue(current + localRandom.nextGaussian() * variance, min, max);
                values.add(current);
                timestamps.add(startTimeMs + (long) i * stepMs);
            }
        }

        void addOrUpdate(double value, long timestamp) {
            if (values.isEmpty()) {
                values.add(value);
                timestamps.add(timestamp);
                return;
            }
            long lastTimestamp = timestamps.get(timestamps.size() - 1);
            if (timestamp - lastTimestamp < stepMs) {
                values.set(values.size() - 1, value);
                timestamps.set(timestamps.size() - 1, timestamp);
                return;
            }
            while (timestamp - lastTimestamp >= stepMs) {
                lastTimestamp += stepMs;
                values.add(value);
                timestamps.add(lastTimestamp);
            }
            trimToMax();
        }

        int size() {
            return values.size();
        }

        double getValue(int index) {
            return values.get(index);
        }

        long getTimestamp(int index) {
            return timestamps.get(index);
        }

        int getMaxPoints() {
            return maxPoints;
        }

        private void trimToMax() {
            while (values.size() > maxPoints) {
                values.remove(0);
                timestamps.remove(0);
            }
        }

        List<Double> getValues() {
            return values;
        }

        private double clampValue(double value, double min, double max) {
            return Math.max(min, Math.min(max, value));
        }
    }

    private static class GraphPanel extends JPanel {
        private final String label;
        private final TelemetrySeries series;

        GraphPanel(String label, TelemetrySeries series) {
            this.label = label;
            this.series = series;
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(210, 210, 210)),
                    BorderFactory.createEmptyBorder(12, 12, 12, 12)
            ));
            setToolTipText("");
        }

        @Override
        protected void paintComponent(java.awt.Graphics g) {
            super.paintComponent(g);
            int width = getWidth();
            int height = getHeight();

            g.setColor(Color.DARK_GRAY);
            g.setFont(new Font("SansSerif", Font.PLAIN, 14));
            g.drawString(label, 8, 18);

            List<Double> values = series.getValues();
            if (values.size() < 2) {
                return;
            }

            double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
            double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(1);
            if (max - min < 0.01) {
                max = min + 0.01;
            }

            int left = 8;
            int right = width - 8;
            int top = 30;
            int bottom = height - 16;
            int plotWidth = right - left;
            int plotHeight = bottom - top;

            g.setColor(new Color(230, 230, 230));
            g.drawRect(left, top, plotWidth, plotHeight);

            g.setColor(new Color(0, 102, 204));
            int count = values.size();
            int prevX = left;
            int prevY = bottom - (int) ((values.get(0) - min) / (max - min) * plotHeight);
            for (int i = 1; i < count; i++) {
                int x = left + (int) ((i / (double) (count - 1)) * plotWidth);
                int y = bottom - (int) ((values.get(i) - min) / (max - min) * plotHeight);
                g.drawLine(prevX, prevY, x, y);
                prevX = x;
                prevY = y;
            }

            g.setColor(new Color(120, 120, 120));
            int ticks = 4;
            for (int i = 0; i <= ticks; i++) {
                int x = left + (int) ((i / (double) ticks) * plotWidth);
                int index = (int) ((i / (double) ticks) * (count - 1));
                long timestamp = series.getTimestamp(index);
                String labelText = java.time.Instant.ofEpochMilli(timestamp)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDateTime()
                        .format(DateTimeFormatter.ofPattern("MM-dd"));
                g.drawString(labelText, x - 14, bottom + 14);
            }
        }

        @Override
        public String getToolTipText(java.awt.event.MouseEvent event) {
            if (series.size() < 2) {
                return null;
            }
            int left = 8;
            int right = getWidth() - 8;
            int plotWidth = right - left;
            if (plotWidth <= 0) {
                return null;
            }
            int x = Math.max(left, Math.min(right, event.getX()));
            int index = (int) (((x - left) / (double) plotWidth) * (series.size() - 1));
            index = Math.max(0, Math.min(series.size() - 1, index));
            long timestamp = series.getTimestamp(index);
            String time = java.time.Instant.ofEpochMilli(timestamp)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDateTime()
                    .format(DATE_TIME_FORMAT);
            double value = series.getValue(index);
            return time + " | " + label + ": " + FORMAT.format(value);
        }
    }

    private enum DataSource {
        SIMULATED("Simulated (Demo)"),
        ISS_TLE("ISS TLE (Celestrak)"),
        OPENSKY("OpenSky (ADS-B)"),
        AISHUB("AIS Hub");

        private final String label;

        DataSource(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static class DataSnapshot {
        private final Double velocityMs;
        private final Double tempC;
        private final Double signalDb;

        DataSnapshot(Double velocityMs, Double tempC, Double signalDb) {
            this.velocityMs = velocityMs;
            this.tempC = tempC;
            this.signalDb = signalDb;
        }
    }
}

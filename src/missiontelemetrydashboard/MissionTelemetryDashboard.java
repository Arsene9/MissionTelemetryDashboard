package missiontelemetrydashboard;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.text.DecimalFormat;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

public class MissionTelemetryDashboard extends JFrame {
    private static final DecimalFormat FORMAT = new DecimalFormat("0.00");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Random random = new Random();
    private final JLabel batteryValue = new JLabel();
    private final JLabel tempValue = new JLabel();
    private final JLabel signalValue = new JLabel();
    private final JLabel velocityValue = new JLabel();
    private final JLabel statusValue = new JLabel();
    private final JTextArea alertsArea = new JTextArea();

    private double battery = 100.0;
    private double temp = 22.0;
    private double signal = 35.0;
    private double velocity = 1200.0;

    private boolean batteryCritical;
    private boolean tempHigh;
    private boolean signalLow;

    public MissionTelemetryDashboard() {
        super("Mission Telemetry Dashboard");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(12, 12));
        add(buildHeader(), BorderLayout.NORTH);
        add(buildMetrics(), BorderLayout.CENTER);
        add(buildAlerts(), BorderLayout.EAST);
        setMinimumSize(new Dimension(900, 520));
        setLocationRelativeTo(null);
        startTelemetryStream();
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(12, 12, 0, 12));

        JLabel title = new JLabel("Lunar/Mars Mission Control");
        title.setFont(new Font("SansSerif", Font.BOLD, 22));

        statusValue.setFont(new Font("SansSerif", Font.BOLD, 16));
        statusValue.setHorizontalAlignment(SwingConstants.RIGHT);
        statusValue.setText("STATUS: NOMINAL");
        statusValue.setForeground(new Color(0, 128, 0));

        header.add(title, BorderLayout.WEST);
        header.add(statusValue, BorderLayout.EAST);
        return header;
    }

    private JPanel buildMetrics() {
        JPanel metrics = new JPanel(new GridLayout(2, 2, 12, 12));
        metrics.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        metrics.add(buildMetricPanel("Battery Voltage (V)", batteryValue));
        metrics.add(buildMetricPanel("Thermal Temp (C)", tempValue));
        metrics.add(buildMetricPanel("Signal Strength (dB)", signalValue));
        metrics.add(buildMetricPanel("Velocity (m/s)", velocityValue));

        updateMetricLabels();
        return metrics;
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
        Timer timer = new Timer(1000, event -> updateTelemetry());
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

        updateMetricLabels();
        updateAlertsAndStatus();
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

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            MissionTelemetryDashboard dashboard = new MissionTelemetryDashboard();
            dashboard.setVisible(true);
        });
    }
}

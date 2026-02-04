package missiontelemetrydashboard;

import javax.swing.SwingUtilities;

public class MissionTelemetryDashboardApp {
    public static void main(String[] args) {
        // App entry point kept separate from the dashboard class.
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                MissionTelemetryDashboard dashboard = new MissionTelemetryDashboard();
                dashboard.setVisible(true);
            }
        });
    }
}

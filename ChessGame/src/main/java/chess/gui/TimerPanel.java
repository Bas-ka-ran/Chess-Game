package chess.gui;
import javax.swing.*;
import java.awt.*;

public class TimerPanel extends JPanel {
    private JLabel timerLabel;
    private int timeLeft = 600; // 10 minutes in seconds

    public TimerPanel() {
        timerLabel = new JLabel("Time: 10:00");
        add(timerLabel);

        // Update every 1000ms (1 second)
        Timer timer = new Timer(1000, e -> {
            if (timeLeft > 0) {
                timeLeft--;
                int mins = timeLeft / 60;
                int secs = timeLeft % 60;
                timerLabel.setText(String.format("Time: %02d:%02d", mins, secs));
            }
        });
        timer.start();
    }
}

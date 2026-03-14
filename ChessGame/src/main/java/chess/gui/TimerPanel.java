package chess.gui;

import chess.shared.GameState;

import javax.swing.*;
import java.awt.*;

public class TimerPanel extends JPanel {

    private final JLabel nameLabel  = new JLabel("", SwingConstants.LEFT);
    private final JLabel clockLabel = new JLabel("10:00", SwingConstants.RIGHT);
    private long millisLeft = 600_000;
    private boolean active = false;

    private final javax.swing.Timer localTick;

    public TimerPanel(String playerName, Color bg, Color fg) {
        setLayout(new BorderLayout(10, 0));
        setBackground(bg);
        setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));

        nameLabel.setText(playerName);
        nameLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
        nameLabel.setForeground(fg);

        clockLabel.setFont(new Font("Monospaced", Font.BOLD, 22));
        clockLabel.setForeground(fg);

        add(nameLabel,  BorderLayout.WEST);
        add(clockLabel, BorderLayout.EAST);

        localTick = new javax.swing.Timer(100, e -> {
            if (active) {
                millisLeft = Math.max(0, millisLeft - 100);
                refresh();
            }
        });
        localTick.start();
    }

    public void sync(long millis, boolean isActive) {
        this.millisLeft = millis;
        this.active     = isActive;
        refresh();
        setBackground(isActive ? new Color(255, 230, 100) : getBackground());
    }

    public void setPlayerName(String name) {
        nameLabel.setText(name);
    }

    public void stop() { localTick.stop(); active = false; }

    private void refresh() {
        clockLabel.setText(GameState.formatTime(millisLeft));
        clockLabel.setForeground(millisLeft < 30_000 ? Color.RED
            : nameLabel.getForeground());
    }
}

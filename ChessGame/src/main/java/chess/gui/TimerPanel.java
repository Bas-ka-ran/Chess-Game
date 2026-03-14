package chess.gui;

import chess.shared.GameState;
import com.github.bhlangonijr.chesslib.Side;

import javax.swing.*;
import java.awt.*;

public class TimerPanel extends JPanel {

    private final JLabel whiteLabel = new JLabel("10:00", SwingConstants.CENTER);
    private final JLabel blackLabel = new JLabel("10:00", SwingConstants.CENTER);

    private long whiteMillis = 600_000;
    private long blackMillis = 600_000;
    private Side activeSide  = Side.WHITE;

    private final javax.swing.Timer localTick;

    // ── FIX: no-arg constructor ──
    public TimerPanel() {
        setLayout(new GridLayout(1, 2));
        setPreferredSize(new Dimension(600, 56));

        Font clockFont = new Font("Monospaced", Font.BOLD, 24);
        whiteLabel.setFont(clockFont);
        blackLabel.setFont(clockFont);
        whiteLabel.setOpaque(true);
        blackLabel.setOpaque(true);

        add(blackLabel);
        add(whiteLabel);
        refreshColors();

        localTick = new javax.swing.Timer(100, e -> {
            if (activeSide == Side.WHITE) whiteMillis = Math.max(0, whiteMillis - 100);
            else                          blackMillis  = Math.max(0, blackMillis  - 100);
            refreshLabels();
        });
        localTick.start();
    }

    // ── FIX: sync(long, long, Side) ──
    public void sync(long wMs, long bMs, Side nextToMove) {
        this.whiteMillis = wMs;
        this.blackMillis = bMs;
        this.activeSide  = nextToMove;
        refreshLabels();
        refreshColors();
    }

    private void refreshLabels() {
        whiteLabel.setText(GameState.formatTime(whiteMillis));
        blackLabel.setText(GameState.formatTime(blackMillis));
        whiteLabel.setForeground(whiteMillis < 30_000 ? Color.RED : Color.BLACK);
        blackLabel.setForeground(blackMillis < 30_000 ? Color.RED : Color.WHITE);
    }

    private void refreshColors() {
        whiteLabel.setBackground(activeSide == Side.WHITE
            ? new Color(255, 255, 200) : Color.WHITE);
        blackLabel.setBackground(activeSide == Side.BLACK
            ? new Color(80, 80, 80) : Color.DARK_GRAY);
    }

    public void stop() { localTick.stop(); }
}

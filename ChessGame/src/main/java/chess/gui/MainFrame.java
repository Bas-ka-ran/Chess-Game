package chess.gui;

import chess.client.ChessClient;
import chess.client.MessageListener;
import chess.shared.ChessMessage;
import chess.shared.GameState;
import chess.shared.MessageType;

import com.github.bhlangonijr.chesslib.Side;

import javax.swing.*;
import java.awt.*;

public class MainFrame extends JFrame implements MessageListener {

    private ChessClient client;
    private Side        myColor = Side.WHITE;

    private JLabel      waitingLabel;
    private BoardPanel  boardPanel;
    private JLabel      whiteClock;
    private JLabel      blackClock;

    public MainFrame() {
        setTitle("Chess");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(600, 680);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // ── Waiting screen ──
        waitingLabel = new JLabel("Waiting for opponent…", SwingConstants.CENTER);
        waitingLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        add(waitingLabel, BorderLayout.CENTER);

        // ── Clock panel ──
        JPanel clockPanel = new JPanel(new GridLayout(1, 2));
        blackClock = new JLabel("10:00", SwingConstants.CENTER);
        whiteClock = new JLabel("10:00", SwingConstants.CENTER);
        blackClock.setFont(new Font("Monospaced", Font.BOLD, 22));
        whiteClock.setFont(new Font("Monospaced", Font.BOLD, 22));
        blackClock.setOpaque(true); blackClock.setBackground(Color.DARK_GRAY); blackClock.setForeground(Color.WHITE);
        whiteClock.setOpaque(true); whiteClock.setBackground(Color.WHITE);     whiteClock.setForeground(Color.BLACK);
        clockPanel.add(blackClock);
        clockPanel.add(whiteClock);
        clockPanel.setPreferredSize(new Dimension(600, 50));
        add(clockPanel, BorderLayout.SOUTH);
    }

    public void setClient(ChessClient client) {
        this.client = client;
    }

    public void showWaiting() {
        waitingLabel.setVisible(true);
        if (boardPanel != null) boardPanel.setVisible(false);
    }

    // ── MessageListener implementation ──
    @Override
    public void onMessage(ChessMessage msg) {
        switch (msg.type) {

            case ASSIGN_COLOR:
                myColor = "WHITE".equals(msg.moveUci) ? Side.WHITE : Side.BLACK;
                setTitle("Chess — Playing as " + myColor);
                break;

            case GAME_START:
                showBoard();
                break;

            case BOARD_UPDATE:
                if (boardPanel != null) {
                    boardPanel.updateFromFen(msg.fen);
                }
                updateClocks(msg.whiteMillis, msg.blackMillis);
                break;

            case INVALID_MOVE:
                if (boardPanel != null) boardPanel.flashInvalid();
                break;

            case GAME_OVER:
                if (boardPanel != null) boardPanel.updateFromFen(msg.fen);
                updateClocks(msg.whiteMillis, msg.blackMillis);
                if (boardPanel != null) boardPanel.setLocked(true);
                JOptionPane.showMessageDialog(this,
                    "Game Over: " + msg.result,
                    "Game Over", JOptionPane.INFORMATION_MESSAGE);
                break;

            case OPPONENT_DISCONNECTED:
                if (boardPanel != null) boardPanel.setLocked(true);
                JOptionPane.showMessageDialog(this,
                    "Opponent disconnected.",
                    "Disconnected", JOptionPane.WARNING_MESSAGE);
                break;
        }
    }

    // ── Private helpers ──

    private void showBoard() {
        remove(waitingLabel);
        boardPanel = new BoardPanel(myColor, client);
        add(boardPanel, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    private void updateClocks(long whiteMs, long blackMs) {
        whiteClock.setText(GameState.formatTime(whiteMs));
        blackClock.setText(GameState.formatTime(blackMs));
        // Turn clocks red when under 30 seconds
        whiteClock.setForeground(whiteMs < 30_000 ? Color.RED : Color.BLACK);
        blackClock.setForeground(blackMs < 30_000 ? Color.RED : Color.WHITE);
    }
}

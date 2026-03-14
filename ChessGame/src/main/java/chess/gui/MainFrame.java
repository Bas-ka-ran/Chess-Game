package chess.gui;

import chess.client.ChessClient;
import chess.client.MessageListener;
import chess.shared.ChessMessage;

import com.github.bhlangonijr.chesslib.Side;

import javax.swing.*;
import java.awt.*;

public class MainFrame extends JFrame implements MessageListener {

    private ChessClient client;
    private Side        myColor = Side.WHITE;

    private JLabel      waitingLabel;
    private BoardPanel  boardPanel;
    private TimerPanel  timerPanel;

    private DefaultListModel<String> historyModel = new DefaultListModel<>();
    private int moveNumber = 1;

    public MainFrame() {
        setTitle("Chess");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(700, 650);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        waitingLabel = new JLabel("Connecting to server…", SwingConstants.CENTER);
        waitingLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        add(waitingLabel, BorderLayout.CENTER);

        timerPanel = new TimerPanel();
        add(timerPanel, BorderLayout.SOUTH);

        JList<String> historyList = new JList<>(historyModel);
        historyList.setFont(new Font("Monospaced", Font.PLAIN, 13));
        JScrollPane scroll = new JScrollPane(historyList);
        scroll.setPreferredSize(new Dimension(110, 0));
        scroll.setBorder(BorderFactory.createTitledBorder("Moves"));
        add(scroll, BorderLayout.EAST);
    }

    public void setClient(ChessClient client) { this.client = client; }

    public void showWaiting() {
        waitingLabel.setText("Connecting to server…");
        waitingLabel.setVisible(true);
        if (boardPanel != null) boardPanel.setVisible(false);
    }

    // ── NEW: called after socket connects, before 2nd player joins ──
    public void showConnected() {
        waitingLabel.setText("⏳  Connected! Waiting for Player 2…");
        waitingLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        waitingLabel.setForeground(new Color(40, 120, 200));
        repaint();
    }

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
                if (boardPanel != null) boardPanel.updateFromFen(msg.fen);
                timerPanel.sync(msg.whiteMillis, msg.blackMillis,
                    msg.fen.contains(" w ") ? Side.WHITE : Side.BLACK);
                if (msg.moveUci != null)
                    historyModel.addElement(moveNumber++ + ". " + msg.moveUci);
                break;

            case INVALID_MOVE:
                if (boardPanel != null) boardPanel.flashInvalid();
                break;

            case GAME_OVER:
                if (boardPanel != null) { boardPanel.updateFromFen(msg.fen); boardPanel.setLocked(true); }
                timerPanel.stop();
                int choice = JOptionPane.showOptionDialog(this,
                    "Game Over: " + msg.result, "Game Over",
                    JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE,
                    null, new String[]{"Play Again", "Exit"}, "Exit");
                if (choice == 0) { dispose(); ChessClient.main(new String[]{}); }
                else System.exit(0);
                break;

            case OPPONENT_DISCONNECTED:
                if (boardPanel != null) boardPanel.setLocked(true);
                timerPanel.stop();
                JOptionPane.showMessageDialog(this, "Opponent disconnected.",
                    "Disconnected", JOptionPane.WARNING_MESSAGE);
                break;
        }
    }

    private void showBoard() {
        remove(waitingLabel);
        boardPanel = new BoardPanel(myColor, client);
        add(boardPanel, BorderLayout.CENTER);
        revalidate();
        repaint();
    }
}

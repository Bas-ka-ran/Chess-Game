package chess.gui;

import chess.client.ChessClient;
import chess.client.MessageListener;
import chess.shared.ChessMessage;
import chess.shared.MessageType;

import com.github.bhlangonijr.chesslib.Side;

import javax.swing.*;
import java.awt.*;

public class MainFrame extends JFrame implements MessageListener {

    private ChessClient client;
    private Side        myColor      = Side.WHITE;
    private final String myName;
    private String      opponentName = "Opponent";

    private JLabel      waitingLabel;
    private JLabel      statusLabel;
    private BoardPanel  boardPanel;
    private TimerPanel  myTimer;
    private TimerPanel  opponentTimer;

    // Action buttons
    private JButton resignButton;
    private JButton drawButton;

    private final DefaultListModel<String> historyModel = new DefaultListModel<>();
    private int     moveNumber        = 1;
    private boolean firstMoveReceived = false;

    public MainFrame(String myName) {
        this.myName = myName;
        setTitle("Chess — " + myName);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(760, 780);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // ── Waiting screen ──
        waitingLabel = new JLabel(
            "<html><center>⏳ Waiting for opponent…<br><small>" + myName + "</small></center></html>",
            SwingConstants.CENTER);
        waitingLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        add(waitingLabel, BorderLayout.CENTER);

        // ── Opponent timer — TOP ──
        opponentTimer = new TimerPanel("Opponent", new Color(50, 50, 50), Color.WHITE);
        add(opponentTimer, BorderLayout.NORTH);

        // ── Bottom panel: status + buttons + my timer ──
        JPanel bottomPanel = new JPanel(new BorderLayout());

        // Status label (first move countdown notice)
        statusLabel = new JLabel("⏳ Waiting for White to make the first move (30s)…",
            SwingConstants.CENTER);
        statusLabel.setFont(new Font("SansSerif", Font.ITALIC, 13));
        statusLabel.setForeground(new Color(160, 80, 0));
        statusLabel.setOpaque(true);
        statusLabel.setBackground(new Color(255, 248, 210));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        statusLabel.setVisible(false);

        // ── Resign + Draw buttons ──
        resignButton = new JButton("🏳 Resign");
        drawButton   = new JButton("🤝 Offer Draw");

        resignButton.setFocusPainted(false);
        drawButton.setFocusPainted(false);

        resignButton.setBackground(new Color(200, 60, 60));
        resignButton.setForeground(Color.WHITE);
        resignButton.setFont(new Font("SansSerif", Font.BOLD, 13));

        drawButton.setBackground(new Color(70, 130, 180));
        drawButton.setForeground(Color.WHITE);
        drawButton.setFont(new Font("SansSerif", Font.BOLD, 13));

        resignButton.addActionListener(e -> handleResign());
        drawButton.addActionListener(e -> handleDrawRequest());

        // Disable until game starts
        resignButton.setEnabled(false);
        drawButton.setEnabled(false);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 6));
        buttonPanel.setBackground(new Color(230, 230, 230));
        buttonPanel.add(resignButton);
        buttonPanel.add(drawButton);

        myTimer = new TimerPanel(myName, new Color(240, 240, 240), Color.BLACK);

        bottomPanel.add(statusLabel,  BorderLayout.NORTH);
        bottomPanel.add(buttonPanel,  BorderLayout.CENTER);
        bottomPanel.add(myTimer,      BorderLayout.SOUTH);
        add(bottomPanel, BorderLayout.SOUTH);

        // ── Move history — RIGHT ──
        JList<String> historyList = new JList<>(historyModel);
        historyList.setFont(new Font("Monospaced", Font.PLAIN, 13));
        JScrollPane scroll = new JScrollPane(historyList);
        scroll.setPreferredSize(new Dimension(120, 0));
        scroll.setBorder(BorderFactory.createTitledBorder("Moves"));
        add(scroll, BorderLayout.EAST);
    }

    public void setClient(ChessClient client) { this.client = client; }

    public void showWaiting() {
        if (waitingLabel != null) waitingLabel.setVisible(true);
        if (boardPanel   != null) boardPanel.setVisible(false);
    }

    // ── Button handlers ──

    private void handleResign() {
        int confirm = JOptionPane.showConfirmDialog(this,
            "Are you sure you want to resign?",
            "Resign", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm == JOptionPane.YES_OPTION) {
            client.sendMessage(ChessMessage.simple(MessageType.RESIGN));
            resignButton.setEnabled(false);
            drawButton.setEnabled(false);
        }
    }

    private void handleDrawRequest() {
        int confirm = JOptionPane.showConfirmDialog(this,
            "Offer a draw to " + opponentName + "?",
            "Offer Draw", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (confirm == JOptionPane.YES_OPTION) {
            client.sendMessage(ChessMessage.simple(MessageType.DRAW_REQUEST));
            drawButton.setEnabled(false); // prevent spam
            JOptionPane.showMessageDialog(this,
                "Draw offer sent to " + opponentName + ".",
                "Draw Offered", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    // ── MessageListener ──

    @Override
    public void onMessage(ChessMessage msg) {
        switch (msg.type) {

            case ASSIGN_COLOR -> {
                myColor      = "WHITE".equals(msg.moveUci) ? Side.WHITE : Side.BLACK;
                opponentName = (msg.playerName != null) ? msg.playerName : "Opponent";
                opponentTimer.setPlayerName(opponentName);
                setTitle("Chess — " + myName + " (" + myColor + ") vs " + opponentName);
            }

            case GAME_START -> {
                showBoard();
                resignButton.setEnabled(true);
                drawButton.setEnabled(true);
                statusLabel.setText("⏳ White has 30 seconds to make the first move…");
                statusLabel.setVisible(true);
            }

            case BOARD_UPDATE -> {
                if (boardPanel != null) boardPanel.updateFromFen(msg.fen);

                // Hide status label after first move received
                if (!firstMoveReceived && msg.moveUci != null && !msg.moveUci.isBlank()) {
                    firstMoveReceived = true;
                    statusLabel.setVisible(false);
                }

                boolean whiteToMove = msg.fen != null && msg.fen.contains(" w ");
                if (myColor == Side.WHITE) {
                    myTimer.sync(msg.whiteMillis, whiteToMove);
                    opponentTimer.sync(msg.blackMillis, !whiteToMove);
                } else {
                    myTimer.sync(msg.blackMillis, !whiteToMove);
                    opponentTimer.sync(msg.whiteMillis, whiteToMove);
                }

                if (msg.moveUci != null && !msg.moveUci.isBlank()) {
                    historyModel.addElement(moveNumber++ + ". " + msg.moveUci);
                }

                // Re-enable draw button after a move (server cleared pending offer)
                drawButton.setEnabled(true);
            }

            case INVALID_MOVE -> {
                if (boardPanel != null) boardPanel.flashInvalid();
            }

            // Opponent is offering a draw — show accept/decline dialog
            case DRAW_REQUEST -> {
                String requester = (msg.playerName != null) ? msg.playerName : opponentName;
                int choice = JOptionPane.showOptionDialog(this,
                    requester + " is offering a draw. Accept?",
                    "Draw Offer",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    new String[]{"Accept", "Decline"},
                    "Decline");
                if (choice == 0) {
                    client.sendMessage(ChessMessage.simple(MessageType.DRAW_ACCEPT));
                } else {
                    client.sendMessage(ChessMessage.simple(MessageType.DRAW_DECLINE));
                    drawButton.setEnabled(true);
                }
            }

            // Our draw offer was declined
            case DRAW_DECLINE -> {
                drawButton.setEnabled(true);
                JOptionPane.showMessageDialog(this,
                    opponentName + " declined your draw offer.",
                    "Draw Declined", JOptionPane.INFORMATION_MESSAGE);
            }

            case GAME_OVER -> {
                if (boardPanel != null) {
                    boardPanel.updateFromFen(msg.fen);
                    boardPanel.setLocked(true);
                }
                statusLabel.setVisible(false);
                resignButton.setEnabled(false);
                drawButton.setEnabled(false);
                myTimer.stop();
                opponentTimer.stop();
                int choice = JOptionPane.showOptionDialog(this,
                    "Game Over: " + msg.result,
                    "Game Over", JOptionPane.YES_NO_OPTION,
                    JOptionPane.INFORMATION_MESSAGE, null,
                    new String[]{"Play Again", "Exit"}, "Exit");
                if (choice == 0) { dispose(); ChessClient.main(new String[]{}); }
                else System.exit(0);
            }

            case OPPONENT_DISCONNECTED -> {
                if (boardPanel != null) boardPanel.setLocked(true);
                statusLabel.setVisible(false);
                resignButton.setEnabled(false);
                drawButton.setEnabled(false);
                myTimer.stop();
                opponentTimer.stop();
                JOptionPane.showMessageDialog(this,
                    opponentName + " disconnected.",
                    "Disconnected", JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    private void showBoard() {
        if (waitingLabel != null) {
            remove(waitingLabel);
            waitingLabel = null;
        }
        boardPanel = new BoardPanel(myColor, client);
        add(boardPanel, BorderLayout.CENTER);
        revalidate();
        repaint();
    }
}

package chess.gui;

import chess.client.ChessClient;
import chess.client.MessageListener;
import chess.shared.ChessMessage;

import com.github.bhlangonijr.chesslib.Side;

import javax.swing.*;
import java.awt.*;

public class MainFrame extends JFrame implements MessageListener {

    private ChessClient client;
    private Side        myColor       = Side.WHITE;
    private final String myName;
    private String      opponentName  = "Opponent";

    // Layout panels
    private JLabel      waitingLabel;
    private BoardPanel  boardPanel;
    private TimerPanel  myTimer;
    private TimerPanel  opponentTimer;

    // Move history
    private final DefaultListModel<String> historyModel = new DefaultListModel<>();
    private int moveNumber = 1;

    public MainFrame(String myName) {
        this.myName = myName;
        setTitle("Chess — " + myName);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(720, 700);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // Waiting screen
        waitingLabel = new JLabel(
            "<html><center>⏳ Waiting for opponent…<br><small>" + myName + "</small></center></html>",
            SwingConstants.CENTER);
        waitingLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        add(waitingLabel, BorderLayout.CENTER);

        // Opponent timer — TOP (dark background)
        opponentTimer = new TimerPanel("Opponent", new Color(50, 50, 50), Color.WHITE);
        add(opponentTimer, BorderLayout.NORTH);

        // My timer — BOTTOM (light background)
        myTimer = new TimerPanel(myName, new Color(240, 240, 240), Color.BLACK);
        add(myTimer, BorderLayout.SOUTH);

        // Move history — RIGHT
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

    @Override
    public void onMessage(ChessMessage msg) {
        switch (msg.type) {

            case ASSIGN_COLOR:
                myColor      = "WHITE".equals(msg.moveUci) ? Side.WHITE : Side.BLACK;
                opponentName = (msg.playerName != null) ? msg.playerName : "Opponent";
                opponentTimer.setPlayerName(opponentName);
                setTitle("Chess — " + myName + " (" + myColor + ") vs " + opponentName);
                break;

            case GAME_START:
                showBoard();
                break;

            case BOARD_UPDATE:
                if (boardPanel != null) boardPanel.updateFromFen(msg.fen);

                // Determine whose turn it is after this update
                boolean whiteToMove = msg.fen != null && msg.fen.contains(" w ");
                boolean myTurn;
                if (myColor == Side.WHITE) {
                    myTurn = whiteToMove;
                    myTimer.sync(msg.whiteMillis, myTurn);
                    opponentTimer.sync(msg.blackMillis, !myTurn);
                } else {
                    myTurn = !whiteToMove;
                    myTimer.sync(msg.blackMillis, myTurn);
                    opponentTimer.sync(msg.whiteMillis, !myTurn);
                }

                // Fix: add move to history only when moveUci is present
                if (msg.moveUci != null && !msg.moveUci.isBlank()) {
                    historyModel.addElement(moveNumber++ + ". " + msg.moveUci);
                    // Auto-scroll to latest move
                    // (done via JList scroll — find the list inside scroll pane)
                }
                break;

            case INVALID_MOVE:
                if (boardPanel != null) boardPanel.flashInvalid();
                break;

            case GAME_OVER:
                if (boardPanel != null) {
                    boardPanel.updateFromFen(msg.fen);
                    boardPanel.setLocked(true);
                }
                myTimer.stop();
                opponentTimer.stop();
                int choice = JOptionPane.showOptionDialog(this,
                    "Game Over: " + msg.result,
                    "Game Over", JOptionPane.YES_NO_OPTION,
                    JOptionPane.INFORMATION_MESSAGE, null,
                    new String[]{"Play Again", "Exit"}, "Exit");
                if (choice == 0) { dispose(); ChessClient.main(new String[]{}); }
                else System.exit(0);
                break;

            case OPPONENT_DISCONNECTED:
                if (boardPanel != null) boardPanel.setLocked(true);
                myTimer.stop();
                opponentTimer.stop();
                JOptionPane.showMessageDialog(this,
                    opponentName + " disconnected.",
                    "Disconnected", JOptionPane.WARNING_MESSAGE);
                break;
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

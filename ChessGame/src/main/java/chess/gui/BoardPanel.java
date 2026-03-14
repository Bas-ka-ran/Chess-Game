package chess.gui;

import chess.client.ChessClient;
import com.github.bhlangonijr.chesslib.Side;

import javax.swing.*;
import java.awt.*;

public class BoardPanel extends JPanel {

    private ChessClient client;
    private Side myColor;
    private boolean locked = false;

    public BoardPanel(Side myColor, ChessClient client) {
        this.myColor = myColor;
        this.client  = client;
        setPreferredSize(new Dimension(600, 600));
        setBackground(Color.GRAY);
    }

    public void updateFromFen(String fen) {
        // Will be fully implemented in Phase 5
        repaint();
    }

    public void flashInvalid() {
        // Will be fully implemented in Phase 5
        setBackground(Color.RED);
        Timer t = new Timer(300, e -> setBackground(Color.GRAY));
        t.setRepeats(false);
        t.start();
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }
}
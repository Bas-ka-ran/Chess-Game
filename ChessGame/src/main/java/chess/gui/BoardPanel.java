//package chess.gui;
import javax.swing.*;
import java.awt.*;

public class BoardPanel extends JPanel {
    private static final int TILE_SIZE = 80;

    public BoardPanel() {
        setPreferredSize(new Dimension(TILE_SIZE * 8, TILE_SIZE * 8));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                // Determine tile color
                if ((row + col) % 2 == 0) {
                    g.setColor(new Color(235, 235, 208)); // Light
                } else {
                    g.setColor(new Color(119, 148, 85)); // Dark
                }
                g.fillRect(col * TILE_SIZE, row * TILE_SIZE, TILE_SIZE, TILE_SIZE);
            }
        }
    }
}

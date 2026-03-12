package chess.gui;
import javax.imageio.ImageIO;
import java.awt.*;
import java.io.File;
import java.io.IOException;

public class PieceRenderer {
    public static Image loadPieceImage(String pieceName) {
        try {
            // Assumes images are in a folder named 'res'
            return ImageIO.read(new File("resources/pieces/" + pieceName + ".png"));
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void drawPiece(Graphics g, Image img, int x, int y, int size) {
        if (img != null) {
            g.drawImage(img, x, y, size, size, null);
        }
    }
}

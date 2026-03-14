package chess.gui;

import com.github.bhlangonijr.chesslib.Piece;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Map;

public class PieceRenderer {

    // ── Unicode fallback symbols ──
    private static final Map<Piece, String> SYMBOLS = Map.ofEntries(
        Map.entry(Piece.WHITE_KING,   "♔"),
        Map.entry(Piece.WHITE_QUEEN,  "♕"),
        Map.entry(Piece.WHITE_ROOK,   "♖"),
        Map.entry(Piece.WHITE_BISHOP, "♗"),
        Map.entry(Piece.WHITE_KNIGHT, "♘"),
        Map.entry(Piece.WHITE_PAWN,   "♙"),
        Map.entry(Piece.BLACK_KING,   "♚"),
        Map.entry(Piece.BLACK_QUEEN,  "♛"),
        Map.entry(Piece.BLACK_ROOK,   "♜"),
        Map.entry(Piece.BLACK_BISHOP, "♝"),
        Map.entry(Piece.BLACK_KNIGHT, "♞"),
        Map.entry(Piece.BLACK_PAWN,   "♟")
    );

    // Used by BoardPanel
    public static String getSymbol(Piece piece) {
        return SYMBOLS.getOrDefault(piece, "");
    }

    // Your original image-based methods (kept for future use)
    public static Image loadPieceImage(String pieceName) {
        try {
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

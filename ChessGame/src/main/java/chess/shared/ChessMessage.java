package chess.shared;

import java.io.Serializable;

public class ChessMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    public final MessageType type;
    public final String      moveUci;
    public final String      fen;
    public final long        whiteMillis;
    public final long        blackMillis;
    public final String      result;

    public ChessMessage(MessageType type, String moveUci,
                        String fen, long whiteMillis,
                        long blackMillis, String result) {
        this.type        = type;
        this.moveUci     = moveUci;
        this.fen         = fen;
        this.whiteMillis = whiteMillis;
        this.blackMillis = blackMillis;
        this.result      = result;
    }

    public static ChessMessage move(String uci) {
        return new ChessMessage(MessageType.MOVE, uci, null, 0, 0, null);
    }

    // ── FIX: assignColor takes (color, fen) — two strings ──
    public static ChessMessage assignColor(String color, String fen) {
        return new ChessMessage(MessageType.ASSIGN_COLOR, color, fen, 0, 0, null);
    }

    // ── FIX: boardUpdate takes (fen, wMs, bMs, result) — result can be null ──
    public static ChessMessage boardUpdate(String fen, long wMs, long bMs, String result) {
        return new ChessMessage(MessageType.BOARD_UPDATE, null, fen, wMs, bMs, result);
    }

    public static ChessMessage gameOver(String fen, long wMs, long bMs, String result) {
        return new ChessMessage(MessageType.GAME_OVER, null, fen, wMs, bMs, result);
    }

    public static ChessMessage simple(MessageType type) {
        return new ChessMessage(type, null, null, 0, 0, null);
    }
}

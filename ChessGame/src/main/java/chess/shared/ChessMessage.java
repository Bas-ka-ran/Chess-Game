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
    public final String      playerName;

    // Single constructor — 7 args
    public ChessMessage(MessageType type, String moveUci, String fen,
                        long whiteMillis, long blackMillis,
                        String result, String playerName) {
        this.type        = type;
        this.moveUci     = moveUci;
        this.fen         = fen;
        this.whiteMillis = whiteMillis;
        this.blackMillis = blackMillis;
        this.result      = result;
        this.playerName  = playerName;
    }

    // ── Factory methods (all use 7-arg constructor) ──

    public static ChessMessage move(String uci) {
        return new ChessMessage(MessageType.MOVE, uci, null, 0L, 0L, null, null);
    }

    public static ChessMessage boardUpdate(String fen, long wMs, long bMs, String lastUci) {
        return new ChessMessage(MessageType.BOARD_UPDATE, lastUci, fen, wMs, bMs, null, null);
    }

    public static ChessMessage gameOver(String fen, long wMs, long bMs, String result) {
        return new ChessMessage(MessageType.GAME_OVER, null, fen, wMs, bMs, result, null);
    }

    public static ChessMessage assignColor(String color, String opponentName) {
        return new ChessMessage(MessageType.ASSIGN_COLOR, color, null, 0L, 0L, null, opponentName);
    }

    public static ChessMessage playerInfo(String name) {
        return new ChessMessage(MessageType.PLAYER_INFO, null, null, 0L, 0L, null, name);
    }

    public static ChessMessage simple(MessageType type) {
        return new ChessMessage(type, null, null, 0L, 0L, null, null);
    }
}

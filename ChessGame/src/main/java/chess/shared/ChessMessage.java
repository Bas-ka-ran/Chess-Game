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
    public final String      playerName;   // NEW: carries name info

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

    // Factory methods
    public static ChessMessage move(String uci) {
        return new ChessMessage(MessageType.MOVE, uci, null, 0, 0, null, null);
    }

    public static ChessMessage boardUpdate(String fen, long wMs, long bMs, String lastUci) {
        return new ChessMessage(MessageType.BOARD_UPDATE, lastUci, fen, wMs, bMs, null, null);
    }

    public static ChessMessage gameOver(String fen, long wMs, long bMs, String result) {
        return new ChessMessage(MessageType.GAME_OVER, null, fen, wMs, bMs, result, null);
    }

    // color = "WHITE"/"BLACK", playerName = opponent's name
    public static ChessMessage assignColor(String color, String opponentName) {
        return new ChessMessage(MessageType.ASSIGN_COLOR, color, null, 0, 0, null, opponentName);
    }

    public static ChessMessage playerInfo(String name) {
        return new ChessMessage(MessageType.PLAYER_INFO, null, null, 0, 0, null, name);
    }

    public static ChessMessage simple(MessageType type) {
        return new ChessMessage(type, null, null, 0, 0, null, null);
    }
}

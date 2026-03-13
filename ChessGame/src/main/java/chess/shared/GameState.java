package chess.shared;

import java.io.Serializable;

public class GameState implements Serializable {
    private static final long serialVersionUID = 1L;

    public final String fen;
    public final long   whiteMillis;
    public final long   blackMillis;

    public GameState(String fen, long whiteMillis, long blackMillis) {
        this.fen         = fen;
        this.whiteMillis = whiteMillis;
        this.blackMillis = blackMillis;
    }

    // Format ms as mm:ss for display
    public static String formatTime(long millis) {
        long totalSeconds = millis / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
}

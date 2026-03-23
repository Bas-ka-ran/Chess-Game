package chess.shared;

public enum MessageType {
    PLAYER_INFO,
    ASSIGN_COLOR,
    GAME_START,
    MOVE,
    BOARD_UPDATE,
    CLOCK_UPDATE,
    GAME_OVER,
    INVALID_MOVE,
    OPPONENT_DISCONNECTED,
    RESIGN,           // client → server: I give up
    DRAW_REQUEST,     // client → server: I want a draw
    DRAW_ACCEPT,      // client → server: I accept the draw
    DRAW_DECLINE      // client → server: I decline the draw
}

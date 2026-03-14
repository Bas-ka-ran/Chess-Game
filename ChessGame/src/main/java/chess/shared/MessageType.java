package chess.shared;

public enum MessageType {
    PLAYER_INFO,           // client → server: send my name
    ASSIGN_COLOR,          // server → client: your color + opponent name
    GAME_START,
    MOVE,
    BOARD_UPDATE,
    CLOCK_UPDATE,
    GAME_OVER,
    INVALID_MOVE,
    OPPONENT_DISCONNECTED
}

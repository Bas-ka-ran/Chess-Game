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
    RESIGN,           
    DRAW_REQUEST,     
    DRAW_ACCEPT,      
    DRAW_DECLINE
}

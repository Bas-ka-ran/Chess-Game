package chess.client;

import chess.shared.ChessMessage;

public interface MessageListener {
    void onMessage(ChessMessage msg);
}
package chess.server;

import chess.shared.ChessMessage;
import chess.shared.MessageType;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.move.Move;

import java.io.IOException;
import java.net.Socket;
import java.util.List;

public class GameSession implements Runnable {

    // chesslib standard starting FEN as a literal (avoids constant name issues)
    private static final String START_FEN =
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    private final ClientHandler[] handlers     = new ClientHandler[2];
    private final String[]        names        = {"Player 1", "Player 2"};
    private final boolean[]       nameReceived = {false, false};
    private final Board           board        = new Board();
    private final long[]          clocks;
    private long    lastTickNanos;
    private boolean gameOver    = false;
    private String  lastMoveUci = null;

    public GameSession(Socket s1, Socket s2, long timeLimitMs) throws IOException {
        handlers[0] = new ClientHandler(s1);
        handlers[1] = new ClientHandler(s2);
        handlers[0].setSession(this);
        handlers[1].setSession(this);
        clocks = new long[]{ timeLimitMs, timeLimitMs };
    }

    @Override
    public void run() {
        board.loadFromFen(START_FEN);
        // Start handler threads — each will send PLAYER_INFO first
        new Thread(handlers[0]).start();
        new Thread(handlers[1]).start();
    }

    public synchronized void onMessage(ClientHandler sender, ChessMessage msg) {

        // ── Handle name registration ──
        if (msg.type == MessageType.PLAYER_INFO) {
            int idx = (sender == handlers[0]) ? 0 : 1;
            names[idx] = (msg.playerName != null && !msg.playerName.isBlank())
                         ? msg.playerName : "Player " + (idx + 1);
            nameReceived[idx] = true;
            System.out.println("Player " + (idx + 1) + " registered as: " + names[idx]);
            if (nameReceived[0] && nameReceived[1]) startGame();
            return;
        }

        if (gameOver) return;
        if (msg.type != MessageType.MOVE) return;

        int  senderIdx = (sender == handlers[0]) ? 0 : 1;
        Side expected  = (senderIdx == 0) ? Side.WHITE : Side.BLACK;

        // Wrong turn?
        if (board.getSideToMove() != expected) {
            sender.send(ChessMessage.simple(MessageType.INVALID_MOVE));
            return;
        }

        // Deduct clock
        long now = System.nanoTime();
        clocks[senderIdx] -= (now - lastTickNanos) / 1_000_000L;
        lastTickNanos = now;

        if (clocks[senderIdx] <= 0) {
            clocks[senderIdx] = 0;
            broadcastGameOver(senderIdx == 0 ? "0-1" : "1-0");
            return;
        }

        // Validate move — legalMoves() returns List<Move>, not MoveList
        List<Move> legal = board.legalMoves();
        Move move = new Move(msg.moveUci, board.getSideToMove());

        if (!legal.contains(move)) {
            sender.send(ChessMessage.simple(MessageType.INVALID_MOVE));
            return;
        }

        lastMoveUci = msg.moveUci;
        board.doMove(move);

        if (checkEndConditions()) return;
        broadcastUpdate();
    }

    public synchronized void onDisconnect(ClientHandler disconnected) {
        if (gameOver) return;
        gameOver = true;
        ClientHandler other = (disconnected == handlers[0]) ? handlers[1] : handlers[0];
        other.send(ChessMessage.simple(MessageType.OPPONENT_DISCONNECTED));
    }

    // ── Private helpers ──

    private void startGame() {
        handlers[0].send(ChessMessage.assignColor("WHITE", names[1]));
        handlers[1].send(ChessMessage.assignColor("BLACK", names[0]));
        handlers[0].send(ChessMessage.simple(MessageType.GAME_START));
        handlers[1].send(ChessMessage.simple(MessageType.GAME_START));
        broadcastUpdate();
        lastTickNanos = System.nanoTime();
        System.out.println("Game started: " + names[0] + " (White) vs " + names[1] + " (Black)");
    }

    private void broadcastUpdate() {
        ChessMessage update = ChessMessage.boardUpdate(
            board.getFen(), clocks[0], clocks[1], lastMoveUci
        );
        handlers[0].send(update);
        handlers[1].send(update);
    }

    private void broadcastGameOver(String result) {
        gameOver = true;
        ChessMessage msg = ChessMessage.gameOver(
            board.getFen(), clocks[0], clocks[1], result
        );
        handlers[0].send(msg);
        handlers[1].send(msg);
        System.out.println("Game over: " + result);
    }

    private boolean checkEndConditions() {
        String result = null;
        if      (board.isMated())                   result = (board.getSideToMove() == Side.WHITE) ? "0-1" : "1-0";
        else if (board.isStaleMate())               result = "1/2-1/2";
        else if (board.isInsufficientMaterial())    result = "1/2-1/2";
        else if (board.isRepetition())              result = "1/2-1/2";
        else if (board.getHalfMoveCounter() >= 100) result = "1/2-1/2";
        if (result != null) { broadcastGameOver(result); return true; }
        return false;
    }
}

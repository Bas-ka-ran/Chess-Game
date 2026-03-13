package chess.server;

import chess.shared.ChessMessage;
import chess.shared.MessageType;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.move.Move;
import java.util.List;

import java.io.IOException;
import java.net.Socket;

public class GameSession implements Runnable {

    private final ClientHandler[] handlers = new ClientHandler[2];
    private final Board  board  = new Board();
    private final long[] clocks;          // [0]=White ms, [1]=Black ms
    private long lastTickNanos;
    private boolean gameOver = false;

    public GameSession(Socket s1, Socket s2, long timeLimitMs) throws IOException {
        handlers[0] = new ClientHandler(s1); // White
        handlers[1] = new ClientHandler(s2); // Black
        handlers[0].setSession(this);
        handlers[1].setSession(this);
        clocks = new long[]{ timeLimitMs, timeLimitMs };
    }

    @Override
    public void run() {
        // Load standard starting position
        board.loadFromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");

        // Tell each client their colour
        handlers[0].send(ChessMessage.assignColor("WHITE"));
        handlers[1].send(ChessMessage.assignColor("BLACK"));

        // Signal game start + send initial board state
        handlers[0].send(ChessMessage.simple(MessageType.GAME_START));
        handlers[1].send(ChessMessage.simple(MessageType.GAME_START));
        broadcastUpdate();

        lastTickNanos = System.nanoTime();

        // Start handler threads (they block reading from clients)
        new Thread(handlers[0]).start();
        new Thread(handlers[1]).start();
    }

    // ── Task 3.4 + 3.5 + 3.6 + 3.7: move validation, clocks, broadcast ──
    public synchronized void onMessage(ClientHandler sender, ChessMessage msg) {
        if (gameOver) return;
        if (msg.type != MessageType.MOVE) return;

        int senderIdx = (sender == handlers[0]) ? 0 : 1;
        Side expected = (senderIdx == 0) ? Side.WHITE : Side.BLACK;

        // Wrong turn?
        if (board.getSideToMove() != expected) {
            sender.send(ChessMessage.simple(MessageType.INVALID_MOVE));
            return;
        }

        // ── Task 3.5: deduct clock time ──
        long now = System.nanoTime();
        clocks[senderIdx] -= (now - lastTickNanos) / 1_000_000L;
        lastTickNanos = now;

        // Flag fall (time out)?
        if (clocks[senderIdx] <= 0) {
            clocks[senderIdx] = 0;
            broadcastGameOver(senderIdx == 0 ? "0-1" : "1-0");
            return;
        }

        // ── Task 3.4: validate move against legal moves ──
        List<Move> legal = board.legalMoves();
        Move move = new Move(msg.moveUci, board.getSideToMove());

        if (!legal.contains(move)) {
            sender.send(ChessMessage.simple(MessageType.INVALID_MOVE));
            return;
        }

        board.doMove(move);

        // ── Task 3.7: check end conditions ──
        if (checkEndConditions()) return;

        // ── Task 3.6: broadcast updated board to both clients ──
        broadcastUpdate();
    }

    // ── Task 3.8: handle disconnection ──
    public synchronized void onDisconnect(ClientHandler disconnected) {
        if (gameOver) return;
        gameOver = true;
        ClientHandler other = (disconnected == handlers[0]) ? handlers[1] : handlers[0];
        other.send(ChessMessage.simple(MessageType.OPPONENT_DISCONNECTED));
    }

    // ── Helpers ──

    private void broadcastUpdate() {
        ChessMessage update = ChessMessage.boardUpdate(
            board.getFen(), clocks[0], clocks[1]
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

    // Returns true if game ended
    private boolean checkEndConditions() {
        String result = null;

        if (board.isMated()) {
            // The side that just moved wins
            result = (board.getSideToMove() == Side.WHITE) ? "0-1" : "1-0";
        } else if (board.isStaleMate()) {
            result = "1/2-1/2";
        } else if (board.isInsufficientMaterial()) {
            result = "1/2-1/2";
        } else if (board.isRepetition()) {
            result = "1/2-1/2";
        } else if (board.getHalfMoveCounter() >= 100) {
            result = "1/2-1/2";
        }

        if (result != null) {
            broadcastGameOver(result);
            return true;
        }
        return false;
    }
}

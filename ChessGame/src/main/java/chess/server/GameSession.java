package chess.server;

import chess.shared.ChessMessage;
import chess.shared.MessageType;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.move.Move;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class GameSession implements Runnable {

    private final ClientHandler[] handlers = new ClientHandler[2];
    private final Board  board  = new Board();
    private final long[] clocks;           // [0]=White ms, [1]=Black ms
    private long lastTickNanos;
    private boolean gameOver = false;

    // ── NEW: background scheduler for clock expiry ──
    private final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor();

    public GameSession(Socket s1, Socket s2, long timeLimitMs) throws IOException {
        handlers[0] = new ClientHandler(s1); // White
        handlers[1] = new ClientHandler(s2); // Black
        handlers[0].setSession(this);
        handlers[1].setSession(this);
        clocks = new long[]{ timeLimitMs, timeLimitMs };
    }

    @Override
    public void run() {
        board.loadFromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");

        handlers[0].send(new ChessMessage(MessageType.ASSIGN_COLOR, "WHITE", null, 0, 0, null));
        handlers[1].send(new ChessMessage(MessageType.ASSIGN_COLOR, "BLACK", null, 0, 0, null));
        handlers[0].send(ChessMessage.simple(MessageType.GAME_START));
        handlers[1].send(ChessMessage.simple(MessageType.GAME_START));
        broadcastUpdate();

        lastTickNanos = System.nanoTime();

        // Start client handler threads
        new Thread(handlers[0]).start();
        new Thread(handlers[1]).start();

        // ── NEW: check clock every second ──
        // If the active player's time hits zero, opponent wins immediately
        scheduler.scheduleAtFixedRate(this::checkClockExpiry, 1, 1, TimeUnit.SECONDS);
    }

    // ── NEW: periodic clock expiry check ──
    private synchronized void checkClockExpiry() {
        if (gameOver) {
            scheduler.shutdown();
            return;
        }

        // Figure out which player's clock is currently ticking
        int activeIdx = (board.getSideToMove() == Side.WHITE) ? 0 : 1;

        // How much time has elapsed since the last move?
        long now     = System.nanoTime();
        long elapsed = (now - lastTickNanos) / 1_000_000L; // ms
        long remaining = clocks[activeIdx] - elapsed;

        if (remaining <= 0) {
            clocks[activeIdx] = 0;
            // Active player ran out of time → opponent wins
            String result = (activeIdx == 0) ? "0-1" : "1-0";
            System.out.println("Time out! Player " + activeIdx
                + " ran out of time. Result: " + result);
            broadcastGameOver(result);
            scheduler.shutdown();
        }
    }

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

        // Deduct elapsed time from the player who just moved
        long now = System.nanoTime();
        clocks[senderIdx] -= (now - lastTickNanos) / 1_000_000L;
        lastTickNanos = now;

        // Flag fall on move receipt
        if (clocks[senderIdx] <= 0) {
            clocks[senderIdx] = 0;
            broadcastGameOver(senderIdx == 0 ? "0-1" : "1-0");
            return;
        }

        // Validate move
        java.util.List<Move> legal = board.legalMoves();
        Move move = new Move(msg.moveUci, board.getSideToMove());

        if (!legal.contains(move)) {
            sender.send(ChessMessage.simple(MessageType.INVALID_MOVE));
            return;
        }

        board.doMove(move);

        if (checkEndConditions()) return;

        broadcastUpdate();
    }

    public synchronized void onDisconnect(ClientHandler disconnected) {
        if (gameOver) return;
        gameOver = true;
        scheduler.shutdown();
        ClientHandler other = (disconnected == handlers[0]) ? handlers[1] : handlers[0];
        other.send(ChessMessage.simple(MessageType.OPPONENT_DISCONNECTED));
    }

    // ── Helpers ──

    private void broadcastUpdate() {
        ChessMessage update = new ChessMessage(
            MessageType.BOARD_UPDATE, null, board.getFen(), clocks[0], clocks[1], null
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

        if (board.isMated()) {
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

package chess.server;

import chess.shared.ChessMessage;
import chess.shared.MessageType;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.move.Move;

import java.io.IOException;
import java.net.Socket;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class GameSession implements Runnable {

    // ── FIX: use literal FEN string ──
    private static final String START_FEN =
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    private static final long FIRST_MOVE_TIMEOUT_MS = 30_000L;

    private final ClientHandler[] handlers = new ClientHandler[2];
    private final Board  board  = new Board();
    private final long[] clocks;
    private long    lastTickNanos;
    private boolean gameOver      = false;
    private boolean firstMoveMade = false;

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

        // ── FIX: assignColor(color, fen) — two args ──
        handlers[0].send(ChessMessage.assignColor("WHITE", START_FEN));
        handlers[1].send(ChessMessage.assignColor("BLACK", START_FEN));
        handlers[0].send(ChessMessage.simple(MessageType.GAME_START));
        handlers[1].send(ChessMessage.simple(MessageType.GAME_START));
        broadcastUpdate();

        lastTickNanos = System.nanoTime();

        new Thread(handlers[0]).start();
        new Thread(handlers[1]).start();

        // 30-second first-move timeout
        Timer firstMoveTimer = new Timer("FirstMoveTimer", true);
        firstMoveTimer.schedule(new TimerTask() {
            @Override public void run() {
                synchronized (GameSession.this) {
                    if (!firstMoveMade && !gameOver) {
                        System.out.println("White did not move in 30s — Black wins!");
                        broadcastGameOver("0-1");
                    }
                }
            }
        }, FIRST_MOVE_TIMEOUT_MS);
    }

    public synchronized void onMessage(ClientHandler sender, ChessMessage msg) {
        if (gameOver) return;
        if (msg.type != MessageType.MOVE) return;

        int  senderIdx = (sender == handlers[0]) ? 0 : 1;
        Side expected  = (senderIdx == 0) ? Side.WHITE : Side.BLACK;

        if (board.getSideToMove() != expected) {
            sender.send(ChessMessage.simple(MessageType.INVALID_MOVE));
            return;
        }

        firstMoveMade = true;

        long now = System.nanoTime();
        clocks[senderIdx] -= (now - lastTickNanos) / 1_000_000L;
        lastTickNanos = now;

        if (clocks[senderIdx] <= 0) {
            clocks[senderIdx] = 0;
            broadcastGameOver(senderIdx == 0 ? "0-1" : "1-0");
            return;
        }

        // ── FIX: legalMoves() returns List<Move> ──
        List<Move> legal = board.legalMoves();
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
        ClientHandler other = (disconnected == handlers[0]) ? handlers[1] : handlers[0];
        other.send(ChessMessage.simple(MessageType.OPPONENT_DISCONNECTED));
    }

    private void broadcastUpdate() {
        // ── FIX: boardUpdate takes 4 args (fen, wMs, bMs, result=null) ──
        handlers[0].send(ChessMessage.boardUpdate(board.getFen(), clocks[0], clocks[1], null));
        handlers[1].send(ChessMessage.boardUpdate(board.getFen(), clocks[0], clocks[1], null));
    }

    private void broadcastGameOver(String result) {
        gameOver = true;
        ChessMessage msg = ChessMessage.gameOver(board.getFen(), clocks[0], clocks[1], result);
        handlers[0].send(msg);
        handlers[1].send(msg);
        System.out.println("Game over: " + result);
    }

    private boolean checkEndConditions() {
        String result = null;
        if      (board.isMated())                   result = board.getSideToMove() == Side.WHITE ? "0-1" : "1-0";
        else if (board.isStaleMate())               result = "1/2-1/2";
        else if (board.isInsufficientMaterial())    result = "1/2-1/2";
        else if (board.isRepetition())              result = "1/2-1/2";
        else if (board.getHalfMoveCounter() >= 100) result = "1/2-1/2";
        if (result != null) { broadcastGameOver(result); return true; }
        return false;
    }
}

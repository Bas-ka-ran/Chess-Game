package chess.server;

import chess.shared.ChessMessage;
import chess.shared.MessageType;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.move.Move;

import java.io.IOException;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.*;

public class GameSession implements Runnable {

    private static final String START_FEN =
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    private static final long FIRST_MOVE_TIMEOUT_MS = 30_000L;

    private final ClientHandler[]        handlers     = new ClientHandler[2];
    private final String[]               names        = {"Player 1", "Player 2"};
    private final boolean[]              nameReceived = {false, false};
    private final Board                  board        = new Board();
    private final long[]                 clocks;

    // Fix: lastTickNanos only set AFTER first move — 30s is free
    private long    lastTickNanos     = 0;
    private boolean gameOver          = false;
    private boolean firstMoveReceived = false;
    private boolean drawPending       = false;   // true while draw offer is open
    private int     drawRequesterIdx  = -1;      // who sent the draw request
    private String  lastMoveUci       = null;

    private final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> firstMoveTimer = null;
    private ScheduledFuture<?> clockWatchdog  = null;

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
        new Thread(handlers[0]).start();
        new Thread(handlers[1]).start();
    }

    public synchronized void onMessage(ClientHandler sender, ChessMessage msg) {
        int senderIdx = (sender == handlers[0]) ? 0 : 1;

        // ── Player name handshake ──
        if (msg.type == MessageType.PLAYER_INFO) {
            names[senderIdx] = (msg.playerName != null && !msg.playerName.isBlank())
                               ? msg.playerName : "Player " + (senderIdx + 1);
            nameReceived[senderIdx] = true;
            System.out.println("Name registered: " + names[senderIdx]);
            if (nameReceived[0] && nameReceived[1]) startGame();
            return;
        }

        if (gameOver) return;

        switch (msg.type) {

            // ── Move ──
            case MOVE -> {
                Side expected = (senderIdx == 0) ? Side.WHITE : Side.BLACK;
                if (board.getSideToMove() != expected) {
                    sender.send(ChessMessage.simple(MessageType.INVALID_MOVE));
                    return;
                }

                // Cancel first-move timer; start main clocks from now
                if (!firstMoveReceived) {
                    firstMoveReceived = true;
                    if (firstMoveTimer != null) firstMoveTimer.cancel(false);
                    // ── Fix: main clock only starts from first move ──
                    lastTickNanos = System.nanoTime();
                    startClockWatchdog();
                }

                // Deduct time
                long now = System.nanoTime();
                clocks[senderIdx] -= (now - lastTickNanos) / 1_000_000L;
                lastTickNanos = now;

                if (clocks[senderIdx] <= 0) {
                    clocks[senderIdx] = 0;
                    broadcastGameOver(senderIdx == 0 ? "0-1 (time)" : "1-0 (time)");
                    return;
                }

                // Validate move
                List<Move> legal = board.legalMoves();
                Move move = new Move(msg.moveUci, board.getSideToMove());
                if (!legal.contains(move)) {
                    sender.send(ChessMessage.simple(MessageType.INVALID_MOVE));
                    return;
                }

                lastMoveUci = msg.moveUci;
                board.doMove(move);
                drawPending = false; // move cancels any open draw offer

                if (checkEndConditions()) return;
                broadcastUpdate();
            }

            // ── Resign ──
            case RESIGN -> {
                // Sender resigns — opponent wins
                String result = (senderIdx == 0) ? "0-1 (resignation)" : "1-0 (resignation)";
                System.out.println(names[senderIdx] + " resigned.");
                broadcastGameOver(result);
            }

            // ── Draw request ──
            case DRAW_REQUEST -> {
                if (drawPending) return; // ignore duplicate requests
                drawPending      = true;
                drawRequesterIdx = senderIdx;
                // Forward to opponent
                int opponentIdx = 1 - senderIdx;
                handlers[opponentIdx].send(ChessMessage.drawRequest(names[senderIdx]));
                System.out.println(names[senderIdx] + " offered a draw.");
            }

            // ── Draw accepted ──
            case DRAW_ACCEPT -> {
                if (!drawPending) return;
                // Only the non-requester can accept
                if (senderIdx == drawRequesterIdx) return;
                drawPending = false;
                System.out.println(names[senderIdx] + " accepted the draw.");
                broadcastGameOver("1/2-1/2 (agreement)");
            }

            // ── Draw declined ──
            case DRAW_DECLINE -> {
                if (!drawPending) return;
                if (senderIdx == drawRequesterIdx) return;
                drawPending = false;
                // Notify requester that draw was declined
                handlers[drawRequesterIdx].send(
                    ChessMessage.simple(MessageType.DRAW_DECLINE));
                System.out.println(names[senderIdx] + " declined the draw.");
            }

            default -> {}
        }
    }

    public synchronized void onDisconnect(ClientHandler disconnected) {
        if (gameOver) return;
        gameOver = true;
        shutdown();
        int otherIdx = (disconnected == handlers[0]) ? 1 : 0;
        handlers[otherIdx].send(ChessMessage.simple(MessageType.OPPONENT_DISCONNECTED));
    }

    // ── Private helpers ──

    private void startGame() {
        handlers[0].send(ChessMessage.assignColor("WHITE", names[1]));
        handlers[1].send(ChessMessage.assignColor("BLACK", names[0]));
        handlers[0].send(ChessMessage.simple(MessageType.GAME_START));
        handlers[1].send(ChessMessage.simple(MessageType.GAME_START));
        broadcastUpdate();

        // ── Fix: 30s first-move timeout — main clocks NOT started yet ──
        firstMoveTimer = scheduler.schedule(() -> {
            synchronized (this) {
                if (!firstMoveReceived && !gameOver) {
                    System.out.println("First move timeout — White forfeits.");
                    broadcastGameOver("0-1 (no first move in 30s)");
                }
            }
        }, FIRST_MOVE_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        System.out.println("Game: " + names[0] + " (W) vs " + names[1] + " (B)");
    }

    // Clock watchdog only starts after the first move
    private void startClockWatchdog() {
        clockWatchdog = scheduler.scheduleAtFixedRate(() -> {
            synchronized (this) {
                if (gameOver || !firstMoveReceived) return;
                int activeIdx = (board.getSideToMove() == Side.WHITE) ? 0 : 1;
                long elapsed  = (System.nanoTime() - lastTickNanos) / 1_000_000L;
                if (clocks[activeIdx] - elapsed <= 0) {
                    clocks[activeIdx] = 0;
                    broadcastGameOver(activeIdx == 0 ? "0-1 (time)" : "1-0 (time)");
                }
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    private void broadcastUpdate() {
        ChessMessage update = ChessMessage.boardUpdate(
            board.getFen(), clocks[0], clocks[1], lastMoveUci);
        handlers[0].send(update);
        handlers[1].send(update);
    }

    private void broadcastGameOver(String result) {
        gameOver = true;
        shutdown();
        ChessMessage msg = ChessMessage.gameOver(
            board.getFen(), clocks[0], clocks[1], result);
        handlers[0].send(msg);
        handlers[1].send(msg);
        System.out.println("Game over: " + result);
    }

    private boolean checkEndConditions() {
        String result = null;
        if      (board.isMated())                   result = (board.getSideToMove() == Side.WHITE) ? "0-1" : "1-0";
        else if (board.isStaleMate())                result = "1/2-1/2";
        else if (board.isInsufficientMaterial())     result = "1/2-1/2";
        else if (board.isRepetition())               result = "1/2-1/2";
        else if (board.getHalfMoveCounter() >= 100)  result = "1/2-1/2";
        if (result != null) { broadcastGameOver(result); return true; }
        return false;
    }

    private void shutdown() {
        if (firstMoveTimer != null) firstMoveTimer.cancel(false);
        if (clockWatchdog  != null) clockWatchdog.cancel(false);
        scheduler.shutdown();
    }
}

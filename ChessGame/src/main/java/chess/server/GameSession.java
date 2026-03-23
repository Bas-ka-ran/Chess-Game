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

    private final ClientHandler[] handlers     = new ClientHandler[2];
    private final String[]        names        = {"Player 1", "Player 2"};
    private final boolean[]       nameReceived = {false, false};

    // Per-player first move tracking
    // [0] = White, [1] = Black
    private final boolean[]              firstMoveDone  = {false, false};
    private final ScheduledFuture<?>[]   firstMoveTimer = new ScheduledFuture<?>[2];

    private final Board  board  = new Board();
    private final long[] clocks;

    private long    lastTickNanos  = 0;
    private boolean gameOver       = false;
    private boolean mainClockStarted = false; // true only after BOTH first moves done
    private boolean drawPending    = false;
    private int     drawRequesterIdx = -1;
    private String  lastMoveUci    = null;

    private final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> clockWatchdog = null;

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

        // ── Name handshake ──
        if (msg.type == MessageType.PLAYER_INFO) {
            names[senderIdx] = (msg.playerName != null && !msg.playerName.isBlank())
                               ? msg.playerName : "Player " + (senderIdx + 1);
            nameReceived[senderIdx] = true;
            if (nameReceived[0] && nameReceived[1]) startGame();
            return;
        }

        if (gameOver) return;

        switch (msg.type) {

            case MOVE -> {
                Side expected = (senderIdx == 0) ? Side.WHITE : Side.BLACK;
                if (board.getSideToMove() != expected) {
                    sender.send(ChessMessage.simple(MessageType.INVALID_MOVE));
                    return;
                }

                // ── First move handling (free 30s, no clock deduction) ──
                if (!firstMoveDone[senderIdx]) {
                    firstMoveDone[senderIdx] = true;

                    // Cancel this player's 30s timer
                    if (firstMoveTimer[senderIdx] != null)
                        firstMoveTimer[senderIdx].cancel(false);

                    // If BOTH players have now made their first move → start main clocks
                    if (firstMoveDone[0] && firstMoveDone[1]) {
                        lastTickNanos    = System.nanoTime();
                        mainClockStarted = true;
                        startClockWatchdog();
                        System.out.println("Main clocks started.");
                    } else {
                        // This was White's first move — start Black's 30s timer
                        int nextIdx = 1 - senderIdx;
                        startFirstMoveTimer(nextIdx);
                    }

                    // Validate and apply move (no time deducted)
                    List<Move> legal = board.legalMoves();
                    Move move = new Move(msg.moveUci, board.getSideToMove());
                    if (!legal.contains(move)) {
                        sender.send(ChessMessage.simple(MessageType.INVALID_MOVE));
                        // Undo first move flag since move was invalid
                        firstMoveDone[senderIdx] = false;
                        return;
                    }
                    lastMoveUci = msg.moveUci;
                    board.doMove(move);
                    drawPending = false;
                    if (checkEndConditions()) return;
                    broadcastUpdate();
                    return;
                }

                // ── Normal move: deduct from main clock ──
                long now = System.nanoTime();
                clocks[senderIdx] -= (now - lastTickNanos) / 1_000_000L;
                lastTickNanos = now;

                if (clocks[senderIdx] <= 0) {
                    clocks[senderIdx] = 0;
                    broadcastGameOver(senderIdx == 0 ? "0-1 (time)" : "1-0 (time)");
                    return;
                }

                List<Move> legal = board.legalMoves();
                Move move = new Move(msg.moveUci, board.getSideToMove());
                if (!legal.contains(move)) {
                    sender.send(ChessMessage.simple(MessageType.INVALID_MOVE));
                    return;
                }

                lastMoveUci = msg.moveUci;
                board.doMove(move);
                drawPending = false;
                if (checkEndConditions()) return;
                broadcastUpdate();
            }

            case RESIGN -> {
                String result = (senderIdx == 0)
                    ? "0-1 (resignation)" : "1-0 (resignation)";
                System.out.println(names[senderIdx] + " resigned.");
                broadcastGameOver(result);
            }

            case DRAW_REQUEST -> {
                if (drawPending) return;
                drawPending      = true;
                drawRequesterIdx = senderIdx;
                handlers[1 - senderIdx].send(
                    ChessMessage.drawRequest(names[senderIdx]));
            }

            case DRAW_ACCEPT -> {
                if (!drawPending || senderIdx == drawRequesterIdx) return;
                drawPending = false;
                broadcastGameOver("1/2-1/2 (agreement)");
            }

            case DRAW_DECLINE -> {
                if (!drawPending || senderIdx == drawRequesterIdx) return;
                drawPending = false;
                handlers[drawRequesterIdx].send(
                    ChessMessage.simple(MessageType.DRAW_DECLINE));
            }

            default -> {}
        }
    }

    public synchronized void onDisconnect(ClientHandler disconnected) {
        if (gameOver) return;
        gameOver = true;
        shutdown();
        int otherIdx = (disconnected == handlers[0]) ? 1 : 0;
        handlers[otherIdx].send(
            ChessMessage.simple(MessageType.OPPONENT_DISCONNECTED));
    }

    // ── Private helpers ──

    private void startGame() {
        handlers[0].send(ChessMessage.assignColor("WHITE", names[1]));
        handlers[1].send(ChessMessage.assignColor("BLACK", names[0]));
        handlers[0].send(ChessMessage.simple(MessageType.GAME_START));
        handlers[1].send(ChessMessage.simple(MessageType.GAME_START));
        broadcastUpdate();

        // White goes first — start White's 30s free timer
        startFirstMoveTimer(0);

        System.out.println("Game: " + names[0] + " (W) vs " + names[1] + " (B)");
    }

    // Start a 30s timeout for player [idx]'s first move
    private void startFirstMoveTimer(int idx) {
        System.out.println("30s first-move timer started for " + names[idx]);
        firstMoveTimer[idx] = scheduler.schedule(() -> {
            synchronized (this) {
                if (!firstMoveDone[idx] && !gameOver) {
                    System.out.println(names[idx] + " timed out on first move.");
                    // idx timed out → opponent wins
                    String result = (idx == 0)
                        ? "0-1 (no first move in 30s)"
                        : "1-0 (no first move in 30s)";
                    broadcastGameOver(result);
                }
            }
        }, FIRST_MOVE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    // Real-time clock watchdog — only runs after both first moves done
    private void startClockWatchdog() {
        clockWatchdog = scheduler.scheduleAtFixedRate(() -> {
            synchronized (this) {
                if (gameOver || !mainClockStarted) return;
                int activeIdx = (board.getSideToMove() == Side.WHITE) ? 0 : 1;
                long elapsed  = (System.nanoTime() - lastTickNanos) / 1_000_000L;
                if (clocks[activeIdx] - elapsed <= 0) {
                    clocks[activeIdx] = 0;
                    broadcastGameOver(activeIdx == 0
                        ? "0-1 (time)" : "1-0 (time)");
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
        for (ScheduledFuture<?> t : firstMoveTimer)
            if (t != null) t.cancel(false);
        if (clockWatchdog != null) clockWatchdog.cancel(false);
        scheduler.shutdown();
    }
}

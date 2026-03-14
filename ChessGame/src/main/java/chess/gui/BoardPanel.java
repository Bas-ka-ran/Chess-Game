package chess.gui;

import chess.client.ChessClient;
import com.github.bhlangonijr.chesslib.*;
import com.github.bhlangonijr.chesslib.move.Move;
import com.github.bhlangonijr.chesslib.move.MoveList;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.HashSet;
import java.util.Set;

public class BoardPanel extends JPanel {

    // ── Colors ──
    private static final Color LIGHT      = new Color(240, 217, 181);
    private static final Color DARK       = new Color(181, 136, 99);
    private static final Color YELLOW     = new Color(255, 255,   0, 120);
    private static final Color GREEN_DOT  = new Color(  0, 180,   0, 160);
    private static final Color BLUE       = new Color( 70, 130, 180, 110);
    private static final Color RED_FLASH  = new Color(220,  50,  50, 150);

    // ── State ──
    private final Board       board    = new Board();
    private final Side        myColor;
    private final ChessClient client;

    private Square     selectedSquare = null;
    private Set<Square> legalTargets  = new HashSet<>();
    private Move       lastMove       = null;
    private boolean    locked         = false;
    private boolean    flashRed       = false;

    public BoardPanel(Side myColor, ChessClient client) {
        this.myColor = myColor;
        this.client  = client;
        board.loadFromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
        setPreferredSize(new Dimension(560, 560));
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { handleClick(e); }
        });
    }

    // ── Called by MainFrame on BOARD_UPDATE ──
    public void updateFromFen(String fen) {
        board.loadFromFen(fen);
        // Store last move for blue highlight
        java.util.LinkedList<com.github.bhlangonijr.chesslib.MoveBackup> history = board.getBackup();
        if (!history.isEmpty()) {
            lastMove = history.getLast().getMove();
        }
        selectedSquare = null;
        legalTargets.clear();
        flashRed = false;
        repaint();
    }

    // ── Called on INVALID_MOVE ──
    public void flashInvalid() {
        flashRed = true;
        repaint();
        // Clear flash after 400ms
        Timer t = new Timer(400, e -> { flashRed = false; repaint(); });
        t.setRepeats(false);
        t.start();
    }

    public void setLocked(boolean locked) { this.locked = locked; }

    // ─────────────────────────────────────────
    // Task 5.1 + 5.3 + 5.5 + 5.11 : PAINTING
    // ─────────────────────────────────────────
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
        int sz = getWidth() / 8;

        for (Square sq : Square.values()) {
            if (sq == Square.NONE) continue;

            // Task 5.1: compute screen col/row, flip for Black
            int col = sq.getFile().ordinal();
            int row = 7 - sq.getRank().ordinal();
            if (myColor == Side.BLACK) { col = 7 - col; row = 7 - row; }

            int x = col * sz;
            int y = row * sz;

            // Draw base square color
            g2.setColor((col + row) % 2 == 0 ? LIGHT : DARK);
            g2.fillRect(x, y, sz, sz);

            // Task 5.11: last-move blue highlight
            if (lastMove != null &&
               (sq.equals(lastMove.getFrom()) || sq.equals(lastMove.getTo()))) {
                drawOverlay(g2, x, y, sz, BLUE);
            }

            // Task 5.5: selected square yellow highlight
            if (sq.equals(selectedSquare)) {
                Color highlight = flashRed ? RED_FLASH : YELLOW;
                drawOverlay(g2, x, y, sz, highlight);
            }

            // Task 5.5: legal target green dots
            if (legalTargets.contains(sq)) {
                drawDot(g2, x, y, sz);
            }

            // Task 5.3: draw piece symbol
            Piece p = board.getPiece(sq);
            if (p != Piece.NONE) {
                drawPiece(g2, PieceRenderer.getSymbol(p), x, y, sz);
            }
        }

        // Draw rank/file labels (a-h, 1-8)
        drawCoordinates(g2, sz);
    }

    // ─────────────────────────────────────────
    // Task 5.4 : CLICK TO MOVE
    // ─────────────────────────────────────────
    private void handleClick(MouseEvent e) {
        if (locked) return;
        if (board.getSideToMove() != myColor) return; // not my turn

        int sz  = getWidth() / 8;
        int col = e.getX() / sz;
        int row = e.getY() / sz;

        // Convert screen position back to Square
        int fileIdx = (myColor == Side.BLACK) ? (7 - col) : col;
        int rankIdx = (myColor == Side.BLACK) ? row : (7 - row);

        File file = File.values()[fileIdx];
        Rank rank = Rank.values()[rankIdx];
        Square clicked = Square.encode(rank, file);

        if (selectedSquare == null) {
            // Click 1: select a friendly piece
            Piece p = board.getPiece(clicked);
            if (p == Piece.NONE) return;
            if (p.getPieceSide() != myColor) return;

            selectedSquare = clicked;
            legalTargets   = computeLegalTargets(clicked);
            repaint();

        } else {
            // Click 2: try to move
            if (clicked.equals(selectedSquare)) {
                // Deselect
                selectedSquare = null;
                legalTargets.clear();
                repaint();
                return;
            }

            if (legalTargets.contains(clicked)) {
                String uci = toUci(selectedSquare, clicked);
                client.sendMove(uci);
            } else {
                // Clicked another friendly piece — re-select
                Piece p = board.getPiece(clicked);
                if (p != Piece.NONE && p.getPieceSide() == myColor) {
                    selectedSquare = clicked;
                    legalTargets   = computeLegalTargets(clicked);
                    repaint();
                    return;
                }
            }

            selectedSquare = null;
            legalTargets.clear();
            repaint();
        }
    }

    // ─────────────────────────────────────────
    // Task 5.6 : PAWN PROMOTION
    // ─────────────────────────────────────────
    private String toUci(Square from, Square to) {
        String base = from.toString().toLowerCase()
                    + to.toString().toLowerCase();

        Piece p = board.getPiece(from);
        boolean isWhitePawn = p == Piece.WHITE_PAWN && to.getRank() == Rank.RANK_8;
        boolean isBlackPawn = p == Piece.BLACK_PAWN && to.getRank() == Rank.RANK_1;

        if (isWhitePawn || isBlackPawn) {
            String[] options = {"Queen", "Rook", "Bishop", "Knight"};
            int choice = JOptionPane.showOptionDialog(
                this, "Promote pawn to:",
                "Pawn Promotion", JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
            String suffix = switch (choice) {
                case 1  -> "r";
                case 2  -> "b";
                case 3  -> "n";
                default -> "q";
            };
            return base + suffix;
        }
        return base;
    }

    // ─────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────

    private Set<Square> computeLegalTargets(Square from) {
        Set<Square> targets = new HashSet<>();
        java.util.List<Move> legal = board.legalMoves();
        for (Move m : legal) {
            if (m.getFrom().equals(from)) targets.add(m.getTo());
        }
        return targets;
    }

    private void drawOverlay(Graphics2D g2, int x, int y, int sz, Color c) {
        g2.setColor(c);
        g2.fillRect(x, y, sz, sz);
    }

    private void drawDot(Graphics2D g2, int x, int y, int sz) {
        int dotSize = sz / 3;
        int offset  = (sz - dotSize) / 2;
        g2.setColor(GREEN_DOT);
        g2.fillOval(x + offset, y + offset, dotSize, dotSize);
    }

    private void drawPiece(Graphics2D g2, String symbol, int x, int y, int sz) {
        g2.setFont(new Font("Serif", Font.PLAIN, sz - 8));
        FontMetrics fm = g2.getFontMetrics();
        int tx = x + (sz - fm.stringWidth(symbol)) / 2;
        int ty = y + (sz + fm.getAscent() - fm.getDescent()) / 2 - 2;
        // Shadow for readability
        g2.setColor(new Color(0, 0, 0, 80));
        g2.drawString(symbol, tx + 1, ty + 1);
        g2.setColor(Color.BLACK);
        g2.drawString(symbol, tx, ty);
    }

    private void drawCoordinates(Graphics2D g2, int sz) {
        g2.setFont(new Font("SansSerif", Font.BOLD, 11));
        g2.setColor(new Color(100, 100, 100));
        String files = "abcdefgh";
        String ranks = "87654321";
        if (myColor == Side.BLACK) {
            files = "hgfedcba";
            ranks = "12345678";
        }
        for (int i = 0; i < 8; i++) {
            // File labels (a-h) at bottom
            g2.drawString(String.valueOf(files.charAt(i)),
                i * sz + sz - 10, getHeight() - 3);
            // Rank labels (1-8) at left
            g2.drawString(String.valueOf(ranks.charAt(i)),
                3, i * sz + 13);
        }
    }
}
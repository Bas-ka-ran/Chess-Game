package chess.gui;

import chess.client.ChessClient;
import com.github.bhlangonijr.chesslib.*;
import com.github.bhlangonijr.chesslib.move.Move;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BoardPanel extends JPanel {

    // ── FIX: use literal FEN string instead of missing constant ──
    private static final String START_FEN =
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    private static final Color LIGHT     = new Color(240, 217, 181);
    private static final Color DARK      = new Color(181, 136,  99);
    private static final Color YELLOW    = new Color(255, 255,   0, 120);
    private static final Color GREEN_DOT = new Color(  0, 180,   0, 160);
    private static final Color BLUE      = new Color( 70, 130, 180, 110);
    private static final Color RED_FLASH = new Color(220,  50,  50, 150);
    private static final Color CHECK_RED = new Color(220,  30,  30, 200);

    private final Board       board;
    private final Side        myColor;
    private final ChessClient client;

    private Square      selectedSquare = null;
    private Set<Square> legalTargets   = new HashSet<>();
    private Move        lastMove       = null;
    private boolean     locked         = false;
    private boolean     flashRed       = false;

    public BoardPanel(Side myColor, ChessClient client) {
        this.myColor = myColor;
        this.client  = client;
        this.board   = new Board();
        board.loadFromFen(START_FEN);
        setPreferredSize(new Dimension(560, 560));
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { handleClick(e); }
        });
    }

    public void updateFromFen(String fen) {
        board.loadFromFen(fen);

        // ── FIX: getBackup() returns LinkedList<MoveBackup>, not MoveList ──
        java.util.LinkedList<com.github.bhlangonijr.chesslib.MoveBackup> history
            = board.getBackup();
        if (!history.isEmpty()) {
            lastMove = history.getLast().getMove();
        }

        selectedSquare = null;
        legalTargets.clear();
        flashRed = false;
        repaint();
    }

    public void flashInvalid() {
        flashRed = true;
        repaint();
        Timer t = new Timer(400, e -> { flashRed = false; repaint(); });
        t.setRepeats(false);
        t.start();
    }

    public void setLocked(boolean locked) { this.locked = locked; }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
        int sz = getWidth() / 8;

        // Check detection
        Square kingInCheck = null;
        if (board.isKingAttacked()) {
            kingInCheck = board.getKingSquare(board.getSideToMove());
        }

        for (Square sq : Square.values()) {
            if (sq == Square.NONE) continue;

            int col = sq.getFile().ordinal();
            int row = 7 - sq.getRank().ordinal();
            if (myColor == Side.BLACK) { col = 7 - col; row = 7 - row; }

            int x = col * sz, y = row * sz;

            // Base color
            g2.setColor((col + row) % 2 == 0 ? LIGHT : DARK);
            g2.fillRect(x, y, sz, sz);

            // Last move blue
            if (lastMove != null &&
               (sq.equals(lastMove.getFrom()) || sq.equals(lastMove.getTo())))
                drawOverlay(g2, x, y, sz, BLUE);

            // King in check red
            if (sq.equals(kingInCheck))
                drawOverlay(g2, x, y, sz, CHECK_RED);

            // Selected square
            if (sq.equals(selectedSquare))
                drawOverlay(g2, x, y, sz, flashRed ? RED_FLASH : YELLOW);

            // Legal dots
            if (legalTargets.contains(sq)) drawDot(g2, x, y, sz);

            // Piece
            Piece p = board.getPiece(sq);
            if (p != Piece.NONE) drawPiece(g2, p, x, y, sz);
        }
        drawCoordinates(g2, sz);
    }

    private void handleClick(MouseEvent e) {
        if (locked) return;
        if (board.getSideToMove() != myColor) return;

        int sz      = getWidth() / 8;
        int col     = e.getX() / sz;
        int row     = e.getY() / sz;
        int fileIdx = (myColor == Side.BLACK) ? (7 - col) : col;
        int rankIdx = (myColor == Side.BLACK) ? row : (7 - row);

        Square clicked = Square.encode(Rank.values()[rankIdx], File.values()[fileIdx]);

        if (selectedSquare == null) {
            Piece p = board.getPiece(clicked);
            if (p == Piece.NONE || p.getPieceSide() != myColor) return;
            selectedSquare = clicked;
            legalTargets   = computeLegalTargets(clicked);
            repaint();
        } else {
            if (clicked.equals(selectedSquare)) {
                selectedSquare = null; legalTargets.clear(); repaint(); return;
            }
            if (legalTargets.contains(clicked)) {
                client.sendMove(toUci(selectedSquare, clicked));
            } else {
                Piece p = board.getPiece(clicked);
                if (p != Piece.NONE && p.getPieceSide() == myColor) {
                    selectedSquare = clicked;
                    legalTargets   = computeLegalTargets(clicked);
                    repaint(); return;
                }
            }
            selectedSquare = null; legalTargets.clear(); repaint();
        }
    }

    private String toUci(Square from, Square to) {
        String base  = from.toString().toLowerCase() + to.toString().toLowerCase();
        Piece  p     = board.getPiece(from);
        boolean promoW = p == Piece.WHITE_PAWN && to.getRank() == Rank.RANK_8;
        boolean promoB = p == Piece.BLACK_PAWN && to.getRank() == Rank.RANK_1;
        if (promoW || promoB) {
            String[] opts = {"Queen","Rook","Bishop","Knight"};
            int c = JOptionPane.showOptionDialog(this, "Promote pawn to:",
                "Pawn Promotion", JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE, null, opts, opts[0]);
            return base + switch(c){ case 1->"r"; case 2->"b"; case 3->"n"; default->"q"; };
        }
        return base;
    }

    private Set<Square> computeLegalTargets(Square from) {
        Set<Square> t = new HashSet<>();
        // ── FIX: legalMoves() returns List<Move> ──
        List<Move> legal = board.legalMoves();
        for (Move m : legal) if (m.getFrom().equals(from)) t.add(m.getTo());
        return t;
    }

    private void drawOverlay(Graphics2D g2, int x, int y, int sz, Color c) {
        g2.setColor(c); g2.fillRect(x, y, sz, sz);
    }

    private void drawDot(Graphics2D g2, int x, int y, int sz) {
        int dot = sz / 3, off = (sz - dot) / 2;
        g2.setColor(GREEN_DOT);
        g2.fillOval(x + off, y + off, dot, dot);
    }

    private void drawPiece(Graphics2D g2, Piece piece, int x, int y, int sz) {
        String symbol  = PieceRenderer.getSymbol(piece);
        boolean isWhite = piece.getPieceSide() == Side.WHITE;
        g2.setFont(new Font("Serif", Font.PLAIN, sz - 6));
        FontMetrics fm = g2.getFontMetrics();
        int tx = x + (sz - fm.stringWidth(symbol)) / 2;
        int ty = y + (sz + fm.getAscent() - fm.getDescent()) / 2 - 2;
        // Outline
        g2.setColor(isWhite ? new Color(0,0,0,200) : new Color(255,255,255,180));
        for (int dx = -2; dx <= 2; dx++)
            for (int dy = -2; dy <= 2; dy++)
                if (dx != 0 || dy != 0) g2.drawString(symbol, tx+dx, ty+dy);
        // Fill
        g2.setColor(isWhite ? Color.WHITE : Color.BLACK);
        g2.drawString(symbol, tx, ty);
    }

    private void drawCoordinates(Graphics2D g2, int sz) {
        g2.setFont(new Font("SansSerif", Font.BOLD, 11));
        g2.setColor(new Color(100,100,100));
        String files = myColor == Side.BLACK ? "hgfedcba" : "abcdefgh";
        String ranks = myColor == Side.BLACK ? "12345678" : "87654321";
        for (int i = 0; i < 8; i++) {
            g2.drawString(String.valueOf(files.charAt(i)), i*sz + sz-10, getHeight()-3);
            g2.drawString(String.valueOf(ranks.charAt(i)), 3, i*sz + 13);
        }
    }
}

package chess.client;

import chess.shared.ChessMessage;

import javax.swing.*;
import java.io.*;
import java.net.Socket;

public class ChessClient {

    private Socket             socket;
    private ObjectOutputStream out;
    private ObjectInputStream  in;
    private MessageListener    listener;

    public ChessClient(MessageListener listener) {
        this.listener = listener;
    }

    public void connect(String host, int port) throws IOException {
        socket = new Socket(host, port);
        out    = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        in     = new ObjectInputStream(socket.getInputStream());

        // ── NEW: tell the GUI we connected, now waiting for player 2 ──
        if (listener instanceof chess.gui.MainFrame) {
            SwingUtilities.invokeLater(
                ((chess.gui.MainFrame) listener)::showConnected
            );
        }

        new NetworkThread(in, listener).start();
    }

    public void sendMove(String uci) {
        try {
            out.writeObject(ChessMessage.move(uci));
            out.flush();
        } catch (IOException e) {
            System.err.println("Failed to send move: " + e.getMessage());
        }
    }

    public void close() {
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            String host = JOptionPane.showInputDialog(null,
                "Enter server address:", "Connect to Chess Server",
                JOptionPane.QUESTION_MESSAGE);
            if (host == null || host.isBlank()) host = "localhost";

            chess.gui.MainFrame frame = new chess.gui.MainFrame();
            ChessClient client = new ChessClient(frame);
            frame.setClient(client);
            frame.showWaiting();
            frame.setVisible(true);

            try {
                client.connect(host.trim(), 5555);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(null,
                    "Could not connect to " + host + ":5555\n" + e.getMessage(),
                    "Connection Failed", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }
}

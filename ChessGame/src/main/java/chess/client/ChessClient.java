package chess.client;

import chess.shared.ChessMessage;
import chess.shared.MessageType;

import javax.swing.*;
import java.io.*;
import java.net.Socket;

public class ChessClient {

    private Socket             socket;
    private ObjectOutputStream out;
    private ObjectInputStream  in;
    private MessageListener    listener;
    private String             myName;

    public ChessClient(MessageListener listener) {
        this.listener = listener;
    }

    public String getMyName() { return myName; }

    public void connect(String host, int port, String name) throws IOException {
        this.myName = name;
        socket = new Socket(host, port);

        out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        in  = new ObjectInputStream(socket.getInputStream());

        // Send name to server immediately
        out.writeObject(ChessMessage.playerInfo(name));
        out.flush();

        NetworkThread net = new NetworkThread(in, listener);
        net.start();
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
        try { if (socket != null) socket.close(); }
        catch (IOException ignored) {}
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {

            // Ask for server address
            String host = JOptionPane.showInputDialog(
                null, "Enter server address:", "Server", JOptionPane.QUESTION_MESSAGE);
            if (host == null || host.isBlank()) host = "localhost";

            // Ask for player name
            String name = JOptionPane.showInputDialog(
                null, "Enter your name:", "Player Name", JOptionPane.QUESTION_MESSAGE);
            if (name == null || name.isBlank()) name = "Anonymous";

            chess.gui.MainFrame frame = new chess.gui.MainFrame(name);
            ChessClient client = new ChessClient(frame);
            frame.setClient(client);
            frame.showWaiting();
            frame.setVisible(true);

            try {
                client.connect(host.trim(), 5555, name);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(null,
                    "Could not connect to " + host + ":5555\n" + e.getMessage(),
                    "Connection Failed", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }
}

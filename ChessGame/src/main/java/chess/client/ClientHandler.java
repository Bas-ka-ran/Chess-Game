package chess.server;

import chess.shared.ChessMessage;
import chess.shared.MessageType;

import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {

    private final Socket socket;
    private ObjectInputStream  in;
    private ObjectOutputStream out;
    private GameSession session;

    public ClientHandler(Socket socket) throws IOException {
        this.socket = socket;
        // IMPORTANT: create out BEFORE in to avoid deadlock
        this.out = new ObjectOutputStream(socket.getOutputStream());
        this.out.flush();
        this.in  = new ObjectInputStream(socket.getInputStream());
    }

    public void setSession(GameSession session) {
        this.session = session;
    }

    // Called from GameSession or anywhere to push a message to this client
    public synchronized void send(ChessMessage msg) {
        try {
            out.writeObject(msg);
            out.flush();
        } catch (IOException e) {
            System.err.println("Failed to send message: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        try {
            while (true) {
                ChessMessage msg = (ChessMessage) in.readObject();
                if (session != null) {
                    session.onMessage(this, msg);
                }
            }
        } catch (EOFException | java.net.SocketException e) {
            System.out.println("Client disconnected.");
            if (session != null) session.onDisconnect(this);
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Handler error: " + e.getMessage());
            if (session != null) session.onDisconnect(this);
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }
}
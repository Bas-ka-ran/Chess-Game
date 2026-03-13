package chess.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ChessServer {

    private static final int PORT = 5555;
    private static final long TIME_LIMIT_MS = 10 * 60 * 1000L; // 10 minutes each

    public static void main(String[] args) throws IOException {
        System.out.println("Chess server started on port " + PORT);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                System.out.println("Waiting for player 1...");
                Socket s1 = serverSocket.accept();
                System.out.println("Player 1 connected: " + s1.getInetAddress());

                System.out.println("Waiting for player 2...");
                Socket s2 = serverSocket.accept();
                System.out.println("Player 2 connected: " + s2.getInetAddress());

                // Start a game session in its own thread
                GameSession session = new GameSession(s1, s2, TIME_LIMIT_MS);
                new Thread(session).start();
                System.out.println("Game session started!");
            }
        }
    }
}
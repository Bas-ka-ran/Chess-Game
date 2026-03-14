package chess.client;

import chess.shared.ChessMessage;

import javax.swing.SwingUtilities;
import java.io.*;

public class NetworkThread extends Thread {

    private final ObjectInputStream in;
    private final MessageListener   listener;

    public NetworkThread(ObjectInputStream in, MessageListener listener) {
        this.in       = in;
        this.listener = listener;
        setDaemon(true); // dies when main window closes
        setName("NetworkThread");
    }

    @Override
    public void run() {
        try {
            while (true) {
                ChessMessage msg = (ChessMessage) in.readObject();
                // Always deliver to GUI on the Event Dispatch Thread
                SwingUtilities.invokeLater(() -> listener.onMessage(msg));
            }
        } catch (EOFException | java.net.SocketException e) {
            System.out.println("Disconnected from server.");
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Network error: " + e.getMessage());
        }
    }
}
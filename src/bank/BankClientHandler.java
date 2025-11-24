package bank;

import common.Message;
import java.io.*;
import java.net.Socket;

/**
 * Handles individual client connections to the bank.
 */
public class BankClientHandler implements Runnable {
    private final Bank bank;
    private final Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    public BankClientHandler(Bank bank, Socket socket) throws IOException {
        this.bank = bank;
        this.socket = socket;

        // Important: Output stream must be flushed before input stream is created
        this.out = new ObjectOutputStream(socket.getOutputStream());
        this.out.flush();
        this.in = new ObjectInputStream(socket.getInputStream());
    }

    @Override
    public void run() {
        try {
            // Continuous loop to process messages
            while (!socket.isClosed()) {
                try {
                    Message message = (Message) in.readObject();

                    // TODO: Implement message handling logic here
                    System.out.println("[BANK HANDLER] Received message type: " + message.getMessageType());

                } catch (EOFException e) {
                    break; // Client disconnected
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("[BANK HANDLER] Error: " + e.getMessage());
        } finally {
            closeConnection();
        }
    }

    private void closeConnection() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            // Ignore
        }
    }
}
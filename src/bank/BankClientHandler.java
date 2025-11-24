package bank;

import common.Message;
import messages.BankMessages;

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
            while (!socket.isClosed()) {
                try {
                    Message message = (Message) in.readObject();

                    // Handle the message and get a response
                    Message response = handleMessage(message);

                    // Send response back
                    if (response != null) {
                        synchronized (out) {
                            out.writeObject(response);
                            out.flush();
                        }
                    }
                } catch (EOFException e) {
                    break;
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("[BANK HANDLER] Error: " + e.getMessage());
        } finally {
            closeConnection();
        }
    }

    /**
     * Routes incoming messages. Currently supports Registration only.
     */
    private Message handleMessage(Message message) {
        if (message instanceof BankMessages.RegisterAgentRequest) {
            BankMessages.RegisterAgentRequest req = (BankMessages.RegisterAgentRequest) message;
            return bank.registerAgent(req.agentName, req.initialBalance);

        } else if (message instanceof BankMessages.RegisterAuctionHouseRequest) {
            BankMessages.RegisterAuctionHouseRequest req = (BankMessages.RegisterAuctionHouseRequest) message;
            return bank.registerAuctionHouse(req.host, req.port);
        }

        return null; // Unknown message
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
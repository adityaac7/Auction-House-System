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
    /**
     * Routes the incoming message to the correct method in the Bank logic.
     */
    private Message handleMessage(Message message) {
        // 1. Registration Requests
        if (message instanceof BankMessages.RegisterAgentRequest) {
            BankMessages.RegisterAgentRequest req = (BankMessages.RegisterAgentRequest) message;
            return bank.registerAgent(req.agentName, req.initialBalance);

        } else if (message instanceof BankMessages.RegisterAuctionHouseRequest) {
            BankMessages.RegisterAuctionHouseRequest req = (BankMessages.RegisterAuctionHouseRequest) message;
            return bank.registerAuctionHouse(req.host, req.port);
        }

        // 2. Financial Requests
        else if (message instanceof BankMessages.BlockFundsRequest) {
            BankMessages.BlockFundsRequest req = (BankMessages.BlockFundsRequest) message;
            return bank.blockFunds(req.accountNumber, req.amount);

        } else if (message instanceof BankMessages.UnblockFundsRequest) {
            BankMessages.UnblockFundsRequest req = (BankMessages.UnblockFundsRequest) message;
            return bank.unblockFunds(req.accountNumber, req.amount);

        } else if (message instanceof BankMessages.TransferFundsRequest) {
            BankMessages.TransferFundsRequest req = (BankMessages.TransferFundsRequest) message;
            return bank.transferFunds(req.fromAccount, req.toAccount, req.amount);
        }

        // 3. Information & Maintenance
        else if (message instanceof BankMessages.GetAccountInfoRequest) {
            BankMessages.GetAccountInfoRequest req = (BankMessages.GetAccountInfoRequest) message;
            return bank.getAccountInfo(req.accountNumber);

        } else if (message instanceof BankMessages.GetAuctionHousesRequest) {
            return bank.getAuctionHouses();

        } else if (message instanceof BankMessages.DeregisterRequest) {
            BankMessages.DeregisterRequest req = (BankMessages.DeregisterRequest) message;
            return bank.deregister(req.accountNumber, req.accountType);
        }

        return new BankMessages.DeregisterResponse(false, "Unknown message type: " + message.getMessageType());
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
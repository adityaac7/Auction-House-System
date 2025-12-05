package auctionhouse;

import common.Message;
import common.NetworkClient;
import messages.AuctionMessages;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

/**
 * Handles one Agent connection.
 * Runs on its own thread and deals with requests like getting items or placing bids.
 */
public class AuctionHouseClientHandler implements Runnable {
    private final Socket socket;
    private final AuctionHouse auctionHouse;
    private NetworkClient agentClient;
    private Integer agentAccountNumber = null;

    /**
     * Basic setup for this handler.
     * Just save socket + AH ref, rest happens in run().
     */
    public AuctionHouseClientHandler(Socket socket, AuctionHouse auctionHouse) {
        this.socket = socket;
        this.auctionHouse = auctionHouse;
    }

    @Override
    public void run() {
        try {
            // create I/O streams for this agent
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            agentClient = new NetworkClient(socket, out, in);

            System.out.println("[AUCTION HOUSE] Agent handler started for "
                    + socket.getInetAddress());

            // keep reading messages while agent stays connected
            while (agentClient.isConnected()) {
                Message message = agentClient.receiveMessage();
                Message response = handleMessage(message);

                if (response != null) {
                    agentClient.sendMessage(response);
                }
            }

        } catch (EOFException e) {
            System.out.println("[AUCTION HOUSE] Agent disconnected normally");
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("[AUCTION HOUSE] Error in client handler: " + e.getMessage());
        } finally {
            closeConnection();
        }
    }

    /**
     * Based on message type, call right function inside AuctionHouse.
     */
    private Message handleMessage(Message message) {
        String messageType = message.getMessageType();

        switch (messageType) {

            case "GET_ITEMS":
                // agent just wants list of items
                return auctionHouse.handleGetItemsRequest();

            case "PLACE_BID":
                AuctionMessages.PlaceBidRequest bidRequest =
                        (AuctionMessages.PlaceBidRequest) message;

                // save agent so we can notify them later (e.g., OUTBID / YOU WON)
                agentAccountNumber = bidRequest.agentAccountNumber;
                auctionHouse.registerAgentConnection(agentAccountNumber, agentClient);

                return auctionHouse.handlePlaceBidRequest(
                        bidRequest.itemId,
                        bidRequest.agentAccountNumber,
                        bidRequest.bidAmount);

            case "CONFIRM_WINNER":
                AuctionMessages.ConfirmWinnerRequest confirmRequest =
                        (AuctionMessages.ConfirmWinnerRequest) message;

                // agent is confirming they won and payment was done
                return auctionHouse.handleConfirmWinnerRequest(
                        confirmRequest.itemId,
                        confirmRequest.agentAccountNumber
                );

            default:
                System.out.println("[AUCTION HOUSE] Unknown message type: " + messageType);
                return null;
        }
    }

    /**
     * Close socket and remove this agent from AH tracking list.
     */
    private void closeConnection() {
        try {
            if (agentAccountNumber != null) {
                // remove this agent since they disconnected
                auctionHouse.unregisterAgentConnection(agentAccountNumber);
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {

        }
    }
}

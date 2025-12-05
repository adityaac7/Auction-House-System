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
    private Socket socket;
    private AuctionHouse auctionHouse;
    private NetworkClient agentClient;
    private Integer agentAccountNumber = null;

    // FIXED: Socket first, then AuctionHouse
    public AuctionHouseClientHandler(Socket socket, AuctionHouse auctionHouse) {
        this.socket = socket;
        this.auctionHouse = auctionHouse;
    }

    @Override
    public void run() {
        try {
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            agentClient = new NetworkClient(socket, out, in);

            System.out.println("[AUCTION HOUSE] Agent handler started for " +
                    socket.getInetAddress());

            while (!socket.isClosed()) {
                Message message = agentClient.receiveMessage();
                Message response = handleMessage(message);

                if (response != null) {
                    agentClient.sendMessage(response);
                }
            }
        } catch (EOFException e) {
            System.out.println("[AUCTION HOUSE] Agent disconnected");
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("[AUCTION HOUSE] Error handling agent: " + e.getMessage());
        } finally {
            if (agentAccountNumber != null) {
                auctionHouse.unregisterAgentConnection(agentAccountNumber);
            }
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                // Ignore
            }
        }
    }

    private Message handleMessage(Message message) {
        String messageType = message.getMessageType();

        switch (messageType) {
            case "GET_ITEMS":
                return auctionHouse.getItems();

            case "PLACE_BID":
                AuctionMessages.PlaceBidRequest bidRequest =
                        (AuctionMessages.PlaceBidRequest) message;

                agentAccountNumber = bidRequest.agentAccountNumber;
                auctionHouse.registerAgentConnection(agentAccountNumber, agentClient);

                return auctionHouse.placeBid(bidRequest.itemId,
                        bidRequest.agentAccountNumber, bidRequest.bidAmount);

            case "CONFIRM_WINNER":
                AuctionMessages.ConfirmWinnerRequest confirmRequest =
                        (AuctionMessages.ConfirmWinnerRequest) message;
                return auctionHouse.confirmWinner(confirmRequest.itemId,
                        confirmRequest.agentAccountNumber);

            default:
                System.out.println("[AUCTION HOUSE] Unknown message type: " + messageType);
                return null;
        }
    }
}


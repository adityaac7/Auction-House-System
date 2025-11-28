package auctionhouse;

import common.AuctionItem;
import messages.AuctionMessages;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

/**
 * Handles one Agent connection for this AuctionHouse.
 * It runs within its own thread.
 * reads the messages from the agent
 * calls method on AuctionHouse.
 * And, send back responses in the auctionHouse.
 */
public class AuctionHouseClientHandler extends Thread {

    private final Socket socket;
    private final AuctionHouse auctionHouse;

    private ObjectInputStream in;
    private ObjectOutputStream out;

    public AuctionHouseClientHandler(Socket socket, AuctionHouse auctionHouse) {
        this.socket = socket;
        this.auctionHouse = auctionHouse;
    }

    @Override
    public void run() {
        try {
            // Create ObjectOutputStream first,
            // then flush,
            // then ObjectInputStream.
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            boolean running = true;
            while (running) {
                Object obj;
                try {
                    //Here, Block until te agent sends somethng or certainly close socket
                    obj = in.readObject();
                } catch (IOException e) {
                    // Remote side likely closed connection
                    break;
                }

                if (obj == null) {
                    break;
                }

                // Handle request types from Agent
                if (obj instanceof AuctionMessages.GetItemsRequest) { //sending current item list
                    handleGetItems((AuctionMessages.GetItemsRequest) obj);

                } else if (obj instanceof AuctionMessages.PlaceBidRequest) { // want to place a bid
                    handlePlaceBid((AuctionMessages.PlaceBidRequest) obj);

                } else if (obj instanceof AuctionMessages.ConfirmWinnerRequest) { // want to confirm they are winner
                    handleConfirmWinner((AuctionMessages.ConfirmWinnerRequest) obj);

                } else if (obj instanceof AuctionMessages.CloseConnection) {//wants to close connection gracefully
                    running = false;

                } else {
                    System.out.println("[AUCTION HOUSE] Unknown message from agent: " + obj);
                }
            }

        } catch (IOException | ClassNotFoundException e) {
            System.out.println("[AUCTION HOUSE] Client handler error: " + e.getMessage());
        } finally {
            closeQuietly();
        }
    }

    /** Handling GetItems req from Agent
     * Assure AuctionhOuse for current items
     * Give back response
     * @param req
     * @throws IOException
     */
    private void handleGetItems(AuctionMessages.GetItemsRequest req) throws IOException {
        AuctionMessages.GetItemsResponse resp = auctionHouse.handleGetItemsRequest();
        out.writeObject(resp);
        out.flush();
    }
    /**
     * Handles the bid placing request from agent
     * Sends to AuctionHouse, then which gives it to actual ItemManger
     * Sends back result with message
     */

    private void handlePlaceBid(AuctionMessages.PlaceBidRequest req) throws IOException {
        AuctionMessages.PlaceBidResponse resp =
                auctionHouse.handlePlaceBidRequest(
                        req.itemId,
                        req.agentAccountNumber,
                        req.bidAmount
                );
        out.writeObject(resp);
        out.flush();
    }

    /** Handling ConfirmWinner req
     * AuctionHouse check wheter this is top bidder agent
     * Close the item and ask bank to transfer a fund
     * @param req
     * @throws IOException
     */
    private void handleConfirmWinner(AuctionMessages.ConfirmWinnerRequest req) throws IOException {
        AuctionMessages.ConfirmWinnerResponse resp =
                auctionHouse.handleConfirmWinnerRequest(
                        req.itemId,
                        req.agentAccountNumber
                );
        out.writeObject(resp);
        out.flush();
    }
    /**
     * Closing Stream and Socket safely.
     */
    private void closeQuietly() {
        try {
            if (in != null) in.close();
        } catch (IOException ignored) { }
        try {
            if (out != null) out.close();
        } catch (IOException ignored) { }
        try {
            socket.close();
        } catch (IOException ignored) { }
    }
}

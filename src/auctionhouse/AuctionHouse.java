package auctionhouse;

import common.AuctionItem; // If referenced directly
import common.NetworkServer; // If using the utility
// If using BankClient to connect:
import bank.BankClient;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import messages.AuctionMessages;
/**
 * The Auction House.
 * Acts as a Client to the Bank and a Server to Agents.
 */
public class AuctionHouse {
        private final int auctionHouseId;
        private int auctionHouseAccountNumber;
        private final String bankHost;
        private final int bankPort;

        private final ItemManager itemManager; // TODO; manages items AND bidding




    //private final Map<Integer, AuctionItem> items = new ConcurrentHashMap<>();
       // private final Map<Integer, common.NetworkClient> agentConnections = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: java AuctionHouse [HouseId] [Bank_Host] [Bank_Port]");
            return;
        }

        int auctionHouseId = Integer.parseInt(args[0]);
        String bankHost = args[1];
        int bankPort = Integer.parseInt(args[2]);

        AuctionHouse house = new AuctionHouse(auctionHouseId, bankHost, bankPort);
        house.start();
    }

    public AuctionHouse(int auctionHouseId, String bankHost, int bankPort) {
        this.auctionHouseId = auctionHouseId;
        this.bankHost = bankHost;
        this.bankPort = bankPort;
        this.auctionHouseAccountNumber = -1;
        this.itemManager = new ItemManager(auctionHouseId); // todo

        System.out.println("[AUCTION HOUSE " + auctionHouseId + "] Created for bank "
                + bankHost + ":" + bankPort);
    }


    private void start() {
        try {
            connectToBank();

            // 2. Register House with Bank
            // TODO: Get Account ID from Bank

            // 3. Start Server for Agents
            startAgentServer();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public AuctionMessages.GetItemsResponse handleGetItemsRequest() {
        Collection<AuctionItem> items = itemManager.getAllItems();
        AuctionItem[] array = items.toArray(new AuctionItem[0]);
        return new AuctionMessages.GetItemsResponse(true, array, "OK");
    }

    // Handle a bid GIVEN BY AGENT
    public AuctionMessages.PlaceBidResponse handlePlaceBidRequest(int itemId,
                                                                  int agentAccountNumber,
                                                                  double bidAmount) {

        // ItemManager shows status;
        // "ACCEPTED", "ITEM_NOT_FOUND", "BID_TOO_LOW",
        // "NOT_HIGH_ENOUGH", "AUCTION_CLOSED"
        String status = itemManager.placeBid(
                itemId,
                String.valueOf(agentAccountNumber),
                bidAmount
        );

        boolean success = "ACCEPTED".equals(status);
        String message;

        switch (status) {
            case "ITEM_NOT_FOUND":
                message = "Item not found";
                break;
            case "BID_TOO_LOW":
                message = "Bid below minimum";
                break;
            case "NOT_HIGH_ENOUGH":
                message = "Bid not higher than current";
                break;
            case "AUCTION_CLOSED":
                message = "Auction already closed";
                break;
            case "ACCEPTED":
                message = "Bid accepted";
                break;
            default:
                message = status;
                break;
        }

        return new AuctionMessages.PlaceBidResponse(
                success,
                status,
                message,
                bidAmount
        );
    }

    // Confirming calling agent is the winner
    public AuctionMessages.ConfirmWinnerResponse handleConfirmWinnerRequest(int itemId,
                                                                            int agentAccountNumber) {
        AuctionItem item = itemManager.getItem(itemId);
        if (item == null) {
            return new AuctionMessages.ConfirmWinnerResponse(false, "Item not found");
        }

        if (item.getCurrentBidderAccount() != agentAccountNumber) {
            return new AuctionMessages.ConfirmWinnerResponse(false, "You are not the highest bidder");
        }

        double finalPrice = item.getCurrentBid();

        // no more bids here
        itemManager.closeItem(itemId);

        // TODO;
        // from agentAccountNumber to auctionHouseAccountNumber thru Bank.

        return new AuctionMessages.ConfirmWinnerResponse(
                true,
                "Winner confirmed at price " + finalPrice
        );
    }

private void connectToBank() {
        // TODO: Implement socket connection to BankServer
        System.out.println("Connecting to Bank...");
    }

    private void startAgentServer() {
        // TODO: Open ServerSocket on dynamic port
        // TODO: Spawn AuctionServiceThreads for incoming Agents
        System.out.println("Starting Agent Server...");
    }
}
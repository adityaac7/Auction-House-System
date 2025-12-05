package auctionhouse;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import common.AuctionItem;
import common.Message;
import common.NetworkClient;
import messages.AuctionMessages;

/**
 * Represents the Auction House in the distributed auction system.
 * This class manages the lifecycle of auction items, handles communication
 * with the Bank and connected Agents, and processes all bidding logic.
 */
public class AuctionHouse {

    private int auctionHouseId;
    private int auctionHouseAccountNumber;
    private NetworkClient bankClient;

    // Manages the collection of auction items and valid bid checking
    private AuctionItemManager itemManager;

    // Stores active connections to agents, mapped by their account number
    private Map<Integer, NetworkClient> agentConnections;

    // Pre-defined data for generating random items
    private String[] itemDescriptions;
    private double[] itemMinimumBids;

    // Callback interface for updating the GUI with logs and events
    private AuctionHouseCallback callback;

    /**
     * Interface to allow the AuctionHouse to communicate events back to the GUI.
     */
    public interface AuctionHouseCallback {
        void onBidPlaced(int itemId, String itemDesc, int agentAccount, double bidAmount);
        void onBidRejected(int itemId, String itemDesc, int agentAccount,
                           double bidAmount, String reason);
        void onAgentOutbid(int itemId, String itemDesc, int previousBidder,
                           int newBidder, double newBid);
        void onItemSold(int itemId, String itemDesc, int winner, double finalPrice);
    }

    // Default constructor that starts with 5 items
    public AuctionHouse(int auctionHouseId,
                        int auctionHouseAccountNumber,
                        NetworkClient bankClient) {
        this(auctionHouseId, auctionHouseAccountNumber, bankClient, 5);
    }

    /**
     * Initializes the Auction House with specific ID and bank connection.
     * @param initialItemCount Number of items to auto-generate upon startup.
     */
    public AuctionHouse(int auctionHouseId,
                        int auctionHouseAccountNumber,
                        NetworkClient bankClient,
                        int initialItemCount) {
        this.auctionHouseId = auctionHouseId;
        this.auctionHouseAccountNumber = auctionHouseAccountNumber;
        this.bankClient = bankClient;

        // Initialize the manager that handles item storage and logic
        this.itemManager = new AuctionItemManager(auctionHouseId);

        this.agentConnections = new ConcurrentHashMap<>();

        // Helper arrays for generating random item details
        this.itemDescriptions = new String[] {
                "Vintage Watch", "Rare Painting", "Antique Vase"
        };

        // Helper array for random minimum bid amounts
        this.itemMinimumBids = new double[] {
                100.0, 500.0, 200.0, 5000.0, 1000.0,
                150.0, 300.0, 75.0, 800.0, 50.0
        };

        // Create the initial set of items
        for (int i = 0; i < initialItemCount; i++) {
            addNewItem();
        }

        System.out.println("[AUCTION HOUSE " + auctionHouseId + "] Initialized with "
                + initialItemCount + " items");
    }

    // ===== Getters and Setters =====

    public void setCallback(AuctionHouseCallback callback) {
        this.callback = callback;
    }

    public AuctionHouseCallback getCallback() {
        return callback;
    }

    public int getAuctionHouseId() {
        return auctionHouseId;
    }

    public int getAuctionHouseAccountNumber() {
        return auctionHouseAccountNumber;
    }

    /**
     * Registers an agent connection so the house can send notifications later.
     */
    public void registerAgentConnection(int agentAccountNumber, NetworkClient connection) {
        agentConnections.put(agentAccountNumber, connection);
    }

    /**
     * Removes an agent connection when they disconnect.
     */
    public void unregisterAgentConnection(int agentAccountNumber) {
        agentConnections.remove(agentAccountNumber);
    }

    /**
     * Retrieves the current snapshot of all items.
     * Synchronized to ensure thread safety when accessing the item list.
     */
    public synchronized AuctionMessages.GetItemsResponse getItems() {
        try {
            // Retrieve all items from the manager
            Collection<AuctionItem> itemsCollection = itemManager.getAllItems();
            AuctionItem[] itemArray = itemsCollection.toArray(new AuctionItem[0]);

            return new AuctionMessages.GetItemsResponse(
                    true, itemArray, "Items retrieved successfully");
        } catch (Exception e) {
            e.printStackTrace();
            return new AuctionMessages.GetItemsResponse(
                    false, new AuctionItem[0], "Failed to retrieve items");
        }
    }

    // Wrappers for handling network requests
    public AuctionMessages.PlaceBidResponse handlePlaceBidRequest(int itemId, int agentAccountNumber, double bidAmount) {
        return placeBid(itemId, agentAccountNumber, bidAmount);
    }

    public AuctionMessages.GetItemsResponse handleGetItemsRequest() {
        return getItems();
    }

    public AuctionMessages.ConfirmWinnerResponse handleConfirmWinnerRequest(int itemId, int agentAccountNumber) {
        return confirmWinner(itemId, agentAccountNumber);
    }

    /**
     * Processes a bid request from an agent.
     * Validates the item existence and delegates the bid logic to the ItemManager.
     */
    public AuctionMessages.PlaceBidResponse placeBid(int itemId,
                                                     int agentAccountNumber,
                                                     double bidAmount) {
        // Retrieve the item object
        AuctionItem item = itemManager.getItem(itemId);

        if (item == null) {
            if (callback != null) {
                callback.onBidRejected(itemId, "Unknown", agentAccountNumber,
                        bidAmount, "Item not found");
            }
            return new AuctionMessages.PlaceBidResponse(
                    false, "REJECTED", "Item not found", bidAmount);
        }

        String itemDesc = item.description;

        // Track who the previous bidder was to send an outbid notification
        int previousBidder = item.currentBidderAccountNumber;

        // Attempt to place the bid via the manager
        String status = itemManager.placeBid(itemId, String.valueOf(agentAccountNumber), bidAmount);
        boolean success = "ACCEPTED".equals(status);

        if (success) {
            // Update GUI
            if (callback != null) {
                callback.onBidPlaced(itemId, itemDesc, agentAccountNumber, bidAmount);
            }

            // Notify the previous bidder that they have been outbid
            if (previousBidder != -1 && previousBidder != agentAccountNumber) {
                notifyAgent(previousBidder, itemId, "OUTBID",
                        "You have been outbid on " + itemDesc, 0, auctionHouseAccountNumber, itemDesc);

                if (callback != null) {
                    callback.onAgentOutbid(itemId, itemDesc, previousBidder, agentAccountNumber, bidAmount);
                }
            }
        } else if (callback != null) {
            callback.onBidRejected(itemId, itemDesc, agentAccountNumber,
                    bidAmount, status);
        }

        String message = success ? "Bid accepted" : "Bid rejected: " + status;
        return new AuctionMessages.PlaceBidResponse(success, status, message, bidAmount);
    }

    /**
     * Confirms that an item has been sold to a specific agent.
     * This is called after the agent successfully transfers funds to the bank.
     */
    public AuctionMessages.ConfirmWinnerResponse confirmWinner(int itemId,
                                                               int agentAccountNumber) {
        System.out.println("[AUCTION HOUSE] ========================================");
        System.out.println("[AUCTION HOUSE] Processing confirmWinner for Item " + itemId);
        System.out.println("[AUCTION HOUSE] Agent: " + agentAccountNumber);

        AuctionItem item = itemManager.getItem(itemId);
        if (item == null) {
            System.out.println("[AUCTION HOUSE] ERROR: Item " + itemId + " not found");
            return new AuctionMessages.ConfirmWinnerResponse(false, "Item not found");
        }

        // Verify the agent claiming the win is actually the current highest bidder
        if (item.currentBidderAccountNumber != agentAccountNumber) {
            System.out.println("[AUCTION HOUSE] ERROR: Agent " + agentAccountNumber
                    + " is not the winner (winner is " + item.currentBidderAccountNumber + ")");
            return new AuctionMessages.ConfirmWinnerResponse(
                    false, "You are not the winning bidder");
        }

        double soldPrice = item.currentBid;
        int winnerAccount = item.currentBidderAccountNumber;
        String itemDesc = item.description;

        System.out.println("[AUCTION HOUSE] Step 1: Logging sale...");

        // Update GUI with sale info
        if (callback != null) {
            callback.onItemSold(itemId, itemDesc, winnerAccount, soldPrice);
        }

        System.out.println("[AUCTION HOUSE] Step 2: Broadcasting ITEM_SOLD to all agents...");

        // Notify all connected agents that the item is sold
        broadcastToAllAgents(itemId, "ITEM_SOLD",
                "Item " + itemId + " (" + itemDesc + ") sold to Agent " + winnerAccount
                        + " for $" + String.format("%.2f", soldPrice));

        System.out.println("[AUCTION HOUSE] Step 3: Closing item...");

        // Mark the item as closed in the manager
        itemManager.closeItem(itemId);

        System.out.println("[AUCTION HOUSE] ✓ Item " + itemId + " sold to agent "
                + winnerAccount + " for $" + soldPrice);
        System.out.println("[AUCTION HOUSE] ========================================");

        return new AuctionMessages.ConfirmWinnerResponse(
                true, "Item purchased and removed from auction");
    }

    /**
     * Broadcasts a status message to all connected agents.
     * Automatically cleans up connections if an agent has disconnected.
     */
    public void broadcastToAllAgents(int itemId, String status, String message) {
        List<Integer> disconnected = new ArrayList<>();

        for (Map.Entry<Integer, NetworkClient> entry : agentConnections.entrySet()) {
            int agentAccount = entry.getKey();
            NetworkClient connection = entry.getValue();

            try {
                AuctionMessages.BidStatusNotification notification =
                        new AuctionMessages.BidStatusNotification(
                                itemId, status, message, 0,
                                auctionHouseAccountNumber, "");
                connection.sendMessage(notification);
            } catch (IOException e) {
                // Track disconnected agents to remove them safely later
                disconnected.add(agentAccount);
            }
        }

        // Remove any agents that failed to receive the message
        for (int account : disconnected) {
            agentConnections.remove(account);
        }
    }

    /**
     * Helper method to send a message to the Bank and block waiting for a response.
     */
    public Message sendToBankAndWait(Message message) throws Exception {
        bankClient.sendMessage(message);
        return bankClient.receiveMessage();
    }

    /**
     * Sends a notification to a specific agent (e.g., when they are outbid).
     */
    public void notifyAgent(int agentAccountNumber,
                            int itemId,
                            String status,
                            String message,
                            double finalPrice,
                            int auctionHouseAccountNumber,
                            String itemDescription) {
        NetworkClient connection = agentConnections.get(agentAccountNumber);
        if (connection != null) {
            try {
                AuctionMessages.BidStatusNotification notification =
                        new AuctionMessages.BidStatusNotification(
                                itemId, status, message,
                                finalPrice, auctionHouseAccountNumber,
                                itemDescription);
                connection.sendMessage(notification);
            } catch (IOException e) {
                agentConnections.remove(agentAccountNumber);
            }
        }
    }

    /**
     * Automatically creates a new item with a random description and minimum bid.
     */
    public synchronized void addNewItem() {
        // Calculate index for random data selection based on current count
        int tempIndex = itemManager.getAllItems().size() + 1;

        String description = itemDescriptions[
                Math.abs(tempIndex % itemDescriptions.length)];
        double minimumBid = itemMinimumBids[
                Math.abs(tempIndex % itemMinimumBids.length)];

        // Add to manager
        itemManager.addItem(description, minimumBid);
    }

    /**
     * Adds a new item with specific details (used by the GUI).
     */
    public synchronized void addNewItem(String description, double minimumBid) {
        itemManager.addItem(description, minimumBid);
    }

    /**
     * Attempts to remove an item.
     * Returns false if the item has active bids or cannot be found.
     */
    public synchronized boolean removeItem(int itemId) {
        AuctionItem item = itemManager.getItem(itemId);
        // Only allow removal if no one has bid on it yet
        if (item != null && item.currentBidderAccountNumber == -1) {
            // NOTE: The current AuctionItemManager does not implement a remove method.
            // This would need to be extended if deletion functionality is required.
            return false;
        }
        return false;
    }

    /**
     * Checks if there are any active bids on any items.
     * Used to prevent closing the server while auctions are active.
     */
    public boolean hasActiveBids() {
        return itemManager.getAllItems().stream()
                .anyMatch(item -> item.currentBidderAccountNumber != -1);
    }

    /**
     * Returns the total number of items currently managed.
     */
    public int getItemCount() {
        return itemManager.getAllItems().size();
    }
}
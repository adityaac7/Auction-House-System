package auctionhouse;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import common.AuctionItem;
import common.Message;
import common.NetworkClient;
import messages.AuctionMessages;

/**
 * Manages auction items and handles bidding in a distributed auction system.
 *
 * <p>This class coordinates the auction process by maintaining a collection of items
 * available for bidding, managing agent connections, and handling bid placement and
 * winner confirmation. It communicates with a bank server to block and release funds
 * during the bidding process and notifies agents of bid status changes and auction results.
 *
 * <p>Key responsibilities include:
 * <ul>
 *   <li>Maintaining auction items through {@link AuctionItemManager} instances</li>
 *   <li>Processing bid requests from agents and coordinating with the bank</li>
 *   <li>Broadcasting notifications to connected agents when items are sold or bids are outbid</li>
 *   <li>Managing fund blocking and release for bidders</li>
 *   <li>Providing activity logging through callback interface</li>
 * </ul>
 *
 * <p>Thread Safety: This class uses concurrent data structures and synchronized methods
 * to support concurrent access from multiple agents.
 *
 * @see AuctionItemManager
 * @see AuctionMessages
 * @see NetworkClient
 */
public class AuctionHouse {

    /** The unique identifier for this auction house. */
    private int auctionHouseId;

    /** The bank account number for this auction house. */
    private int auctionHouseAccountNumber;

    /** Network client for communicating with the bank server. */
    private NetworkClient bankClient;

    /** Map of item IDs to their respective auction item managers. */
    private Map<Integer, AuctionItemManager> items;

    /** Map of agent account numbers to their network connections. */
    private Map<Integer, NetworkClient> agentConnections;

    /** Counter for generating unique item IDs. */
    private int nextItemId;

    /** Array of predefined item descriptions for random item generation. */
    private String[] itemDescriptions;

    /** Array of predefined minimum bid amounts for random item generation. */
    private double[] itemMinimumBids;

    /** Callback interface for GUI activity logging. */
    private AuctionHouseCallback callback;

    /**
     * Callback interface for auction activity notifications.
     *
     * <p>Implementations of this interface receive real-time notifications about
     * auction events such as bids placed, bids rejected, agents outbid, and items sold.
     * This is typically used for GUI updates and activity logging.
     */
    public interface AuctionHouseCallback {
        /**
         * Called when a bid is successfully placed on an item.
         *
         * @param itemId the unique identifier of the item
         * @param itemDesc the description of the item
         * @param agentAccount the agent's bank account number
         * @param bidAmount the amount of the bid
         */
        void onBidPlaced(int itemId, String itemDesc, int agentAccount, double bidAmount);

        /**
         * Called when a bid is rejected.
         *
         * @param itemId the unique identifier of the item
         * @param itemDesc the description of the item
         * @param agentAccount the agent's bank account number
         * @param bidAmount the attempted bid amount
         * @param reason the reason for rejection
         */
        void onBidRejected(int itemId, String itemDesc, int agentAccount,
                           double bidAmount, String reason);

        /**
         * Called when an agent is outbid by another agent.
         *
         * @param itemId the unique identifier of the item
         * @param itemDesc the description of the item
         * @param previousBidder the account number of the agent who was outbid
         * @param newBidder the account number of the new highest bidder
         * @param newBid the new highest bid amount
         */
        void onAgentOutbid(int itemId, String itemDesc, int previousBidder,
                           int newBidder, double newBid);

        /**
         * Called when an item is sold and removed from the auction.
         *
         * @param itemId the unique identifier of the item
         * @param itemDesc the description of the item
         * @param winner the account number of the winning agent
         * @param finalPrice the final sale price
         */
        void onItemSold(int itemId, String itemDesc, int winner, double finalPrice);
    }

    /**
     * Constructs an auction house with default number of initial items (5).
     *
     * @param auctionHouseId the unique identifier for this auction house
     * @param auctionHouseAccountNumber the bank account number for this auction house
     * @param bankClient the network client for communicating with the bank
     */
    public AuctionHouse(int auctionHouseId,
                        int auctionHouseAccountNumber,
                        NetworkClient bankClient) {
        this(auctionHouseId, auctionHouseAccountNumber, bankClient, 5);
    }

    /**
     * Constructs an auction house with specified number of initial items.
     *
     * <p>Initializes the auction house with concurrent data structures, registers
     * with the bank, and creates the specified number of random auction items.
     *
     * @param auctionHouseId the unique identifier for this auction house
     * @param auctionHouseAccountNumber the bank account number for this auction house
     * @param bankClient the network client for communicating with the bank
     * @param initialItemCount the number of items to create at initialization
     */
    public AuctionHouse(int auctionHouseId,
                        int auctionHouseAccountNumber,
                        NetworkClient bankClient,
                        int initialItemCount) {
        this.auctionHouseId = auctionHouseId;
        this.auctionHouseAccountNumber = auctionHouseAccountNumber;
        this.bankClient = bankClient;
        this.items = new ConcurrentHashMap<>();
        this.agentConnections = new ConcurrentHashMap<>();
        this.nextItemId = 1;

        this.itemDescriptions = new String[] {
                "Vintage Watch", "Rare Painting", "Antique Vase"
        };

        this.itemMinimumBids = new double[] {
                100.0, 500.0, 200.0, 5000.0, 1000.0,
                150.0, 300.0, 75.0, 800.0, 50.0
        };

        for (int i = 0; i < initialItemCount; i++) {
            addNewItem();
        }

        System.out.println("[AUCTION HOUSE " + auctionHouseId + "] Initialized with "
                + initialItemCount + " items");
    }

    /**
     * Sets the callback interface for auction activity logging.
     *
     * @param callback the callback implementation to receive auction event notifications,
     *                 or null to disable callbacks
     */
    public void setCallback(AuctionHouseCallback callback) {
        this.callback = callback;
    }

    /**
     * Gets the currently registered callback interface.
     *
     * @return the current callback implementation, or null if none is set
     */
    public AuctionHouseCallback getCallback() {
        return callback;
    }

    /**
     * Gets the unique identifier for this auction house.
     *
     * @return the auction house ID
     */
    public int getAuctionHouseId() {
        return auctionHouseId;
    }

    /**
     * Gets the bank account number associated with this auction house.
     *
     * @return the auction house's bank account number
     */
    public int getAuctionHouseAccountNumber() {
        return auctionHouseAccountNumber;
    }

    /**
     * Registers an agent's network connection for receiving notifications.
     *
     * <p>Once registered, the agent will receive bid status updates and item sold
     * notifications through this connection.
     *
     * @param agentAccountNumber the agent's bank account number
     * @param connection the network client for communicating with the agent
     */
    public void registerAgentConnection(int agentAccountNumber, NetworkClient connection) {
        agentConnections.put(agentAccountNumber, connection);
    }

    /**
     * Unregisters an agent's network connection.
     *
     * <p>The agent will no longer receive notifications after being unregistered.
     *
     * @param agentAccountNumber the agent's bank account number to unregister
     */
    public void unregisterAgentConnection(int agentAccountNumber) {
        agentConnections.remove(agentAccountNumber);
    }

    /**
     * Retrieves all current auction items available for bidding.
     *
     * @return a response containing an array of all auction items and success status
     */
    public synchronized AuctionMessages.GetItemsResponse getItems() {
        try {
            AuctionItem[] itemArray = items.values().stream()
                    .map(AuctionItemManager::getItem)
                    .toArray(AuctionItem[]::new);
            return new AuctionMessages.GetItemsResponse(
                    true, itemArray, "Items retrieved successfully");
        } catch (Exception e) {
            return new AuctionMessages.GetItemsResponse(
                    false, new AuctionItem[0], "Failed to retrieve items");
        }
    }

    /**
     * Processes a bid request for a specific auction item.
     *
     * <p>This method validates the bid amount against the item's current bid and minimum
     * bid requirements. If accepted, the bid is recorded and the callback is notified.
     * If rejected, the reason is communicated through the callback and response.
     *
     * @param itemId the unique identifier of the item being bid on
     * @param agentAccountNumber the agent's bank account number placing the bid
     * @param bidAmount the amount being bid
     * @return a response indicating whether the bid was accepted or rejected with details
     */
    public AuctionMessages.PlaceBidResponse placeBid(int itemId,
                                                     int agentAccountNumber,
                                                     double bidAmount) {
        // Validate bid amount to prevent invalid values
        if (Double.isNaN(bidAmount) || Double.isInfinite(bidAmount) || bidAmount <= 0) {
            if (callback != null) {
                callback.onBidRejected(itemId, "Unknown", agentAccountNumber,
                        bidAmount, "Invalid bid amount");
            }
            return new AuctionMessages.PlaceBidResponse(false, "REJECTED",
                    "Invalid bid amount", bidAmount);
        }
        
        AuctionItemManager manager = items.get(itemId);
        if (manager == null) {
            if (callback != null) {
                callback.onBidRejected(itemId, "Unknown", agentAccountNumber,
                        bidAmount, "Item not found or no longer available");
            }
            return new AuctionMessages.PlaceBidResponse(
                    false, "REJECTED", "Item not found or no longer available", bidAmount);
        }

        String itemDesc = manager.getItem().description;
        String status = manager.placeBid(agentAccountNumber, bidAmount);
        boolean success = "ACCEPTED".equals(status);

        if (success && callback != null) {
            callback.onBidPlaced(itemId, itemDesc, agentAccountNumber, bidAmount);
        } else if (!success && callback != null) {
            callback.onBidRejected(itemId, itemDesc, agentAccountNumber,
                    bidAmount, status);
        }

        String message = success ? "Bid accepted" : "Bid rejected: " + status;
        return new AuctionMessages.PlaceBidResponse(success, status, message, bidAmount);
    }

    /**
     * Confirms the winner and removes the item from the auction after payment.
     *
     * <p>This method should be called by the agent AFTER successfully transferring funds
     * to the auction house. It performs the following operations in sequence:
     * <ol>
     *   <li>Releases funds for all losing bidders</li>
     *   <li>Logs the sale through the callback interface</li>
     *   <li>Broadcasts ITEM_SOLD notification to all connected agents</li>
     *   <li>Removes the item from the auction map</li>
     *   <li>Shuts down the item manager's timer executor</li>
     * </ol>
     *
     * @param itemId the unique identifier of the sold item
     * @param agentAccountNumber the winning agent's bank account number
     * @return a response indicating success or failure with an appropriate message
     */
    public synchronized AuctionMessages.ConfirmWinnerResponse confirmWinner(int itemId,
                                                               int agentAccountNumber) {
        System.out.println("[AUCTION HOUSE] ========================================");
        System.out.println("[AUCTION HOUSE] Processing confirmWinner for Item " + itemId);
        System.out.println("[AUCTION HOUSE] Agent: " + agentAccountNumber);

        AuctionItemManager manager = items.get(itemId);
        if (manager == null) {
            System.out.println("[AUCTION HOUSE] ERROR: Item " + itemId + " not found (may have been already sold)");
            return new AuctionMessages.ConfirmWinnerResponse(false, "Item not found or already sold");
        }

        AuctionItem item = manager.getItem();
        if (item.currentBidderAccountNumber != agentAccountNumber) {
            System.out.println("[AUCTION HOUSE] ERROR: Agent " + agentAccountNumber
                    + " is not the winner (winner is " + item.currentBidderAccountNumber + ")");
            return new AuctionMessages.ConfirmWinnerResponse(
                    false, "You are not the winning bidder");
        }
        
        // Double-check the item is still there - another thread might have removed it
        // between the first check and now (though unlikely with synchronized method)
        if (!items.containsKey(itemId)) {
            System.out.println("[AUCTION HOUSE] ERROR: Item " + itemId + " was removed during confirmation");
            return new AuctionMessages.ConfirmWinnerResponse(false, "Item was removed during confirmation");
        }

        double soldPrice = item.currentBid;
        int winnerAccount = item.currentBidderAccountNumber;
        String itemDesc = item.description;

        System.out.println("[AUCTION HOUSE] Step 1: Releasing funds for losing bidders...");

        // Release funds for all losers BEFORE removing item
        manager.releaseLoserFunds(winnerAccount);

        System.out.println("[AUCTION HOUSE] Step 2: Broadcasting ITEM_SOLD to all agents...");

        // Broadcast ITEM_SOLD to ALL agents so they can remove it from their view
        // Do this BEFORE removing item to ensure all agents are notified
        broadcastToAllAgents(itemId, "ITEM_SOLD",
                "Item " + itemId + " (" + itemDesc + ") sold to Agent " + winnerAccount
                        + " for $" + String.format("%.2f", soldPrice));

        System.out.println("[AUCTION HOUSE] Step 3: Removing item from auction...");

        // Remove the item from the map BEFORE calling callback
        // This ensures refreshItems() won't see the item
        items.remove(itemId);

        System.out.println("[AUCTION HOUSE] Step 4: Adding replacement item...");
        
        // Add a new item to replace the sold one (as per requirements)
        addNewItem();
        System.out.println("[AUCTION HOUSE] ✓ New item added to replace sold item");

        System.out.println("[AUCTION HOUSE] Step 5: Logging sale and refreshing UI...");

        // Log the sale AFTER removing item so refreshItems() won't see it
        if (callback != null) {
            callback.onItemSold(itemId, itemDesc, winnerAccount, soldPrice);
        }

        System.out.println("[AUCTION HOUSE] Step 6: Shutting down item manager...");

        // Shut down the timer executor
        manager.shutdown();

        // Create response
        AuctionMessages.ConfirmWinnerResponse response =
                new AuctionMessages.ConfirmWinnerResponse(
                        true, "Item purchased and removed from auction");

        System.out.println("[AUCTION HOUSE] ✓ Item " + itemId + " sold to agent "
                + winnerAccount + " for $" + soldPrice);
        System.out.println("[AUCTION HOUSE] ✓ Item removed from auction list");
        System.out.println("[AUCTION HOUSE] ========================================");

        return response;
    }

    /**
     * Broadcasts a notification to all connected agents.
     *
     * <p>This method sends the same message to every registered agent connection.
     * Connections that fail are automatically removed from the registry to prevent
     * future communication attempts with disconnected agents.
     *
     * @param itemId the item ID associated with the notification
     * @param status the status string (e.g., "ITEM_SOLD", "OUTBID")
     * @param message the notification message text
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
                disconnected.add(agentAccount);
            }
        }

        // Clean up disconnected agents
        for (int account : disconnected) {
            agentConnections.remove(account);
        }
    }

    /**
     * Sends a message to the bank and waits for a response.
     *
     * <p>This is a synchronous blocking call that will wait until the bank
     * responds or an error occurs.
     *
     * @param message the message to send to the bank
     * @return the bank's response message
     * @throws Exception if communication with the bank fails
     */
    public Message sendToBankAndWait(Message message) throws Exception {
        bankClient.sendMessage(message);
        return bankClient.receiveMessage();
    }

    /**
     * Notifies a specific agent about a bid status change or auction result.
     *
     * <p>If the agent's connection is no longer valid, it is automatically removed
     * from the registry to prevent future communication errors.
     *
     * @param agentAccountNumber the agent's bank account number
     * @param itemId the item ID associated with the notification
     * @param status the status string describing the notification type
     * @param message the notification message text
     * @param finalPrice the final price of the item (relevant for winning bids)
     * @param auctionHouseAccountNumber the auction house's account number for payment
     * @param itemDescription the description of the item
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
     * Adds a new auction item with a randomly selected description and minimum bid.
     *
     * <p>The item description and minimum bid are selected from predefined arrays
     * based on the item ID modulo the array length. This ensures variety while
     * reusing the predefined descriptions and bid amounts.
     */
    public synchronized void addNewItem() {
        int itemId = nextItemId++;
        String description = itemDescriptions[
                Math.abs(itemId % itemDescriptions.length)];
        double minimumBid = itemMinimumBids[
                Math.abs(itemId % itemMinimumBids.length)];
        AuctionItem item = new AuctionItem(auctionHouseId, itemId, description, minimumBid);
        AuctionItemManager manager = new AuctionItemManager(item, bankClient, this);
        items.put(itemId, manager);
    }

    /**
     * Adds a new auction item with specified description and minimum bid.
     *
     * @param description the item description
     * @param minimumBid the minimum bid amount for the item
     */
    public synchronized void addNewItem(String description, double minimumBid) {
        int itemId = nextItemId++;
        AuctionItem item = new AuctionItem(auctionHouseId, itemId, description, minimumBid);
        AuctionItemManager manager = new AuctionItemManager(item, bankClient, this);
        items.put(itemId, manager);
    }

    /**
     * Removes an item from the auction if it has no active bids.
     *
     * <p>Items with active bids cannot be removed to preserve auction integrity
     * and prevent disputes with bidders.
     *
     * @param itemId the unique identifier of the item to remove
     * @return true if the item was removed, false if it has active bids or doesn't exist
     */
    public synchronized boolean removeItem(int itemId) {
        AuctionItemManager manager = items.get(itemId);
        if (manager != null) {
            if (manager.getItem().currentBidderAccountNumber == -1) {
                items.remove(itemId);
                // Shut down the timer executor to prevent resource leak
                manager.shutdown();
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if any items in the auction have active bids.
     *
     * <p>This is useful for determining if the auction house can safely shut down
     * or if there are pending transactions that need to be completed.
     *
     * @return true if at least one item has a current bidder, false otherwise
     */
    public boolean hasActiveBids() {
        return items.values().stream()
                .anyMatch(manager -> manager.getItem().currentBidderAccountNumber != -1);
    }

    /**
     * Gets the current number of items in the auction.
     *
     * @return the count of active auction items
     */
    public int getItemCount() {
        return items.size();
    }
}

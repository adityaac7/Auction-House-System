import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Auction House managing items and handling bidding.
 * FIXED: Activity logging, fund unblocking, and broadcast notifications
 */
public class AuctionHouse {

    private int auctionHouseId;
    private int auctionHouseAccountNumber;
    private NetworkClient bankClient;
    private Map<Integer, AuctionItemManager> items;
    private Map<Integer, NetworkClient> agentConnections;
    private int nextItemId;
    private String[] itemDescriptions;
    private double[] itemMinimumBids;

    // ⭐ NEW: Callback for GUI activity logging
    private AuctionHouseCallback callback;

    // ⭐ NEW: Callback interface
    public interface AuctionHouseCallback {
        void onBidPlaced(int itemId, String itemDesc, int agentAccount, double bidAmount);
        void onBidRejected(int itemId, String itemDesc, int agentAccount,
                           double bidAmount, String reason);
        void onAgentOutbid(int itemId, String itemDesc, int previousBidder,
                           int newBidder, double newBid);
        void onItemSold(int itemId, String itemDesc, int winner, double finalPrice);
    }

    public AuctionHouse(int auctionHouseId,
                        int auctionHouseAccountNumber,
                        NetworkClient bankClient) {
        this(auctionHouseId, auctionHouseAccountNumber, bankClient, 5);
    }

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

    // ⭐ NEW: Setters/getters for callback
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

    public void registerAgentConnection(int agentAccountNumber, NetworkClient connection) {
        agentConnections.put(agentAccountNumber, connection);
    }

    public void unregisterAgentConnection(int agentAccountNumber) {
        agentConnections.remove(agentAccountNumber);
    }

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

    // ⭐ UPDATED: Added callback notifications
    public AuctionMessages.PlaceBidResponse placeBid(int itemId,
                                                     int agentAccountNumber,
                                                     double bidAmount) {
        AuctionItemManager manager = items.get(itemId);
        if (manager == null) {
            if (callback != null) {
                callback.onBidRejected(itemId, "Unknown", agentAccountNumber,
                        bidAmount, "Item not found");
            }
            return new AuctionMessages.PlaceBidResponse(
                    false, "REJECTED", "Item not found", bidAmount);
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
     * Confirm winner and remove item from auction.
     * Called by agent AFTER bank transfer completes.
     */
    public AuctionMessages.ConfirmWinnerResponse confirmWinner(int itemId,
                                                               int agentAccountNumber) {
        System.out.println("[AUCTION HOUSE] ========================================");
        System.out.println("[AUCTION HOUSE] Processing confirmWinner for Item " + itemId);
        System.out.println("[AUCTION HOUSE] Agent: " + agentAccountNumber);

        AuctionItemManager manager = items.get(itemId);
        if (manager == null) {
            System.out.println("[AUCTION HOUSE] ERROR: Item " + itemId + " not found");
            return new AuctionMessages.ConfirmWinnerResponse(false, "Item not found");
        }

        AuctionItem item = manager.getItem();
        if (item.currentBidderAccountNumber != agentAccountNumber) {
            System.out.println("[AUCTION HOUSE] ERROR: Agent " + agentAccountNumber
                    + " is not the winner (winner is " + item.currentBidderAccountNumber + ")");
            return new AuctionMessages.ConfirmWinnerResponse(
                    false, "You are not the winning bidder");
        }

        double soldPrice = item.currentBid;
        int winnerAccount = item.currentBidderAccountNumber;
        String itemDesc = item.description;

        System.out.println("[AUCTION HOUSE] Step 1: Releasing funds for losing bidders...");

        // Release funds for all losers BEFORE removing item
        manager.releaseLoserFunds(winnerAccount);

        System.out.println("[AUCTION HOUSE] Step 2: Logging sale...");

        // Log the sale
        if (callback != null) {
            callback.onItemSold(itemId, itemDesc, winnerAccount, soldPrice);
        }

        System.out.println("[AUCTION HOUSE] Step 3: Broadcasting ITEM_SOLD to all agents...");

        // Broadcast ITEM_SOLD to ALL agents so they can remove it from their view
        broadcastToAllAgents(itemId, "ITEM_SOLD",
                "Item " + itemId + " (" + itemDesc + ") sold to Agent " + winnerAccount
                        + " for $" + String.format("%.2f", soldPrice));

        System.out.println("[AUCTION HOUSE] Step 4: Removing item from auction...");

        // Remove the item from the map
        items.remove(itemId);

        System.out.println("[AUCTION HOUSE] Step 5: Shutting down item manager...");

        // Shut down the timer executor
        manager.shutdown();

        System.out.println("[AUCTION HOUSE] ✓ Item " + itemId + " sold to agent "
                + winnerAccount + " for $" + soldPrice);
        System.out.println("[AUCTION HOUSE] ✓ Item removed from auction list");
        System.out.println("[AUCTION HOUSE] ========================================");

        return new AuctionMessages.ConfirmWinnerResponse(
                true, "Item purchased and removed from auction");
    }

    // ⭐ NEW: Broadcast to all connected agents
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

    public Message sendToBankAndWait(Message message) throws Exception {
        bankClient.sendMessage(message);
        return bankClient.receiveMessage();
    }

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

    public synchronized void addNewItem(String description, double minimumBid) {
        int itemId = nextItemId++;
        AuctionItem item = new AuctionItem(auctionHouseId, itemId, description, minimumBid);
        AuctionItemManager manager = new AuctionItemManager(item, bankClient, this);
        items.put(itemId, manager);
    }

    public synchronized boolean removeItem(int itemId) {
        AuctionItemManager manager = items.get(itemId);
        if (manager != null) {
            if (manager.getItem().currentBidderAccountNumber == -1) {
                items.remove(itemId);
                return true;
            }
        }
        return false;
    }

    public boolean hasActiveBids() {
        return items.values().stream()
                .anyMatch(manager -> manager.getItem().currentBidderAccountNumber != -1);
    }

    public int getItemCount() {
        return items.size();
    }
}

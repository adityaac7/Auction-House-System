import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import common.AuctionItem;
import common.Message;
import common.NetworkClient;
import messages.AuctionMessages;

/**
 * AuctionHouse side for our project.
 * I basically manage all items here, talk to Bank and Agents,
 * and handle the whole bidding flow.
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

    // used by GUI to show activity log etc.
    private AuctionHouseCallback callback;

    /**
     * Small interface so GUI can see what is happening in auction house.
     */
    public interface AuctionHouseCallback {
        void onBidPlaced(int itemId, String itemDesc, int agentAccount, double bidAmount);
        void onBidRejected(int itemId, String itemDesc, int agentAccount,
                           double bidAmount, String reason);
        void onAgentOutbid(int itemId, String itemDesc, int previousBidder,
                           int newBidder, double newBid);
        void onItemSold(int itemId, String itemDesc, int winner, double finalPrice);
    }

    // default: start with 5 auto items
    public AuctionHouse(int auctionHouseId,
                        int auctionHouseAccountNumber,
                        NetworkClient bankClient) {
        this(auctionHouseId, auctionHouseAccountNumber, bankClient, 5);
    }

    // here I can choose how many items to auto-create when starting
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

        // some demo item names I reuse when auto adding items
        this.itemDescriptions = new String[] {
                "Vintage Watch", "Rare Painting", "Antique Vase"
        };

        // list of sample minimum bids, I just index into this
        this.itemMinimumBids = new double[] {
                100.0, 500.0, 200.0, 5000.0, 1000.0,
                150.0, 300.0, 75.0, 800.0, 50.0
        };

        // create starting items
        for (int i = 0; i < initialItemCount; i++) {
            addNewItem();
        }

        System.out.println("[AUCTION HOUSE " + auctionHouseId + "] Initialized with "
                + initialItemCount + " items");
    }

    // ===== basic getters / callback =====

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

    // save agent connection when they talk to this house
    public void registerAgentConnection(int agentAccountNumber, NetworkClient connection) {
        agentConnections.put(agentAccountNumber, connection);
    }

    // remove agent connection on disconnect
    public void unregisterAgentConnection(int agentAccountNumber) {
        agentConnections.remove(agentAccountNumber);
    }

    /**
     * Give current snapshot of all items in this auction house.
     * I sync it because different threads can touch items.
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
     * Main place where agent sends a bid.
     * I pass it to AuctionItemManager and also ping the GUI callback.
     */
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
     * Confirm the winner and remove item from auction list.
     * Agent calls this AFTER bank transfer is done.
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

        // free up money for all losers before we delete item
        manager.releaseLoserFunds(winnerAccount);

        System.out.println("[AUCTION HOUSE] Step 2: Logging sale...");

        // tell GUI about the sale
        if (callback != null) {
            callback.onItemSold(itemId, itemDesc, winnerAccount, soldPrice);
        }

        System.out.println("[AUCTION HOUSE] Step 3: Broadcasting ITEM_SOLD to all agents...");

        // let every connected agent know that this item is done
        broadcastToAllAgents(itemId, "ITEM_SOLD",
                "Item " + itemId + " (" + itemDesc + ") sold to Agent " + winnerAccount
                        + " for $" + String.format("%.2f", soldPrice));

        System.out.println("[AUCTION HOUSE] Step 4: Removing item from auction...");

        // remove from map so it disappears from list
        items.remove(itemId);

        System.out.println("[AUCTION HOUSE] Step 5: Shutting down item manager...");


        manager.shutdown();

        System.out.println("[AUCTION HOUSE] ✓ Item " + itemId + " sold to agent "
                + winnerAccount + " for $" + soldPrice);
        System.out.println("[AUCTION HOUSE] ✓ Item removed from auction list");
        System.out.println("[AUCTION HOUSE] ========================================");

        return new AuctionMessages.ConfirmWinnerResponse(
                true, "Item purchased and removed from auction");
    }

    /**
     * Send a status update about an item to every connected agent.
     * If some agent is dead, I mark and remove that connection.
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

        // clean any broken connections
        for (int account : disconnected) {
            agentConnections.remove(account);
        }
    }

    /**
     * Helper to send one message to Bank and wait for reply.
     */
    public Message sendToBankAndWait(Message message) throws Exception {
        bankClient.sendMessage(message);
        return bankClient.receiveMessage();
    }

    /**
     * Notify just one agent about status change on some item.
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
     * Auto create item with built-in description + min bid.
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
     * Overload: here GUI can pass its own description and minimum bid.
     */
    public synchronized void addNewItem(String description, double minimumBid) {
        int itemId = nextItemId++;
        AuctionItem item = new AuctionItem(auctionHouseId, itemId, description, minimumBid);
        AuctionItemManager manager = new AuctionItemManager(item, bankClient, this);
        items.put(itemId, manager);
    }

    /**
     * Try to remove item, only allowed if no one has bid yet.
     */
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

    /**
     * Check if at least one item already has a bidder.
     */
    public boolean hasActiveBids() {
        return items.values().stream()
                .anyMatch(manager -> manager.getItem().currentBidderAccountNumber != -1);
    }

    /**
     * Just number of items we are managing right now.
     */
    public int getItemCount() {
        return items.size();
    }
}

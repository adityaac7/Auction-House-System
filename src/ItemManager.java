/**
 * Manages the list of items and bids in a thread-safe manner.
 */
public class ItemManager {
    // TODO: Use ConcurrentHashMap for items

    public void addItem(String description, double minBid) {
        // TODO
    }

    public synchronized String placeBid(int itemId, String agentId, double amount) {
        // TODO: Check locks, verify funds with Bank, update bid
        return "NOT_IMPLEMENTED";
    }
}
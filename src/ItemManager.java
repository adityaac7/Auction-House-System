import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * It Manages all items bidding logic for the auction house.
 */
public class ItemManager {
    private final Map<Integer, AuctionItem> items = new ConcurrentHashMap<>();
    private int nextItemId = 1;
    private final int houseId;

    public ItemManager(int houseId) {
        this.houseId = houseId;
    }

    /**
     * It is adding a new item to this auction house.
     */
    public synchronized void addItem(String description, double minimumBid) {
        int id = nextItemId++;
        // We set auctionEndTime = 0 for now
        AuctionItem item = new AuctionItem(houseId, id, description, minimumBid, 0L);
        items.put(id, item);
    }

    public Collection<AuctionItem> getAllItems() {
        return items.values();
    }

    public AuctionItem getItem(int itemId) {
        return items.get(itemId);
    }

    /**
     *
     * It @return status like accepted, itemNotFound, bidTooLow,
     * not highEnough, auctionClosed
     */
    public synchronized String placeBid(int itemId, String agentId, double amount) {
        AuctionItem item = items.get(itemId);
        if (item == null) {
            return "ITEM_NOT_FOUND";
        }

        // TODO: use auctionEndTime to check if the item is already closed by time

        if (!item.isOpen()) {
            return "AUCTION_CLOSED";
        }

        if (amount < item.getMinimumBid()) {
            return "BID_TOO_LOW";
        }

        if (amount <= item.getCurrentBid()) {
            return "NOT_HIGH_ENOUGH";
        }

        item.setCurrentBid(amount);

        try {
            int accountNumber = Integer.parseInt(agentId);
            item.setCurrentBidderAccount(accountNumber);
        } catch (NumberFormatException e) {
            item.setCurrentBidderAccount(-1);
        }

        return "ACCEPTED";
    }

    /**
     * It says an item as auction closed.
     */
    public synchronized void closeItem(int itemId) {
        AuctionItem item = items.get(itemId);
        if (item != null) {
            item.closeAuction();
        }
    }
}

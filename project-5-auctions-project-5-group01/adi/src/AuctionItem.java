import java.io.Serializable;

/**
 * Represents an item being auctioned
 */
public class AuctionItem implements Serializable {
    private static final long serialVersionUID = 1L;

    public int houseId;
    public int itemId;
    public String description;
    public double minimumBid;
    public double currentBid;
    public int currentBidderAccountNumber;
    public long auctionEndTime; // Timestamp when auction ends (0 if no bid yet)

    public AuctionItem(int houseId, int itemId, String description, double minimumBid) {
        this.houseId = houseId;
        this.itemId = itemId;
        this.description = description;
        this.minimumBid = minimumBid;
        this.currentBid = 0;
        this.currentBidderAccountNumber = -1;
        this.auctionEndTime = 0;
    }

    @Override
    public String toString() {
        return String.format("Item %d (House %d): %s - Min: $%.2f, Current: $%.2f",
                itemId, houseId, description, minimumBid, currentBid);
    }
}

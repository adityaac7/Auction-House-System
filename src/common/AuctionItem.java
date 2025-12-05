package common;

import java.io.Serializable;

/**
 * Represents an item up for auction.
 * Implements Serializable to be sent between Server, Agent, and Client.
 * FIXED: Added missing fields and methods (currentBidderAccountNumber, isClosed, setters)
 * to resolve errors in AuctionItemManager and AuctionHouseApplication.
 */
public class AuctionItem implements Serializable {
    private static final long serialVersionUID = 1L;

    public int itemId;
    public int auctionHouseId;
    public String description;
    public double minimumBid;
    public double currentBid;

    // Name of current highest bidder (optional, but useful for display)
    public String bidderName = "None";

    // Account number of the current highest bidder (-1 if none)
    // Made public to be accessible by AuctionHouseApplication
    public int currentBidderAccountNumber = -1;

    // Time when the auction ends (0 if not set/indefinite)
    // Renamed from remainingTime to align with usage in Application
    public long auctionEndTime;

    // Status flag
    public boolean isClosed = false;

    /**
     * Constructor for creating a new auction item.
     * @param itemId Unique ID for the item
     * @param auctionHouseId ID of the house selling the item
     * @param description Text description of the item
     * @param minimumBid The starting bid amount
     * @param auctionEndTime Time in ms when auction ends (0 for indefinite)
     */
    public AuctionItem(int itemId, int auctionHouseId, String description, double minimumBid, long auctionEndTime) {
        this.itemId = itemId;
        this.auctionHouseId = auctionHouseId;
        this.description = description;
        this.minimumBid = minimumBid;
        this.auctionEndTime = auctionEndTime;

        // Initialize state
        this.currentBid = 0;
        this.currentBidderAccountNumber = -1;
        this.bidderName = "None";
        this.isClosed = false;
    }

    /**
     * Checks if the auction for this item is still open.
     * @return true if not closed manually AND (time is 0 OR time has not passed)
     */
    public boolean isOpen() {
        if (isClosed) {
            return false;
        }
        // If auctionEndTime is 0, it means no specific end time set (open indefinitely)
        // If it is > 0, check if current time is before end time
        return auctionEndTime == 0 || System.currentTimeMillis() < auctionEndTime;
    }

    // --- Methods required by AuctionItemManager ---

    public void closeAuction() {
        this.isClosed = true;
    }

    public double getMinimumBid() {
        return minimumBid;
    }

    public double getCurrentBid() {
        return currentBid;
    }

    public void setCurrentBid(double amount) {
        this.currentBid = amount;
    }

    public void setCurrentBidderAccount(int accountNumber) {
        this.currentBidderAccountNumber = accountNumber;
    }

    @Override
    public String toString() {
        return String.format("Item %d: %s (Min: $%.2f, Current: $%.2f, Bidder: %d)",
                itemId, description, minimumBid, currentBid, currentBidderAccountNumber);
    }
}
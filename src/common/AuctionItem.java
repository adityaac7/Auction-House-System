package common;

import java.io.Serializable;

/**
 * It represents a single item that lives inside the one Auction House.
 * and tracks basic infOS, highest bid and bidder.
 *
 */
public class AuctionItem implements Serializable {

    private static final long serialVersionUID = 1L;


    private final int houseId;


    private final int itemId;


    private final String description;


    private final double minimumBid;

    // It is the current highest bid and the bidder's bank account number
    private volatile double currentBid;
    private volatile int currentBidderAccount;


    private volatile long auctionEndTime;

    private volatile boolean open = true;

    /**
     *
     * @param houseId           it is which auction house it relate with
     * @param itemId
     * @param description
     * @param minimumBid
     * @param auctionEndTime
     */
    public AuctionItem(
            int houseId,
            int itemId,
            String description,
            double minimumBid,
            long auctionEndTime
    ) {
        this.houseId = houseId;
        this.itemId = itemId;
        this.description = description;
        this.minimumBid = minimumBid;
        this.currentBid = 0.0;
        this.currentBidderAccount = -1;
        this.auctionEndTime = auctionEndTime;
        this.open = true;
    }



    public int getHouseId() {
        return houseId;
    }

    public int getItemId() {
        return itemId;
    }

    public String getDescription() {
        return description;
    }

    public double getMinimumBid() {
        return minimumBid;
    }

    public double getCurrentBid() {
        return currentBid;
    }

    public int getCurrentBidderAccount() {
        return currentBidderAccount;
    }

    public long getAuctionEndTime() {
        return auctionEndTime;
    }

    public boolean isOpen() {
        return open;
    }


    /**
     * Updating the current highest bid.
     */
    public void setCurrentBid(double bid) {
        this.currentBid = bid;
    }

    /**
     * Updating the bank account which is currently holding the top bid.
     */
    public void setCurrentBidderAccount(int accountNumber) {
        this.currentBidderAccount = accountNumber;
    }

    public void setAuctionEndTime(long auctionEndTime) {
        this.auctionEndTime = auctionEndTime;
    }

    /**
     *After this step, manager will not take new bids.
     */
    public void closeAuction() {
        this.open = false;
    }
}

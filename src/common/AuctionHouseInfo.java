package common;

import java.io.Serializable;

/**
 * Information about an auction house for agents to connect to.
 */
public class AuctionHouseInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    public int auctionHouseId;
    public String host;
    public int port;

    public AuctionHouseInfo(int auctionHouseId, String host, int port) {
        this.auctionHouseId = auctionHouseId;
        this.host = host;
        this.port = port;
    }

    @Override
    public String toString() {
        return String.format("Auction House %d: %s:%d", auctionHouseId, host, port);
    }
}
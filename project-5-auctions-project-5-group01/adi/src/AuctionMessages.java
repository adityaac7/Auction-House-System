/**
 * Messages for Auction House communication
 */
public class AuctionMessages {

    // Get Items List
    public static class GetItemsRequest extends Message {
        private static final long serialVersionUID = 1L;

        public GetItemsRequest() {
            super("GET_ITEMS");
        }
    }

    public static class GetItemsResponse extends Message {
        private static final long serialVersionUID = 1L;

        public boolean success;
        public AuctionItem[] items;
        public String message;

        public GetItemsResponse(boolean success, AuctionItem[] items, String message) {
            super("GET_ITEMS_RESPONSE");
            this.success = success;
            this.items = items;
            this.message = message;
        }
    }

    // Place Bid
    public static class PlaceBidRequest extends Message {
        private static final long serialVersionUID = 1L;

        public int itemId;
        public int agentAccountNumber;
        public double bidAmount;

        public PlaceBidRequest(int itemId, int agentAccountNumber, double bidAmount) {
            super("PLACE_BID");
            this.itemId = itemId;
            this.agentAccountNumber = agentAccountNumber;
            this.bidAmount = bidAmount;
        }
    }

    public static class PlaceBidResponse extends Message {
        private static final long serialVersionUID = 1L;

        public boolean success;
        public String status;   // "ACCEPTED" / "REJECTED"
        public String message;
        public double bidAmount;

        public PlaceBidResponse(boolean success, String status,
                                String message, double bidAmount) {
            super("PLACE_BID_RESPONSE");
            this.success = success;
            this.status = status;
            this.message = message;
            this.bidAmount = bidAmount;
        }
    }

    // Confirm Winner (agent -> auction house AFTER bank transfer)
    public static class ConfirmWinnerRequest extends Message {
        private static final long serialVersionUID = 1L;

        public int itemId;
        public int agentAccountNumber;

        public ConfirmWinnerRequest(int itemId, int agentAccountNumber) {
            super("CONFIRM_WINNER");
            this.itemId = itemId;
            this.agentAccountNumber = agentAccountNumber;
        }
    }

    public static class ConfirmWinnerResponse extends Message {
        private static final long serialVersionUID = 1L;

        public boolean success;
        public String message;

        public ConfirmWinnerResponse(boolean success, String message) {
            super("CONFIRM_WINNER_RESPONSE");
            this.success = success;
            this.message = message;
        }
    }

    /**
     * Bid status notifications (ACCEPTED/REJECTED/OUTBID/WINNER).
     * When status == "WINNER", finalPrice and auctionHouseAccountNumber
     * are set so the agent can initiate the bank transfer.
     */
    public static class BidStatusNotification extends Message {
        private static final long serialVersionUID = 1L;

        public int itemId;
        public String status;
        public String message;

        public double finalPrice;             // for WINNER: amount to transfer
        public int auctionHouseAccountNumber; // bank account to credit
        public String itemDescription;        // description at win time

        public BidStatusNotification(int itemId,
                                     String status,
                                     String message,
                                     double finalPrice,
                                     int auctionHouseAccountNumber,
                                     String itemDescription) {
            super("BID_STATUS_NOTIFICATION");
            this.itemId = itemId;
            this.status = status;
            this.message = message;
            this.finalPrice = finalPrice;
            this.auctionHouseAccountNumber = auctionHouseAccountNumber;
            this.itemDescription = itemDescription;
        }
    }
}

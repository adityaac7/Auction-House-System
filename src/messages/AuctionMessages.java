package messages;

import common.Message;
import common.AuctionItem;

/**
 * Messages for Auction House communication with agent
 *
 */
public class AuctionMessages {
    // Agents ask AuctionHouse to send current items.
    public static class GetItemsRequest extends Message {
        private static final long serialVersionUID = 1L;

        public GetItemsRequest() {
            super("GET_ITEMS");
        }
    }

    /**
     * AuctionHouse return currentItems with a message
     */

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

    /**
     * Agent req to place a bid on an item
     */


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

    /**
     * Response Back to agent after trying to bid
     */

    public static class PlaceBidResponse extends Message {
        private static final long serialVersionUID = 1L;

        public boolean success;
        public String status;
        public String message;
        public double bidAmount;

        public PlaceBidResponse(boolean success,
                                String status,
                                String message,
                                double bidAmount) {
            super("PLACE_BID_RESPONSE");
            this.success = success;
            this.status = status;
            this.message = message;
            this.bidAmount = bidAmount;
        }
    }
    /**
     * It confirms the winner of the bid, If agents is winning and trigger a payment
    */

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

    /**
     * AuctionHouse tell if agent is winner confirmed! and if payment worked
     */

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
     * It sends notification form auctionHouse to Agent
     */


    public static class BidStatusNotification extends Message {
        private static final long serialVersionUID = 1L;

        public int itemId;
        public String status;
        public String message;

        /**
         * It decides the final prise and where to pay for the actual winner.
         **/
        public double finalPrice;
        public int auctionHouseAccountNumber;
        public String itemDescription;

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
    //agent ensure auctionHouse to Closes the connection
    public static class CloseConnection extends Message {
        private static final long serialVersionUID = 1L;

        public CloseConnection() {
            super("CLOSE_CONNECTION");
        }
    }
}

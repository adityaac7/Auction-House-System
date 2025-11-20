/**
 * Container for Bank-related protocol messages.
 * Currently supports Agent registration.
 */
public class BankMessages {

    /**
     * Request sent by an Agent to register with the Bank.
     */
    public static class RegisterAgentRequest extends Message {
        private static final long serialVersionUID = 1L;
        public String agentName;
        public double initialBalance;

        public RegisterAgentRequest(String agentName, double initialBalance) {
            super("REGISTER_AGENT");
            this.agentName = agentName;
            this.initialBalance = initialBalance;
        }
    }

    /**
     * Response from Bank to Agent after registration.
     * Contains the new account ID and list of active auction houses.
     */
    public static class RegisterAgentResponse extends Message {
        private static final long serialVersionUID = 1L;
        public boolean success;
        public int accountNumber;
        public String message;
        public AuctionHouseInfo[] auctionHouses;

        public RegisterAgentResponse(boolean success, int accountNumber, String message,
                                     AuctionHouseInfo[] auctionHouses) {
            super("REGISTER_AGENT_RESPONSE");
            this.success = success;
            this.accountNumber = accountNumber;
            this.message = message;
            this.auctionHouses = auctionHouses;
        }
    }

    /**
     * Request sent by an Auction House to register.
     * Includes connection details so Agents can find it.
     */
    public static class RegisterAuctionHouseRequest extends Message {
        private static final long serialVersionUID = 1L;
        public String host;
        public int port;

        public RegisterAuctionHouseRequest(String host, int port) {
            super("REGISTER_AUCTION_HOUSE");
            this.host = host;
            this.port = port;
        }
    }

    /**
     * Response to Auction House registration.
     * Returns the assigned Auction House ID.
     */
    public static class RegisterAuctionHouseResponse extends Message {
        private static final long serialVersionUID = 1L;
        public boolean success;
        public int auctionHouseId;
        public int accountNumber;
        public String message;

        public RegisterAuctionHouseResponse(boolean success, int auctionHouseId,
                                            int accountNumber, String message) {
            super("REGISTER_AUCTION_HOUSE_RESPONSE");
            this.success = success;
            this.auctionHouseId = auctionHouseId;
            this.accountNumber = accountNumber;
            this.message = message;
        }
    }
}
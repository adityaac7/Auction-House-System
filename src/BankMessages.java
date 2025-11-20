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

    /**
     * Request to block funds for a pending bid.
     * Ensures the Agent has enough money before the bid is accepted.
     */
    public static class BlockFundsRequest extends Message {
        private static final long serialVersionUID = 1L;
        public int accountNumber;
        public double amount;

        public BlockFundsRequest(int accountNumber, double amount) {
            super("BLOCK_FUNDS");
            this.accountNumber = accountNumber;
            this.amount = amount;
        }
    }

    public static class BlockFundsResponse extends Message {
        private static final long serialVersionUID = 1L;
        public boolean success;
        public String message;

        public BlockFundsResponse(boolean success, String message) {
            super("BLOCK_FUNDS_RESPONSE");
            this.success = success;
            this.message = message;
        }
    }

    /**
     * Request to unblock funds (e.g., when an Agent is outbid).
     */
    public static class UnblockFundsRequest extends Message {
        private static final long serialVersionUID = 1L;
        public int accountNumber;
        public double amount;

        public UnblockFundsRequest(int accountNumber, double amount) {
            super("UNBLOCK_FUNDS");
            this.accountNumber = accountNumber;
            this.amount = amount;
        }
    }

    public static class UnblockFundsResponse extends Message {
        private static final long serialVersionUID = 1L;
        public boolean success;
        public String message;

        public UnblockFundsResponse(boolean success, String message) {
            super("UNBLOCK_FUNDS_RESPONSE");
            this.success = success;
            this.message = message;
        }
    }

    /**
     * Request to permanently transfer blocked funds to another account.
     * Used when an auction is won.
     */
    public static class TransferFundsRequest extends Message {
        private static final long serialVersionUID = 1L;
        public int fromAccount;
        public int toAccount;
        public double amount;

        public TransferFundsRequest(int fromAccount, int toAccount, double amount) {
            super("TRANSFER_FUNDS");
            this.fromAccount = fromAccount;
            this.toAccount = toAccount;
            this.amount = amount;
        }
    }

    public static class TransferFundsResponse extends Message {
        private static final long serialVersionUID = 1L;
        public boolean success;
        public String message;

        public TransferFundsResponse(boolean success, String message) {
            super("TRANSFER_FUNDS_RESPONSE");
            this.success = success;
            this.message = message;
        }
    }
}
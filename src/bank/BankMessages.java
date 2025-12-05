package bank;
import common.Message;
import common.AuctionHouseInfo;


/**
 * Messages for Bank communication
 * FIXED: Added GetAuctionHouses messages for dynamic auction house discovery
 */
public class BankMessages {
    // Agent Registration
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

    // Auction House Registration
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

    // Block Funds
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

    // Unblock Funds
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

    // Transfer Funds
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

    // Get Account Info
    public static class GetAccountInfoRequest extends Message {
        private static final long serialVersionUID = 1L;
        public int accountNumber;

        public GetAccountInfoRequest(int accountNumber) {
            super("GET_ACCOUNT_INFO");
            this.accountNumber = accountNumber;
        }
    }

    public static class GetAccountInfoResponse extends Message {
        private static final long serialVersionUID = 1L;
        public boolean success;
        public double totalBalance;
        public double availableFunds;
        public double blockedFunds;
        public String message;

        public GetAccountInfoResponse(boolean success, double totalBalance,
                                      double availableFunds, double blockedFunds, String message) {
            super("GET_ACCOUNT_INFO_RESPONSE");
            this.success = success;
            this.totalBalance = totalBalance;
            this.availableFunds = availableFunds;
            this.blockedFunds = blockedFunds;
            this.message = message;
        }
    }

    // Deregister
    public static class DeregisterRequest extends Message {
        private static final long serialVersionUID = 1L;
        public int accountNumber;
        public String accountType; // "AGENT" or "AUCTION_HOUSE"

        public DeregisterRequest(int accountNumber, String accountType) {
            super("DEREGISTER");
            this.accountNumber = accountNumber;
            this.accountType = accountType;
        }
    }

    public static class DeregisterResponse extends Message {
        private static final long serialVersionUID = 1L;
        public boolean success;
        public String message;

        public DeregisterResponse(boolean success, String message) {
            super("DEREGISTER_RESPONSE");
            this.success = success;
            this.message = message;
        }
    }

    // NEW: Get Auction Houses (for dynamic discovery)
    public static class GetAuctionHousesRequest extends Message {
        private static final long serialVersionUID = 1L;

        public GetAuctionHousesRequest() {
            super("GET_AUCTION_HOUSES");
        }
    }

    public static class GetAuctionHousesResponse extends Message {
        private static final long serialVersionUID = 1L;
        public boolean success;
        public AuctionHouseInfo[] auctionHouses;
        public String message;

        public GetAuctionHousesResponse(boolean success, AuctionHouseInfo[] auctionHouses,
                                        String message) {
            super("GET_AUCTION_HOUSES_RESPONSE");
            this.success = success;
            this.auctionHouses = auctionHouses;
            this.message = message;
        }
    }
}

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
}
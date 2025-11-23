
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central bank logic.
 * Manages the collection of Accounts and Auction Houses.
 * Handles the processing of BankMessages.
 */
public class Bank {

    private Map<Integer, BankAccount> accounts;
    private Map<Integer, AuctionHouseInfo> auctionHouses;

    // Helper to map a Bank Account Number -> Auction House ID
    // We need this so when an Auction House deregisters by account number, we know which ID to remove.
    private Map<Integer, Integer> auctionHouseAccountToId;

    private int nextAccountNumber;
    private int nextAuctionHouseId;

    public Bank() {
        this.accounts = new ConcurrentHashMap<>();
        this.auctionHouses = new ConcurrentHashMap<>();
        this.auctionHouseAccountToId = new ConcurrentHashMap<>();
        this.nextAccountNumber = 1000;
        this.nextAuctionHouseId = 1;
    }

    public synchronized BankMessages.RegisterAgentResponse registerAgent(String agentName,
                                                                         double initialBalance) {
        int accountNumber = nextAccountNumber++;
        BankAccount account = new BankAccount(accountNumber, agentName,
                initialBalance, "AGENT");
        accounts.put(accountNumber, account);

        System.out.println("[BANK] Registered Agent: " + agentName + " (Acct: " + accountNumber + ")");

        // We send the list of active auction houses so the agent knows who to connect to immediately
        AuctionHouseInfo[] auctionHousesArray =
                auctionHouses.values().toArray(new AuctionHouseInfo[0]);

        return new BankMessages.RegisterAgentResponse(
                true, accountNumber, "Agent registered successfully", auctionHousesArray);
    }

    public synchronized BankMessages.RegisterAuctionHouseResponse registerAuctionHouse(
            String host, int port) {
        int auctionHouseId = nextAuctionHouseId++;
        int accountNumber = nextAccountNumber++;

        // Create account for auction house with 0 balance
        BankAccount account = new BankAccount(
                accountNumber,
                "AuctionHouse_" + auctionHouseId,
                0.0,
                "AUCTION_HOUSE");
        accounts.put(accountNumber, account);

        // Store auction house info
        AuctionHouseInfo info = new AuctionHouseInfo(auctionHouseId, host, port);
        auctionHouses.put(auctionHouseId, info);

        // Remember mapping from account -> auction house id for later lookup
        auctionHouseAccountToId.put(accountNumber, auctionHouseId);

        System.out.println("[BANK] Registered AH " + auctionHouseId + " at " + host + ":" + port);

        return new BankMessages.RegisterAuctionHouseResponse(
                true, auctionHouseId, accountNumber,
                "Auction house registered successfully");
    }

    /**
     * Attempt to block funds for a bid.
     * This is called when an Auction House receives a bid.
     */
    public BankMessages.BlockFundsResponse blockFunds(int accountNumber, double amount) {
        BankAccount account = accounts.get(accountNumber);

        // Safety check: Does the account exist?
        if (account == null) {
            return new BankMessages.BlockFundsResponse(false, "Account not found");
        }

        // Try to block the funds using the thread-safe method in BankAccount
        boolean success = account.blockFunds(amount);

        if (success) {
            System.out.println("[BANK] Blocked $" + amount + " for Acct " + accountNumber);
        } else {
            System.out.println("[BANK] Failed to block $" + amount + " for Acct " + accountNumber + " (Insufficient funds)");
        }

        return new BankMessages.BlockFundsResponse(success,
                success ? "Funds blocked" : "Insufficient funds");
    }

    /**
     * Unblocks funds.
     * Usually called when an agent is outbid and needs their money back.
     */
    public BankMessages.UnblockFundsResponse unblockFunds(int accountNumber, double amount) {
        BankAccount account = accounts.get(accountNumber);
        if (account == null) {
            return new BankMessages.UnblockFundsResponse(false, "Account not found");
        }

        account.unblockFunds(amount);
        System.out.println("[BANK] Unblocked $" + amount + " for Acct " + accountNumber);
        return new BankMessages.UnblockFundsResponse(true, "Funds unblocked");
    }

    /**
     * Transfers funds from one account to another.
     * This moves money from 'Blocked' status in the sender's account to the receiver's total balance.
     */
    public BankMessages.TransferFundsResponse transferFunds(int fromAccount,
                                                            int toAccount,
                                                            double amount) {
        BankAccount from = accounts.get(fromAccount);
        BankAccount to = accounts.get(toAccount);

        // Verify both accounts exist before processing
        if (from == null || to == null) {
            return new BankMessages.TransferFundsResponse(false, "Account not found");
        }

        // 1. Try to take the money from the sender (must be blocked first)
        boolean success = from.transferFunds(amount);

        if (success) {
            // 2. If successful, deposit into the receiver
            to.deposit(amount);
            System.out.println("[BANK] Transferred $" + amount + " from " + fromAccount + " to " + toAccount);
            return new BankMessages.TransferFundsResponse(true, "Transfer successful");
        } else {
            return new BankMessages.TransferFundsResponse(false, "Insufficient blocked funds");
        }
    }
}
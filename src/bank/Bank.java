package bank;

import common.AuctionHouseInfo;
import messages.BankMessages;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The core logic engine for the Bank component.
 * <p>
 * This class acts as the centralized database and transaction manager. It is responsible for:
 * <ul>
 * <li>Managing the lifecycle of Bank Accounts (creation, lookup, deletion).</li>
 * <li>Maintaining the registry of active Auction Houses.</li>
 * <li>Processing financial transactions (blocking, unblocking, and transferring funds).</li>
 * </ul>
 * <p>
 * This class is designed to be thread-safe, utilizing {@link ConcurrentHashMap} and
 * synchronized blocks where necessary to handle concurrent requests from multiple clients.
 */
public class Bank {

    // Primary storage for all accounts (Agents and Auction Houses) keyed by Account ID
    private Map<Integer, BankAccount> accounts;

    // Registry of active Auction Houses keyed by Auction House ID
    private Map<Integer, AuctionHouseInfo> auctionHouses;

    // specific mapping to link a Bank Account Number back to an Auction House ID.
    // This is required to efficiently remove an Auction House from the registry
    // when a deregistration request comes in via Account Number.
    private Map<Integer, Integer> auctionHouseAccountToId;

    private int nextAccountNumber;
    private int nextAuctionHouseId;

    /**
     * Initializes the Bank with empty registries and default starting IDs.
     */
    public Bank() {
        this.accounts = new ConcurrentHashMap<>();
        this.auctionHouses = new ConcurrentHashMap<>();
        this.auctionHouseAccountToId = new ConcurrentHashMap<>();

        // Start account numbers at 1000 to simulate realistic bank account IDs
        this.nextAccountNumber = 1000;
        this.nextAuctionHouseId = 1;
    }

    /**
     * Registers a new Agent in the system.
     * Creates a new BankAccount with the specified initial balance.
     *
     * @param agentName      The display name of the agent.
     * @param initialBalance The starting funds for the agent.
     * @return A response containing the new Account ID and the current list of active Auction Houses.
     */
    public synchronized BankMessages.RegisterAgentResponse registerAgent(String agentName,
                                                                         double initialBalance) {
        int accountNumber = nextAccountNumber++;
        BankAccount account = new BankAccount(accountNumber, agentName,
                initialBalance, "AGENT");
        accounts.put(accountNumber, account);

        System.out.println("[BANK] Registered Agent: " + agentName + " (Acct: " + accountNumber + ")");

        // Return list of available auction houses so the Agent can connect immediately
        AuctionHouseInfo[] auctionHousesArray =
                auctionHouses.values().toArray(new AuctionHouseInfo[0]);

        return new BankMessages.RegisterAgentResponse(
                true, accountNumber, "Agent registered successfully", auctionHousesArray);
    }

    /**
     * Registers a new Auction House in the system.
     * Creates a BankAccount (with 0 balance) and adds the house to the public registry.
     *
     * @param host The hostname where the Auction House is listening for Agents.
     * @param port The port where the Auction House is listening for Agents.
     * @return A response containing the assigned Auction House ID and Bank Account ID.
     */
    public synchronized BankMessages.RegisterAuctionHouseResponse registerAuctionHouse(
            String host, int port) {
        int auctionHouseId = nextAuctionHouseId++;
        int accountNumber = nextAccountNumber++;

        // Create account for auction house with 0 balance (they receive funds, usually don't spend)
        BankAccount account = new BankAccount(
                accountNumber,
                "AuctionHouse_" + auctionHouseId,
                0.0,
                "AUCTION_HOUSE");
        accounts.put(accountNumber, account);

        // Store public info for Agents to find this house
        AuctionHouseInfo info = new AuctionHouseInfo(auctionHouseId, host, port);
        auctionHouses.put(auctionHouseId, info);

        // Remember the link between Account # and House ID for deregistration later
        auctionHouseAccountToId.put(accountNumber, auctionHouseId);

        System.out.println("[BANK] Registered AH " + auctionHouseId + " at " + host + ":" + port);

        return new BankMessages.RegisterAuctionHouseResponse(
                true, auctionHouseId, accountNumber,
                "Auction house registered successfully");
    }

    /**
     * Attempts to block funds in an account.
     * This is typically called by an Auction House when a bid is placed to ensure the Agent can pay.
     *
     * @param accountNumber The account to block funds from.
     * @param amount        The amount to block.
     * @return Success if the account exists and has sufficient available funds.
     */
    public BankMessages.BlockFundsResponse blockFunds(int accountNumber, double amount) {
        BankAccount account = accounts.get(accountNumber);

        // Edge Case: Account does not exist
        if (account == null) {
            return new BankMessages.BlockFundsResponse(false, "Account not found");
        }

        // Delegate to the thread-safe method in BankAccount
        boolean success = account.blockFunds(amount);

        if (success) {
            System.out.println("[BANK] Blocked $" + amount + " for Acct " + accountNumber);
        } else {
            // Optional: Log failure for debugging
            // System.out.println("[BANK] Failed to block funds for Acct " + accountNumber);
        }

        return new BankMessages.BlockFundsResponse(success,
                success ? "Funds blocked" : "Insufficient funds");
    }

    /**
     * Unblocks previously blocked funds.
     * Called when an Agent is outbid and their hold on the funds should be released.
     *
     * @param accountNumber The account to unblock funds for.
     * @param amount        The amount to release.
     * @return Success message.
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
     * Permanent transfer of funds.
     * Moves money from the 'Blocked' status of the sender to the 'Total' balance of the receiver.
     * Used when an Auction is won.
     *
     * @param fromAccount Sender's account ID (Agent).
     * @param toAccount   Receiver's account ID (Auction House).
     * @param amount      The amount to transfer.
     * @return Success if funds were successfully moved.
     */
    public BankMessages.TransferFundsResponse transferFunds(int fromAccount,
                                                            int toAccount,
                                                            double amount) {
        BankAccount from = accounts.get(fromAccount);
        BankAccount to = accounts.get(toAccount);

        if (from == null || to == null) {
            return new BankMessages.TransferFundsResponse(false, "Account not found");
        }

        // 1. Withdraw from sender (must be blocked first)
        boolean success = from.transferFunds(amount);

        if (success) {
            // 2. Deposit to receiver
            to.deposit(amount);
            System.out.println("[BANK] Transferred $" + amount + " from " + fromAccount + " to " + toAccount);
            return new BankMessages.TransferFundsResponse(true, "Transfer successful");
        } else {
            return new BankMessages.TransferFundsResponse(false, "Insufficient blocked funds");
        }
    }

    /**
     * Retrieves the current state of an account.
     *
     * @param accountNumber The account to query.
     * @return Response containing Total, Available, and Blocked balances.
     */
    public BankMessages.GetAccountInfoResponse getAccountInfo(int accountNumber) {
        BankAccount account = accounts.get(accountNumber);
        if (account == null) {
            return new BankMessages.GetAccountInfoResponse(
                    false, 0, 0, 0, "Account not found");
        }

        return new BankMessages.GetAccountInfoResponse(
                true,
                account.getTotalBalance(),
                account.getAvailableFunds(),
                account.getBlockedFunds(),
                "Success");
    }

    /**
     * Handles the cleanup when a client disconnects or explicitly deregisters.
     *
     * @param accountNumber The account to remove.
     * @param accountType   The type of account (used to update specific registries).
     * @return Success message.
     */
    public BankMessages.DeregisterResponse deregister(int accountNumber,
                                                      String accountType) {
        // Special Handling: If an Auction House leaves, we must remove it from the
        // public list so Agents don't try to connect to a dead server.
        if ("AUCTION_HOUSE".equals(accountType)) {
            Integer auctionHouseId = auctionHouseAccountToId.remove(accountNumber);
            if (auctionHouseId != null) {
                auctionHouses.remove(auctionHouseId);
            }
        }

        // Remove the bank account from memory
        BankAccount account = accounts.remove(accountNumber);
        if (account != null) {
            System.out.println("[BANK] Deregistered " + accountType + " " + accountNumber);
            return new BankMessages.DeregisterResponse(true, "Deregistered");
        } else {
            return new BankMessages.DeregisterResponse(false, "Account not found");
        }
    }

    /**
     * Returns a list of all currently active Auction Houses.
     * Used by Agents to refresh their list of markets.
     *
     * @return Response containing the array of AuctionHouseInfo.
     */
    public BankMessages.GetAuctionHousesResponse getAuctionHouses() {
        AuctionHouseInfo[] auctionHousesArray =
                auctionHouses.values().toArray(new AuctionHouseInfo[0]);
        return new BankMessages.GetAuctionHousesResponse(
                true, auctionHousesArray, "Success");
    }

    /**
     * Returns the count of currently active bank accounts (Agents + Auction Houses).
     * Used by the Server GUI to display traffic stats.
     */
    public int getAccountCount() {
        return accounts.size();
    }

    /**
     * Returns the count of registered Auction Houses.
     * Used by the Server GUI to display market activity.
     */
    public int getAuctionHouseCount() {
        return auctionHouses.size();
    }
}
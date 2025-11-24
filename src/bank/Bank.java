package bank;

import common.AuctionHouseInfo;
import messages.BankMessages;

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

        // Remember mapping from account -> auction house id
        auctionHouseAccountToId.put(accountNumber, auctionHouseId);

        System.out.println("[BANK] Registered AH " + auctionHouseId + " at " + host + ":" + port);

        return new BankMessages.RegisterAuctionHouseResponse(
                true, auctionHouseId, accountNumber,
                "Auction house registered successfully");
    }

    public BankMessages.BlockFundsResponse blockFunds(int accountNumber, double amount) {
        BankAccount account = accounts.get(accountNumber);
        if (account == null) {
            return new BankMessages.BlockFundsResponse(false, "Account not found");
        }

        boolean success = account.blockFunds(amount);
        if (success) {
            System.out.println("[BANK] Blocked $" + amount + " for Acct " + accountNumber);
        }

        return new BankMessages.BlockFundsResponse(success,
                success ? "Funds blocked" : "Insufficient funds");
    }

    public BankMessages.UnblockFundsResponse unblockFunds(int accountNumber, double amount) {
        BankAccount account = accounts.get(accountNumber);
        if (account == null) {
            return new BankMessages.UnblockFundsResponse(false, "Account not found");
        }

        account.unblockFunds(amount);
        System.out.println("[BANK] Unblocked $" + amount + " for Acct " + accountNumber);
        return new BankMessages.UnblockFundsResponse(true, "Funds unblocked");
    }

    public BankMessages.TransferFundsResponse transferFunds(int fromAccount,
                                                            int toAccount,
                                                            double amount) {
        BankAccount from = accounts.get(fromAccount);
        BankAccount to = accounts.get(toAccount);
        if (from == null || to == null) {
            return new BankMessages.TransferFundsResponse(false, "Account not found");
        }

        boolean success = from.transferFunds(amount);
        if (success) {
            to.deposit(amount);
            System.out.println("[BANK] Transferred $" + amount + " from " + fromAccount + " to " + toAccount);
            return new BankMessages.TransferFundsResponse(true, "Transfer successful");
        } else {
            return new BankMessages.TransferFundsResponse(false, "Insufficient blocked funds");
        }
    }

    /**
     * Retrieves account balance info for the GUI.
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
     * Handles user disconnection.
     * If an Auction House leaves, we must remove it from the list so Agents stop trying to connect.
     */
    public BankMessages.DeregisterResponse deregister(int accountNumber,
                                                      String accountType) {
        // Special handling for Auction Houses: remove from the public list
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
     * Returns a list of all active Auction Houses.
     */
    public BankMessages.GetAuctionHousesResponse getAuctionHouses() {
        AuctionHouseInfo[] auctionHousesArray =
                auctionHouses.values().toArray(new AuctionHouseInfo[0]);
        return new BankMessages.GetAuctionHousesResponse(
                true, auctionHousesArray, "Success");
    }
}
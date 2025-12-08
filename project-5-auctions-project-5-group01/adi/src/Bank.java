import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central bank managing all accounts and fund operations
 * FIXED: Added GetAuctionHouses support and correct deregistration of auction houses
 */
public class Bank {

    private Map<Integer, BankAccount> accounts;
    private Map<Integer, AuctionHouseInfo> auctionHouses;

    // Map auction-house bank account number -> auctionHouseId
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
        System.out.println("[BANK] Registered agent: " + agentName + " (Account: "
                + accountNumber + ", Balance: $" + initialBalance + ")");

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

        System.out.println("[BANK] Registered auction house " + auctionHouseId
                + " at " + host + ":" + port
                + " (Account: " + accountNumber + ")");

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
        String message = success ? "Funds blocked successfully"
                : "Insufficient available funds";
        if (success) {
            System.out.println("[BANK] Blocked $" + amount + " for account "
                    + accountNumber + " (Available: $"
                    + account.getAvailableFunds() + ")");
        }

        return new BankMessages.BlockFundsResponse(success, message);
    }

    public BankMessages.UnblockFundsResponse unblockFunds(int accountNumber, double amount) {
        BankAccount account = accounts.get(accountNumber);
        if (account == null) {
            return new BankMessages.UnblockFundsResponse(false, "Account not found");
        }

        account.unblockFunds(amount);
        System.out.println("[BANK] Unblocked $" + amount + " for account "
                + accountNumber + " (Available: $"
                + account.getAvailableFunds() + ")");
        return new BankMessages.UnblockFundsResponse(true, "Funds unblocked successfully");
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
            System.out.println("[BANK] Transferred $" + amount + " from account "
                    + fromAccount + " to account " + toAccount);
            System.out.println("[BANK] From balance: $" + from.getTotalBalance()
                    + ", To balance: $" + to.getTotalBalance());
            return new BankMessages.TransferFundsResponse(
                    true, "Funds transferred successfully");
        } else {
            return new BankMessages.TransferFundsResponse(
                    false, "Insufficient blocked funds");
        }
    }

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
                "Account info retrieved successfully");
    }

    public BankMessages.DeregisterResponse deregister(int accountNumber,
                                                      String accountType) {
        if ("AUCTION_HOUSE".equals(accountType)) {
            // Remove only this auction house, not all of them
            Integer auctionHouseId = auctionHouseAccountToId.remove(accountNumber);
            if (auctionHouseId != null) {
                auctionHouses.remove(auctionHouseId);
            }
        }

        BankAccount account = accounts.remove(accountNumber);
        if (account != null) {
            System.out.println("[BANK] Deregistered " + accountType
                    + " account " + accountNumber);
            return new BankMessages.DeregisterResponse(
                    true, "Deregistered successfully");
        } else {
            return new BankMessages.DeregisterResponse(
                    false, "Account not found");
        }
    }

    // Get current list of auction houses
    public BankMessages.GetAuctionHousesResponse getAuctionHouses() {
        AuctionHouseInfo[] auctionHousesArray =
                auctionHouses.values().toArray(new AuctionHouseInfo[0]);
        return new BankMessages.GetAuctionHousesResponse(
                true, auctionHousesArray, "Auction houses retrieved successfully");
    }

    public int getAccountCount() {
        return accounts.size();
    }

    public int getAuctionHouseCount() {
        return auctionHouses.size();
    }
}


import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central bank logic.
 * Manages the collection of Accounts and Auction Houses.
 */
public class Bank {

    private Map<Integer, BankAccount> accounts;
    private Map<Integer, AuctionHouseInfo> auctionHouses;
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

        // Send list of current auction houses to the new agent
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

        // Store auction house info so Agents can find it
        AuctionHouseInfo info = new AuctionHouseInfo(auctionHouseId, host, port);
        auctionHouses.put(auctionHouseId, info);

        // Remember mapping from account -> auction house id
        auctionHouseAccountToId.put(accountNumber, auctionHouseId);

        System.out.println("[BANK] Registered AH " + auctionHouseId + " at " + host + ":" + port);

        return new BankMessages.RegisterAuctionHouseResponse(
                true, auctionHouseId, accountNumber,
                "Auction house registered successfully");
    }
}
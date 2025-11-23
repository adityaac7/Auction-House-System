import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central bank logic.
 * Manages the collection of Accounts and Auction Houses.
 */
public class Bank {

    // Storage for all active accounts
    private Map<Integer, BankAccount> accounts;

    // Registry of active Auction Houses
    private Map<Integer, AuctionHouseInfo> auctionHouses;

    // Helper to map a Bank Account Number -> Auction House ID
    private Map<Integer, Integer> auctionHouseAccountToId;

    private int nextAccountNumber;
    private int nextAuctionHouseId;

    public Bank() {
        this.accounts = new ConcurrentHashMap<>();
        this.auctionHouses = new ConcurrentHashMap<>();
        this.auctionHouseAccountToId = new ConcurrentHashMap<>();

        // Start account numbers at 1000 to look realistic
        this.nextAccountNumber = 1000;
        this.nextAuctionHouseId = 1;
    }

    // TODO: Implement registration logic
    // TODO: Implement fund blocking logic
}
/**
 * Represents a bank account with balance tracking.
 * TODO: Implement thread-safe blocking and fund transfer logic.
 */
public class BankAccount {
    private int accountNumber;
    private String accountName;
    private double totalBalance;
    private double blockedFunds;
    private String accountType; // "AGENT" or "AUCTION_HOUSE"

    public BankAccount(int accountNumber, String accountName, double initialBalance,
                       String accountType) {
        this.accountNumber = accountNumber;
        this.accountName = accountName;
        this.totalBalance = initialBalance;
        this.blockedFunds = 0;
        this.accountType = accountType;
    }

    public int getAccountNumber() {
        return accountNumber;
    }

    public String getAccountName() {
        return accountName;
    }

    public double getTotalBalance() {
        return totalBalance;
    }

    /**
     * Calculates available funds (Total - Blocked).
     */
    public double getAvailableFunds() {
        return totalBalance - blockedFunds;
    }

    public synchronized void deposit(double amount) {
        if (amount > 0) {
            totalBalance += amount;
        }
    }

    // TODO: Implement blockFunds logic for auctions
    public boolean blockFunds(double amount) {
        return false;
    }

    // TODO: Implement transfer logic
    public boolean transferFunds(double amount) {
        return false;
    }

    @Override
    public String toString() {
        return "Account " + accountNumber + " (" + accountName + "): $" + totalBalance;
    }
}
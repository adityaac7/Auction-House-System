/**
 * Represents a bank account with balance tracking and fund blocking
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

    public synchronized boolean blockFunds(double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        if (getAvailableFunds() >= amount) {
            blockedFunds += amount;
            return true;
        }
        return false;
    }

    public synchronized void unblockFunds(double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        blockedFunds = Math.max(0, blockedFunds - amount);
    }

    public synchronized boolean transferFunds(double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        if (blockedFunds >= amount) {
            blockedFunds -= amount;
            totalBalance -= amount;
            return true;
        }
        return false;
    }

    public synchronized void deposit(double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        totalBalance += amount;
    }

    public synchronized double getTotalBalance() {
        return totalBalance;
    }

    public synchronized double getAvailableFunds() {
        return totalBalance - blockedFunds;
    }

    public synchronized double getBlockedFunds() {
        return blockedFunds;
    }

    public int getAccountNumber() {
        return accountNumber;
    }

    public String getAccountName() {
        return accountName;
    }

    public String getAccountType() {
        return accountType;
    }

    @Override
    public String toString() {
        return String.format("Account %d (%s): Total=$%.2f, Available=$%.2f, Blocked=$%.2f",
                accountNumber, accountName, totalBalance, getAvailableFunds(), blockedFunds);
    }
}

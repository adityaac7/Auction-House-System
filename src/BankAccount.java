
/**
 * Represents a bank account with balance tracking and fund blocking.
 * Handles thread-safety for financial transactions using synchronized methods.
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

    /**
     * Blocks funds for a bid. Returns true if sufficient funds are available.
     */
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

    /**
     * Unblocks funds (e.g., when an agent is outbid).
     */
    public synchronized void unblockFunds(double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        // Ensure we don't unblock more than what is blocked
        blockedFunds = Math.max(0, blockedFunds - amount);
    }

    /**
     * Transfers blocked funds (e.g., when an auction is won).
     * Removes the amount from both blocked funds and total balance.
     */
    public synchronized boolean transferFunds(double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        // We can only transfer funds that were previously blocked
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
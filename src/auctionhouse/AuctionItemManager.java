package auctionhouse;

import common.AuctionItem;
import common.Message;
import messages.BankMessages;


import java.util.Map;
import java.util.concurrent.*;

/**
 * Manages individual auction items with bidding logic and 30-second timers.
 *
 * <p>This class handles the complete lifecycle of a single auction item, including:
 * <ul>
 *   <li>Validating and accepting bids from agents</li>
 *   <li>Coordinating with the bank to block and release funds</li>
 *   <li>Managing a 30-second auction timer that resets on each new bid</li>
 *   <li>Notifying outbid agents and resolving auctions when time expires</li>
 *   <li>Tracking held funds for all bidders on this item</li>
 * </ul>
 *
 * <p>Thread Safety: This class uses synchronized methods and concurrent collections
 * to ensure safe access from multiple bidding threads. The timer executor operates
 * on a separate thread but accesses shared state through synchronized methods.
 *
 * <p>Timer Behavior: The auction timer is set to exactly 30 seconds and resets to
 * 30 seconds from the current time whenever a new bid is placed. When the timer
 * expires without new bids, the auction is resolved and the winner is notified.
 *
 * @see AuctionItem
 * @see AuctionHouse
 * @see ScheduledExecutorService
 */
public class AuctionItemManager {

    /** The auction item being managed. */
    private AuctionItem item;

    /** Reference to the parent auction house for notifications and callbacks. */
    private AuctionHouse auctionHouse;

    /** Single-threaded executor for managing the 30-second auction timer. */
    private ScheduledExecutorService timerExecutor;

    /** Currently scheduled timer task, cancelled when a new bid arrives. */
    private ScheduledFuture<?> currentTimer;

    /**
     * Map tracking blocked funds for all bidders on this item.
     * Key: agent account number, Value: blocked amount.
     * Uses ConcurrentHashMap for thread-safe access.
     */
    private Map<Integer, Double> heldFunds = new ConcurrentHashMap<>();

    /**
     * Constructs an auction item manager for a specific item.
     *
     * <p>Initializes the timer executor but does not start the auction timer
     * until the first bid is placed.
     *
     * @param item the auction item to manage
     * @param auctionHouse the parent auction house for notifications and bank communication
     */
    public AuctionItemManager(AuctionItem item, AuctionHouse auctionHouse) {
        this.item = item;
        this.auctionHouse = auctionHouse;
        this.timerExecutor = Executors.newSingleThreadScheduledExecutor();
        this.currentTimer = null;
    }

    /**
     * Gets the auction item being managed.
     *
     * @return the auction item
     */
    public AuctionItem getItem() {
        return item;
    }

    /**
     * Attempts to place a bid on this item.
     *
     * <p>This method performs the following validation and operations:
     * <ol>
     *   <li>Validates bid amount is positive, meets minimum, and exceeds current bid</li>
     *   <li>If agent is rebidding, unblocks their previous bid amount</li>
     *   <li>Requests the bank to block funds for the new bid amount</li>
     *   <li>Updates the item's current bid and bidder</li>
     *   <li>Unblocks funds for the previous highest bidder (if different agent)</li>
     *   <li>Notifies the outbid agent and triggers callback events</li>
     *   <li>Resets the auction timer to 30 seconds</li>
     * </ol>
     *
     * <p>Thread Safety: This method is synchronized to prevent concurrent bid
     * processing on the same item, which could cause race conditions in fund
     * blocking/unblocking operations.
     *
     * @param agentAccountNumber the agent's bank account number placing the bid
     * @param bidAmount the amount being bid
     * @return "ACCEPTED" if successful, or "REJECTED: [reason]" if bid is invalid or fails
     */
    public synchronized String placeBid(int agentAccountNumber, double bidAmount) {
        // Validate bid amount
        if (bidAmount <= 0) {
            return "REJECTED: Invalid amount";
        }
        if (bidAmount < item.minimumBid) {
            return "REJECTED: Below minimum bid of $" + item.minimumBid;
        }
        if (bidAmount <= item.currentBid) {
            return "REJECTED: Bid too low (current: $" + item.currentBid + ")";
        }

        try {
            // STEP 1: Handle Self-Rebid
            // If the agent is already the high bidder and wants to bid again, we need to
            // unblock their old bid amount first, then block the new amount. If the new
            // block fails, we have to restore the old block - otherwise their funds would
            // be left unblocked incorrectly.
            double unblockedAmount = 0; // How much we unblocked (if any)
            boolean wasSelfRebid = false;
            
            if (heldFunds.containsKey(agentAccountNumber)) {
                wasSelfRebid = true;
                double oldAmount = heldFunds.get(agentAccountNumber);
                System.out.println("[ITEM " + item.itemId + "] Agent " + agentAccountNumber
                        + " rebidding - unblocking old $" + oldAmount);

                BankMessages.UnblockFundsRequest unblockSelf =
                        new BankMessages.UnblockFundsRequest(agentAccountNumber, oldAmount);
                Message unblockSelfResponse = auctionHouse.sendToBankAndWait(unblockSelf);

                if (unblockSelfResponse instanceof BankMessages.UnblockFundsResponse) {
                    BankMessages.UnblockFundsResponse unblockResp =
                            (BankMessages.UnblockFundsResponse) unblockSelfResponse;

                    if (unblockResp.success) {
                        heldFunds.remove(agentAccountNumber);
                        unblockedAmount = oldAmount; // Track amount we unblocked
                    } else {
                        // Unblock failed - can't proceed with rebid
                        System.err.println("[ITEM " + item.itemId + "] ERROR: Failed to unblock self-rebid funds: "
                                + unblockResp.message);
                        return "REJECTED: Failed to unblock previous bid";
                    }
                } else {
                    // Bank communication error during unblock
                    System.err.println("[ITEM " + item.itemId + "] ERROR: Bank communication failed during unblock");
                    return "REJECTED: Bank communication error";
                }
            }

            // STEP 2: Block NEW funds
            BankMessages.BlockFundsRequest blockRequest =
                    new BankMessages.BlockFundsRequest(agentAccountNumber, bidAmount);
            Message response = auctionHouse.sendToBankAndWait(blockRequest);

            if (response instanceof BankMessages.BlockFundsResponse) {
                BankMessages.BlockFundsResponse blockResponse =
                        (BankMessages.BlockFundsResponse) response;

                if (!blockResponse.success) {
                    System.out.println("[ITEM " + item.itemId + "] Agent " + agentAccountNumber
                            + " - Insufficient funds for $" + bidAmount);
                    
                    // IMPORTANT: If we unblocked their old bid but the new block failed,
                    // we need to re-block the old amount. Otherwise their funds are stuck
                    // in an unblocked state even though they should still have a bid active.
                    if (wasSelfRebid && unblockedAmount > 0) {
                        System.out.println("[ITEM " + item.itemId + "] Restoring previous bid block of $"
                                + unblockedAmount + " after failed rebid");
                        try {
                            BankMessages.BlockFundsRequest restoreBlock =
                                    new BankMessages.BlockFundsRequest(agentAccountNumber, unblockedAmount);
                            Message restoreResponse = auctionHouse.sendToBankAndWait(restoreBlock);
                            
                            if (restoreResponse instanceof BankMessages.BlockFundsResponse) {
                                BankMessages.BlockFundsResponse restoreBlockResp =
                                        (BankMessages.BlockFundsResponse) restoreResponse;
                                if (restoreBlockResp.success) {
                                    heldFunds.put(agentAccountNumber, unblockedAmount);
                                    System.out.println("[ITEM " + item.itemId + "] ✓ Previous bid restored");
                                } else {
                                    System.err.println("[ITEM " + item.itemId + "] CRITICAL: Failed to restore previous bid! "
                                            + "Agent " + agentAccountNumber + " has $" + unblockedAmount
                                            + " unblocked but not re-blocked. State inconsistent!");
                                }
                            }
                        } catch (Exception e) {
                            System.err.println("[ITEM " + item.itemId + "] CRITICAL ERROR restoring bid: "
                                    + e.getMessage());
                        }
                    }
                    return "REJECTED: Insufficient funds";
                }
            } else {
                // Bank communication error - if we unblocked self-rebid, restore it
                System.err.println("[ITEM " + item.itemId + "] ERROR: Bank communication failed after unblocking self-rebid");
                
                if (wasSelfRebid && unblockedAmount > 0) {
                    System.out.println("[ITEM " + item.itemId + "] Attempting to restore previous bid block of $"
                            + unblockedAmount);
                    try {
                        BankMessages.BlockFundsRequest restoreBlock =
                                new BankMessages.BlockFundsRequest(agentAccountNumber, unblockedAmount);
                        Message restoreResponse = auctionHouse.sendToBankAndWait(restoreBlock);
                        
                        if (restoreResponse instanceof BankMessages.BlockFundsResponse) {
                            BankMessages.BlockFundsResponse restoreBlockResp =
                                    (BankMessages.BlockFundsResponse) restoreResponse;
                            if (restoreBlockResp.success) {
                                heldFunds.put(agentAccountNumber, unblockedAmount);
                                System.out.println("[ITEM " + item.itemId + "] ✓ Previous bid restored");
                            } else {
                                System.err.println("[ITEM " + item.itemId + "] CRITICAL: Failed to restore previous bid!");
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("[ITEM " + item.itemId + "] CRITICAL ERROR restoring bid: "
                                + e.getMessage());
                    }
                }
                return "REJECTED: Bank communication error";
            }

            // STEP 3: Save previous bidder BEFORE updating
            int previousBidder = item.currentBidderAccountNumber;

            // STEP 4: Update item
            item.currentBid = bidAmount;
            item.currentBidderAccountNumber = agentAccountNumber;
            heldFunds.put(agentAccountNumber, bidAmount);

            System.out.println("[ITEM " + item.itemId + "] New bid accepted: $" + bidAmount
                    + " from agent " + agentAccountNumber);

            // STEP 5: UNBLOCK PREVIOUS BIDDER'S FUNDS
            if (previousBidder != -1 && previousBidder != agentAccountNumber) {
                Double previousHeldAmount = heldFunds.get(previousBidder);

                if (previousHeldAmount != null) {
                    System.out.println("[ITEM " + item.itemId + "] Unblocking $"
                            + previousHeldAmount + " for outbid agent " + previousBidder);

                    try {
                        BankMessages.UnblockFundsRequest unblockRequest =
                                new BankMessages.UnblockFundsRequest(
                                        previousBidder, previousHeldAmount);
                        Message unblockResponse = auctionHouse.sendToBankAndWait(unblockRequest);

                        if (unblockResponse instanceof BankMessages.UnblockFundsResponse) {
                            BankMessages.UnblockFundsResponse unblockResp =
                                    (BankMessages.UnblockFundsResponse) unblockResponse;

                            if (unblockResp.success) {
                                System.out.println("[ITEM " + item.itemId
                                        + "] Successfully unblocked funds for agent "
                                        + previousBidder);
                                heldFunds.remove(previousBidder);
                            } else {
                                System.err.println("[ITEM " + item.itemId
                                        + "] WARNING: Failed to unblock: "
                                        + unblockResp.message);
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("[ITEM " + item.itemId
                                + "] ERROR unblocking: " + e.getMessage());
                    }
                }

                // STEP 6: Notify previous bidder
                String msg = "You were outbid on " + item.description
                        + ". New bid: $" + bidAmount;
                auctionHouse.notifyAgent(previousBidder, item.itemId, "OUTBID", msg,
                        bidAmount, auctionHouse.getAuctionHouseAccountNumber(), item.description);

                // STEP 7: Notify GUI callback
                AuctionHouse.AuctionHouseCallback callback = auctionHouse.getCallback();
                if (callback != null) {
                    callback.onAgentOutbid(item.itemId, item.description,
                            previousBidder, agentAccountNumber, bidAmount);
                }
            }

            // STEP 8: RESET timer to 30 seconds
            startAuctionTimer();
            return "ACCEPTED";

        } catch (Exception e) {
            System.err.println("[ITEM " + item.itemId + "] Error: " + e.getMessage());
            e.printStackTrace();
            return "REJECTED: Internal error";
        }
    }

    /**
     * Releases blocked funds for all losing bidders.
     *
     * <p>This method is called when the auction ends and the winner is confirmed.
     * It iterates through all agents who have blocked funds on this item and
     * unblocks funds for everyone except the winner. The winner's funds remain
     * blocked until they complete the transfer to the auction house.
     *
     * <p>Thread Safety: This method is synchronized to prevent concurrent access
     * during fund release operations.
     *
     * @param winnerAccountNumber the account number of the winning bidder whose
     *                            funds should remain blocked
     */
    public synchronized void releaseLoserFunds(int winnerAccountNumber) {
        System.out.println("[ITEM " + item.itemId
                + "] Releasing remaining blocked funds (winner: " + winnerAccountNumber + ")");

        for (Map.Entry<Integer, Double> entry : heldFunds.entrySet()) {
            int account = entry.getKey();
            double amount = entry.getValue();

            if (account != winnerAccountNumber) {
                System.out.println("[ITEM " + item.itemId + "] WARNING: Found blocked $"
                        + amount + " for agent " + account);

                try {
                    BankMessages.UnblockFundsRequest unblock =
                            new BankMessages.UnblockFundsRequest(account, amount);
                    Message response = auctionHouse.sendToBankAndWait(unblock);

                    if (response instanceof BankMessages.UnblockFundsResponse) {
                        BankMessages.UnblockFundsResponse unblockResp =
                                (BankMessages.UnblockFundsResponse) response;

                        if (unblockResp.success) {
                            System.out.println("[ITEM " + item.itemId + "] Unblocked $"
                                    + amount + " for agent " + account);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("[ITEM " + item.itemId + "] Error: " + e.getMessage());
                }
            }
        }
        heldFunds.clear();
    }

    /**
     * Resets the auction timer to exactly 30 seconds from the current time.
     *
     * <p>This method is called whenever a new bid is placed. It cancels any existing
     * timer and schedules a new auction resolution task to run in exactly 30 seconds.
     * This ensures that the auction only ends after 30 consecutive seconds with no
     * new bids.
     *
     * <p>Thread Safety: This method is synchronized to prevent race conditions when
     * cancelling and rescheduling timers from multiple bidding threads.
     */
    private synchronized void startAuctionTimer() {
        // Cancel existing timer
        if (currentTimer != null && !currentTimer.isDone()) {
            currentTimer.cancel(false);
        }

        // RESET: Set auction end time to exactly 30 seconds from NOW
        item.auctionEndTime = System.currentTimeMillis() + 30000;

        System.out.println("[ITEM " + item.itemId + "] Timer RESET to 30 seconds");

        // Schedule auction resolution in exactly 30 seconds
        currentTimer = timerExecutor.schedule(() -> {
            try {
                resolveAuction();
            } catch (Exception e) {
                System.err.println("[ITEM " + item.itemId + "] Error: " + e.getMessage());
                e.printStackTrace();
            }
        }, 30, TimeUnit.SECONDS);
    }

    /**
     * Resolves the auction when the timer expires (30 seconds with no new bids).
     *
     * <p>If the item has no bids, it remains listed for future bidding. If there
     * is a winning bidder, this method sends a WINNER notification to that agent
     * with the final price and auction house account information. The agent is then
     * responsible for initiating the payment transfer.
     *
     * <p>Thread Safety: This method is synchronized because it is called from the
     * timer executor thread and accesses shared item state.
     * 
     * <p>Race Condition Protection: Checks if timer was reset (auctionEndTime changed)
     * to prevent resolving an auction that just received a new bid.
     */
    private synchronized void resolveAuction() {
        System.out.println("[ITEM " + item.itemId + "] ========================================");
        System.out.println("[ITEM " + item.itemId + "] Timer expired - Resolving auction...");

        // Race condition check: If a bid came in right as the timer was expiring,
        // the auctionEndTime would have been updated to a future time. Check if that
        // happened before we resolve the auction.
        long currentTime = System.currentTimeMillis();
        if (item.auctionEndTime > currentTime) {
            // Timer got reset by a new bid - don't resolve yet
            System.out.println("[ITEM " + item.itemId + "] Timer was reset - auction still active");
            System.out.println("[ITEM " + item.itemId + "] ========================================");
            return;
        }

        if (item.currentBidderAccountNumber == -1) {
            System.out.println("[ITEM " + item.itemId + "] No bids placed - item remains listed");
            System.out.println("[ITEM " + item.itemId + "] ========================================");
            return;
        }

        int winnerAccount = item.currentBidderAccountNumber;
        double finalPrice = item.currentBid;

        System.out.println("[ITEM " + item.itemId + "] Winner: Agent " + winnerAccount);
        System.out.println("[ITEM " + item.itemId + "] Final Price: $" + finalPrice);
        System.out.println("[ITEM " + item.itemId + "] Sending WINNER notification...");

        String msg = "You won " + item.description + " for $" + finalPrice
                + "! Confirming purchase...";

        auctionHouse.notifyAgent(winnerAccount, item.itemId, "WINNER", msg,
                finalPrice, auctionHouse.getAuctionHouseAccountNumber(), item.description);

        System.out.println("[ITEM " + item.itemId + "] ✓ WINNER notification sent");
        System.out.println("[ITEM " + item.itemId + "] Waiting for agent to confirm...");
        System.out.println("[ITEM " + item.itemId + "] ========================================");
    }

    /**
     * Resets the item with a new description and minimum bid after a sale.
     *
     * <p>Note: This method is no longer used in the current implementation.
     * Items are removed from the auction entirely after being sold rather than
     * being reset and reused.
     *
     * <p>Thread Safety: This method is synchronized to ensure atomic updates
     * to all item fields.
     *
     * @param newDescription the new description for the item
     * @param newMinimumBid the new minimum bid amount
     * @deprecated Items are now removed after sale instead of being reset
     */
    public synchronized void resetItem(String newDescription, double newMinimumBid) {
        item.description = newDescription;
        item.minimumBid = newMinimumBid;
        item.currentBid = 0;
        item.currentBidderAccountNumber = -1;
        item.auctionEndTime = 0;

        System.out.println("[ITEM " + item.itemId + "] Reset with: "
                + newDescription + " (Min bid: $" + newMinimumBid + ")");
    }

    /**
     * Shuts down the timer executor cleanly.
     *
     * <p>This method should be called when the item is sold or removed from the
     * auction. It attempts to gracefully shut down the executor, waiting up to
     * 5 seconds for any running tasks to complete. If tasks do not complete within
     * the timeout, it forces shutdown with {@code shutdownNow()}.
     *
     * <p>This prevents resource leaks by ensuring that background timer threads
     * are properly terminated when no longer needed.
     */
    public void shutdown() {
        if (timerExecutor != null && !timerExecutor.isShutdown()) {
            System.out.println("[ITEM " + item.itemId + "] Shutting down timer executor");
            timerExecutor.shutdown();
            try {
                if (!timerExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    timerExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                timerExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}

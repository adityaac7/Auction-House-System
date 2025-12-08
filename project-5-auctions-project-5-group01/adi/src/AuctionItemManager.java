import java.util.Map;
import java.util.concurrent.*;


/**
 * Manages individual auction items with bidding logic and 30-second timers.
 * Timer RESETS to 30 seconds on each new bid.
 */
public class AuctionItemManager {

    private AuctionItem item;
    private NetworkClient bankClient;
    private AuctionHouse auctionHouse;

    private ScheduledExecutorService timerExecutor;
    private ScheduledFuture<?> currentTimer;

    // Track blocked funds for ALL bidders on this item
    private Map<Integer, Double> heldFunds = new ConcurrentHashMap<>();

    public AuctionItemManager(AuctionItem item,
                              NetworkClient bankClient,
                              AuctionHouse auctionHouse) {
        this.item = item;
        this.bankClient = bankClient;
        this.auctionHouse = auctionHouse;
        this.timerExecutor = Executors.newSingleThreadScheduledExecutor();
        this.currentTimer = null;
    }

    public AuctionItem getItem() {
        return item;
    }

    /**
     * Attempts to place a bid on this item.
     * Check and block funds BEFORE accepting bid.
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
            if (heldFunds.containsKey(agentAccountNumber)) {
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
                    }
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
                    return "REJECTED: Insufficient funds";
                }
            } else {
                return "REJECTED: Bank communication error";
            }

            // STEP 3: Save previous bidder BEFORE updating
            int previousBidder = item.currentBidderAccountNumber;
            double previousBid = item.currentBid;

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

            // STEP 8: ⭐ RESET timer to 30 seconds
            startAuctionTimer();
            return "ACCEPTED";

        } catch (Exception e) {
            System.err.println("[ITEM " + item.itemId + "] Error: " + e.getMessage());
            e.printStackTrace();
            return "REJECTED: Internal error";
        }
    }

    /**
     * Release blocked funds for all losing bidders.
     * Called when auction ends and winner is confirmed.
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
     * ⭐ RESET timer to exactly 30 seconds on each new bid
     */
    private synchronized void startAuctionTimer() {
        // Cancel existing timer
        if (currentTimer != null && !currentTimer.isDone()) {
            currentTimer.cancel(false);
        }

        // ⭐ RESET: Set auction end time to exactly 30 seconds from NOW
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
     * Called when timer expires (30 seconds with no new bids)
     */
    private synchronized void resolveAuction() {
        System.out.println("[ITEM " + item.itemId + "] ========================================");
        System.out.println("[ITEM " + item.itemId + "] Timer expired - Resolving auction...");

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
     * Reset item after sale (not used anymore - items are removed instead)
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
     * Shutdown the timer executor cleanly
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


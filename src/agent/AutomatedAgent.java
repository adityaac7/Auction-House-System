package agent;

import common.AuctionHouseInfo;
import common.AuctionItem;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Automated agent with improved bidding strategies for distributed auctions.
 *
 * <p>This agent implements several optimization strategies:
 * <ul>
 *   <li>Last-minute sniping to avoid bid wars</li>
 *   <li>Budget management to prevent overcommitment</li>
 *   <li>Selective bidding based on item value and competition</li>
 *   <li>Dynamic bid increments based on remaining time</li>
 * </ul>
 *
 * @see Agent
 */
public class AutomatedAgent {

    private final Agent agent;
    private volatile boolean running;
    private Thread biddingThread;
    private final Random random;

    /** Multiplier for calculating competitive bids (default: 1.1 = 10% increase) */
    private final double bidMultiplier;

    /** Maximum percentage of balance to commit to single item */
    private final double maxItemBudgetRatio;

    /** Minimum time (ms) remaining before placing snipe bid */
    private final long snipeWindowMs;

    /** Track our active bids to manage budget */
    private final Map<Integer, Double> activeBids;

    /** Track items we're interested in but waiting to snipe */
    private final Map<Integer, AuctionItem> watchList;

    /**
     * Constructs an automated agent with default bidding parameters.
     *
     * @param bankHost the bank server hostname
     * @param bankPort the bank server port
     * @param initialBalance the starting balance for the agent
     * @throws IOException if connection to bank fails
     * @throws ClassNotFoundException if message deserialization fails
     */
    public AutomatedAgent(String bankHost, int bankPort, double initialBalance)
            throws IOException, ClassNotFoundException {
        this(bankHost, bankPort, initialBalance, 1.08, 0.3);
    }

    /**
     * Constructs an automated agent with optimized bidding parameters.
     *
     * @param bankHost the bank server hostname
     * @param bankPort the bank server port
     * @param initialBalance the starting balance for the agent
     * @param bidMultiplier bid increase multiplier (1.05-1.15 recommended)
     * @param maxItemBudgetRatio max % of balance per item (0.2-0.4 recommended)
     * @throws IOException if connection to bank fails
     * @throws ClassNotFoundException if message deserialization fails
     */
    public AutomatedAgent(String bankHost, int bankPort, double initialBalance,
                          double bidMultiplier, double maxItemBudgetRatio)
            throws IOException, ClassNotFoundException {
        this.agent = new Agent("autoagent", initialBalance, bankHost, bankPort);
        this.random = new Random();
        this.bidMultiplier = bidMultiplier;
        this.maxItemBudgetRatio = maxItemBudgetRatio;
        this.snipeWindowMs = 5000; // Bid in last 5 seconds
        this.activeBids = new ConcurrentHashMap<>();
        this.watchList = new ConcurrentHashMap<>();
        this.running = false;
        
        // Register callback to clean up tracking when items are sold
        agent.setUICallback(new Agent.AgentUICallback() {
            @Override
            public void onBalanceUpdated(double total, double available, double blocked) {
                // Balance updates are handled automatically by the agent
                // No action needed here for automated bidding
            }

            @Override
            public void onItemsUpdated(AuctionItem[] items) {
                // Item updates are processed in the main bidding loop
                // No action needed here
            }

            @Override
            public void onBidStatusChanged(int itemId, String status, String message) {
                // Clean up tracking when item is sold or we're outbid
                if ("ITEM_SOLD".equals(status) || "OUTBID".equals(status)) {
                    activeBids.remove(itemId);
                    watchList.remove(itemId);
                }
            }

            @Override
            public void onPurchasesUpdated(java.util.List<Agent.Purchase> purchases) {
                // Clean up any items we won
                for (Agent.Purchase purchase : purchases) {
                    activeBids.remove(purchase.itemId);
                    watchList.remove(purchase.itemId);
                }
            }
        });
    }

    /**
     * Starts the automated bidding loop with improved strategy.
     */
    public void start() {
        if (running) {
            return;
        }

        running = true;
        biddingThread = new Thread(this::runBiddingLoop);
        biddingThread.setDaemon(true);
        biddingThread.start();
        System.out.println("[AUTO AGENT] Started with optimized strategy");
    }

    /**
     * Main bidding loop with timing-aware strategy.
     */
    private void runBiddingLoop() {
        int iterationCount = 0;
        while (running) {
            try {
                // Refresh auction houses
                agent.refreshAuctionHouses();

                // Get list of auction houses
                AuctionHouseInfo[] auctionHousesArray = agent.getAuctionHouses();

                if (auctionHousesArray == null || auctionHousesArray.length == 0) {
                    Thread.sleep(1000);
                    continue;
                }

                // Process items with timing strategy
                processItemsStrategically(auctionHousesArray);

                // Periodically clean up stale watchlist items (every 10 iterations)
                iterationCount++;
                if (iterationCount % 10 == 0) {
                    cleanupStaleWatchlist();
                }

                // Variable sleep based on activity (1-2 seconds)
                Thread.sleep(1000 + random.nextInt(1000));

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("[AUTO AGENT] Error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Removes stale items from the watchlist that are no longer available.
     * This prevents memory leaks from items that were sold but not properly cleaned up.
     */
    private void cleanupStaleWatchlist() {
        long currentTime = System.currentTimeMillis();
        watchList.entrySet().removeIf(entry -> {
            AuctionItem item = entry.getValue();
            // Remove if auction ended more than 10 seconds ago
            return item.auctionEndTime > 0 && item.auctionEndTime < currentTime - 10000;
        });
    }

    /**
     * Processes items using strategic timing and budget management.
     */
    private void processItemsStrategically(AuctionHouseInfo[] auctionHouses) {
        // Calculate available balance
        double totalBalance = agent.getTotalBalance();
        double blockedFunds = agent.getBlockedFunds();
        double availableBalance = totalBalance - blockedFunds;
        double maxBidPerItem = availableBalance * maxItemBudgetRatio;

        for (AuctionHouseInfo ahInfo : auctionHouses) {
            try {
                // Connect if not already connected
                agent.connectToAuctionHouse(ahInfo.auctionHouseId);
                agent.startListeningForNotifications(ahInfo.auctionHouseId);

                // Get items from this auction house
                AuctionItem[] items = agent.getItemsFromAuctionHouse(ahInfo.auctionHouseId);

                if (items != null) {
                    for (AuctionItem item : items) {
                        processItemWithTiming(ahInfo, item, maxBidPerItem);
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                System.err.println("[AUTO AGENT] Error getting items from "
                        + ahInfo.auctionHouseId + ": " + e.getMessage());
            }
        }
    }

    /**
     * Processes a single item with timing-aware bidding strategy.
     *
     * <p>Strategy:
     * - Only bids if there is already another bidder (no first bids)
     * - If timer > 5 seconds: Add to watchlist, don't bid yet (sniping)
     * - If timer <= 5 seconds: Execute snipe bid
     * - If already winning: Monitor but don't increase bid
     */
    private void processItemWithTiming(AuctionHouseInfo ahInfo,
                                       AuctionItem item,
                                       double maxBidPerItem) {
        try {
            // Skip if we're already the highest bidder
            if (item.currentBidderAccountNumber == agent.getAccountNumber()) {
                return;
            }

            // Only bid if there is already another bidder
            // Skip items with no bids (auctionEndTime == 0 or currentBid <= 0)
            if (item.auctionEndTime == 0 || item.currentBid <= 0) {
                // No bids yet - wait for someone else to bid first
                return;
            }

            // Calculate time remaining
            long timeRemaining = item.auctionEndTime - System.currentTimeMillis();

            // SNIPING STRATEGY: Only bid in last few seconds
            if (timeRemaining > snipeWindowMs) {
                // Add to watchlist for later
                watchList.put(item.itemId, item);
                return;
            }

            // Time to snipe! Place competitive bid
            placeSnipeBid(ahInfo, item, maxBidPerItem);

        } catch (Exception e) {
            System.err.println("[AUTO AGENT] Error processing item "
                    + item.itemId + ": " + e.getMessage());
        }
    }

    /**
     * Places snipe bid in the last few seconds of auction.
     *
     * <p>This strategy minimizes the time for others to counter-bid,
     * often winning at lower prices.
     */
    private void placeSnipeBid(AuctionHouseInfo ahInfo,
                               AuctionItem item,
                               double maxBidPerItem) {
        try {
            double bidAmount = calculateSnipeBid(item, maxBidPerItem);

            if (bidAmount <= item.currentBid || bidAmount > maxBidPerItem) {
                return;
            }

            boolean success = agent.placeBid(ahInfo.auctionHouseId, item.itemId, bidAmount);
            if (success) {
                activeBids.put(item.itemId, bidAmount);
                System.out.println("[AUTO AGENT] SNIPE BID: $" +
                        String.format("%.2f", bidAmount) +
                        " on item " + item.itemId +
                        " (" + item.description + ")");
                watchList.remove(item.itemId);
            }
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("[AUTO AGENT] Snipe failed: " + e.getMessage());
        }
    }

    /**
     * Calculates optimal snipe bid amount.
     *
     * <p>Uses the configured bidMultiplier to determine bid increments,
     * with some randomization to avoid predictable patterns.
     */
    private double calculateSnipeBid(AuctionItem item, double maxBidPerItem) {
        if (item == null) {
            throw new IllegalArgumentException("Item cannot be null");
        }

        double bidAmount;

        if (item.currentBid <= 0) {
            // First bid: near minimum with small random variation
            bidAmount = item.minimumBid * (1.0 + random.nextDouble() * 0.15);
        } else {
            // Snipe: use bidMultiplier with some randomization
            // Apply multiplier with small random variation (±2%)
            double multiplierVariation = bidMultiplier + (random.nextDouble() - 0.5) * 0.04;
            bidAmount = item.currentBid * multiplierVariation;
            
            // Ensure minimum increment of at least 1% to be competitive
            double minIncrement = item.currentBid * 0.01;
            if (bidAmount - item.currentBid < minIncrement) {
                bidAmount = item.currentBid + minIncrement;
            }
        }

        return Math.min(bidAmount, maxBidPerItem);
    }

    /**
     * Stops the automated bidding loop.
     */
    public void stop() {
        running = false;
        agent.disconnect();
        System.out.println("[AUTO AGENT] Stopped");
    }

    /**
     * Gets the underlying agent instance.
     *
     * @return the agent instance
     */
    public Agent getAgent() {
        return agent;
    }

    /**
     * Main entry point for automated agent.
     * 
     * <p>Supports running with default parameters (no arguments) or custom parameters.
     * Default values:
     * <ul>
     *   <li>bankHost: "localhost"</li>
     *   <li>bankPort: 9999</li>
     *   <li>initialBalance: 10000.0</li>
     *   <li>bidMultiplier: 1.08</li>
     *   <li>maxItemBudgetRatio: 0.3</li>
     * </ul>
     *
     * @param args optional command line arguments: [bankHost] [bankPort] [initialBalance] [bidMultiplier] [maxItemBudgetRatio]
     */
    public static void main(String[] args) {
        // Default values
        String bankHost = "localhost";
        int bankPort = 9999; // Default bank port
        double initialBalance = 10000.0;
        double bidMultiplier = 1.08;
        double maxItemBudgetRatio = 0.3;

        // Parse arguments if provided
        if (args.length > 0) {
            bankHost = args[0];
        }
        if (args.length > 1) {
            try {
                bankPort = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid bank port: " + args[1] + ". Using default: " + bankPort);
            }
        }
        if (args.length > 2) {
            try {
                initialBalance = Double.parseDouble(args[2]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid initial balance: " + args[2] + ". Using default: " + initialBalance);
            }
        }
        if (args.length > 3) {
            try {
                bidMultiplier = Double.parseDouble(args[3]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid bid multiplier: " + args[3] + ". Using default: " + bidMultiplier);
            }
        }
        if (args.length > 4) {
            try {
                maxItemBudgetRatio = Double.parseDouble(args[4]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid max item budget ratio: " + args[4] + ". Using default: " + maxItemBudgetRatio);
            }
        }

        // Show configuration
        if (args.length == 0) {
            System.out.println("[AUTO AGENT] Starting with default parameters:");
        } else {
            System.out.println("[AUTO AGENT] Starting with configuration:");
        }
        System.out.println("  Bank Host: " + bankHost);
        System.out.println("  Bank Port: " + bankPort);
        System.out.println("  Initial Balance: $" + String.format("%.2f", initialBalance));
        System.out.println("  Bid Multiplier: " + bidMultiplier);
        System.out.println("  Max Item Budget Ratio: " + maxItemBudgetRatio);
        System.out.println();

        try {
            AutomatedAgent autoAgent = new AutomatedAgent(
                    bankHost, bankPort, initialBalance, bidMultiplier, maxItemBudgetRatio);

            autoAgent.start();

            System.out.println("[AUTO AGENT] Running... Press Ctrl+C to stop.");
            System.out.println();

            // Keep running until interrupted
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            System.out.println("[AUTO AGENT] Interrupted, shutting down...");
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("[AUTO AGENT] Failed to initialize: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}

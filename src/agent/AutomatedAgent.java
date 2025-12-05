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
        this.agent = new Agent("AutoBot", initialBalance, bankHost, bankPort);
        this.random = new Random();
        this.bidMultiplier = bidMultiplier;
        this.maxItemBudgetRatio = maxItemBudgetRatio;
        this.snipeWindowMs = 5000; // Bid in last 5 seconds
        this.activeBids = new ConcurrentHashMap<>();
        this.watchList = new ConcurrentHashMap<>();
        this.running = false;
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

                // Variable sleep based on activity (1-2 seconds)
                Thread.sleep(1000 + random.nextInt(1000));

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("[AUTO AGENT] Error: " + e.getMessage());
            }
        }
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
     * - If timer > 5 seconds: Add to watchlist, don't bid yet (sniping)
     * - If timer <= 5 seconds: Execute snipe bid
     * - If already winning: Monitor but don't increase bid
     * - If no bids yet: Occasionally place first bid to start timer
     */
    private void processItemWithTiming(AuctionHouseInfo ahInfo,
                                       AuctionItem item,
                                       double maxBidPerItem) {
        try {
            // Skip if we're already the highest bidder
            if (item.currentBidderAccountNumber == agent.getAccountNumber()) {
                return;
            }

            // Calculate time remaining
            long timeRemaining = item.auctionEndTime - System.currentTimeMillis();

            // If no timer started yet (no bids), occasionally place strategic first bid
            if (item.auctionEndTime == 0 || item.currentBid <= 0) {
                if (random.nextDouble() < 0.3) { // 30% chance to start bidding
                    placeStrategicFirstBid(ahInfo, item, maxBidPerItem);
                }
                return;
            }

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
     * Places strategic first bid - slightly above minimum to start timer.
     */
    private void placeStrategicFirstBid(AuctionHouseInfo ahInfo,
                                        AuctionItem item,
                                        double maxBidPerItem) {
        try {
            // Bid just above minimum
            double bidAmount = item.minimumBid * (1.0 + random.nextDouble() * 0.1);

            if (bidAmount > maxBidPerItem) {
                return;
            }

            boolean success = agent.placeBid(ahInfo.auctionHouseId, item.itemId, bidAmount);
            if (success) {
                activeBids.put(item.itemId, bidAmount);
                System.out.println("[AUTO AGENT] First bid: $" +
                        String.format("%.2f", bidAmount) +
                        " on item " + item.itemId);
            }
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("[AUTO AGENT] Failed first bid: " + e.getMessage());
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
     * <p>Uses smaller increment than normal bidding to avoid
     * triggering aggressive counter-bids.
     */
    private double calculateSnipeBid(AuctionItem item, double maxBidPerItem) {
        if (item == null) {
            throw new IllegalArgumentException("Item cannot be null");
        }

        double bidAmount;

        if (item.currentBid <= 0) {
            // First bid: near minimum
            bidAmount = item.minimumBid * (1.0 + random.nextDouble() * 0.15);
        } else {
            // Snipe: smaller increment (5-10% instead of multiplier)
            double increment = item.currentBid * (0.05 + random.nextDouble() * 0.05);
            bidAmount = item.currentBid + increment;
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
     * @param args command line arguments: bankHost bankPort initialBalance [bidMultiplier] [maxItemBudgetRatio]
     */
    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: java AutomatedAgent <bankHost> <bankPort> <initialBalance> [bidMultiplier] [maxItemBudgetRatio]");
            return;
        }

        try {
            String bankHost = args[0];
            int bankPort = Integer.parseInt(args[1]);
            double initialBalance = Double.parseDouble(args[2]);
            double bidMultiplier = args.length > 3 ? Double.parseDouble(args[3]) : 1.08;
            double maxItemBudgetRatio = args.length > 4 ? Double.parseDouble(args[4]) : 0.3;

            AutomatedAgent autoAgent = new AutomatedAgent(
                    bankHost, bankPort, initialBalance, bidMultiplier, maxItemBudgetRatio);

            autoAgent.start();

            // Keep running until interrupted
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            System.out.println("[AUTO AGENT] Interrupted, shutting down...");
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("[AUTO AGENT] Failed to initialize: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

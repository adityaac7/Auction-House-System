package agent;

import common.AuctionHouseInfo;
import common.AuctionItem;

import java.io.IOException;
import java.util.Random;
/**
* Automated agent that randomly bids on items*/

public class AutomatedAgent {

    private Agent agent;
    private volatile boolean running = false;
    private Random random;
    private long bidInterval;      // Milliseconds between bids
    private double bidMultiplier;// How much to increase bid (e.g., 1.1 = 10% increase)

    public AutomatedAgent(String agentName, double initialBalance, String bankHost, int bankPort, long bidInterval,
                          double bidMultiplier) throws IOException, ClassNotFoundException {
        this.agent = new Agent(agentName, initialBalance, bankHost, bankPort);
        this.random = new Random();
        this.bidInterval = bidInterval;
        this.bidMultiplier = bidMultiplier;
        System.out.println("[AUTO AGENT] Created: " + agentName);
        System.out.println("[AUTO AGENT] Bid Interval: " + bidInterval + "ms");
        System.out.println("[AUTO AGENT] Bid Multiplier: " + bidMultiplier);
    }
    public void start() {
        running = true;

        Thread biddingThread = new Thread(() -> {
            try {
                // Connect to all auction houses
                AuctionHouseInfo[] auctionHouses = agent.getAuctionHouses();
                for (AuctionHouseInfo house : auctionHouses) {
                    agent.connectToAuctionHouse(house.auctionHouseId);
                    agent.startListeningForNotifications(house.auctionHouseId);
                }

                System.out.println("[AUTO AGENT] Connected to "
                        + auctionHouses.length + " auction houses");

                // Continuously bid on items
                while (running) {
                    try {
                        if (auctionHouses.length > 0) {
                            // Select a random auction house
                            AuctionHouseInfo selectedHouse =
                                    auctionHouses[random.nextInt(auctionHouses.length)];

                            // Get items from the auction house
                            AuctionItem[] items = agent.getItemsFromAuctionHouse(
                                    selectedHouse.auctionHouseId);
                            if (items.length > 0) {
                                // Select a random item
                                AuctionItem selectedItem =
                                        items[random.nextInt(items.length)];

                                double bidAmount = calculateBidAmount(selectedItem);

                                // Check if we have enough available funds
                                if (agent.getAvailableFunds() >= bidAmount) {
                                    agent.placeBid(
                                            selectedHouse.auctionHouseId,
                                            selectedItem.itemId,
                                            bidAmount);
                                } else {
                                    System.out.println(
                                            "[AUTO AGENT] Insufficient funds for bid");
                                }
                            }
                        }

                        Thread.sleep(bidInterval);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        System.out.println("[AUTO AGENT] Error during bidding: "
                                + e.getMessage());
                        try {
                            Thread.sleep(bidInterval);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("[AUTO AGENT] Error: " + e.getMessage());
                e.printStackTrace();
            }
        });

        biddingThread.setDaemon(true);
        biddingThread.start();
    }
    private double calculateBidAmount(AuctionItem item) {
        if (item == null) {
            throw new IllegalArgumentException("Item cannot be null");
        }

        if (item.currentBid <= 0) {
            // First bid: near minimum
            return item.minimumBid
                    + random.nextDouble() * item.minimumBid * 0.5;
        } else {
            // Increase current bid
            return item.currentBid * bidMultiplier
                    + random.nextDouble() * item.minimumBid * 0.1;
        }
    }
}
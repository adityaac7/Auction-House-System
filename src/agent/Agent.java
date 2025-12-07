package agent;

import common.AuctionHouseInfo;
import common.AuctionItem;
import common.Message;
import common.NetworkClient;
import messages.AuctionMessages;
import messages.BankMessages;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Represents an agent (bidder) in the distributed auction system.
 * <p>
 * This class manages all agent operations including:
 * <ul>
 *   <li>Registration and communication with the bank server</li>
 *   <li>Connections to multiple auction houses</li>
 *   <li>Placing bids and tracking auction item statuses</li>
 *   <li>Processing bid notifications (outbid, winner, etc.)</li>
 *   <li>Managing account balance and blocked funds</li>
 *   <li>Handling fund transfers for won auctions</li>
 * </ul>
 * <p>
 * The Agent uses a multithreaded architecture with dedicated listener threads
 * for each auction house connection to handle asynchronous notifications.
 * Thread-safe operations are ensured through synchronization and concurrent
 * data structures.
 */
public class Agent {

    /** The unique account number assigned by the bank */
    private int accountNumber;

    /** The display name of this agent */
    private String agentName;

    /** Network client for communication with the bank server */
    private NetworkClient bankClient;

    /** Map of auction house IDs to their network client connections */
    private Map<Integer, NetworkClient> auctionHouseConnections;

    /** Map of auction house IDs to their information objects */
    private Map<Integer, AuctionHouseInfo> auctionHouses;

    /** Map of auction house IDs to their listener threads */
    private Map<Integer, Thread> listenerThreads;

    /** Map of auction house IDs to queues for non-notification responses */
    private Map<Integer, BlockingQueue<Message>> responseQueues;

    /** Callback interface for UI updates */
    private AgentUICallback uiCallback;

    /** Total balance in the agent's account (available + blocked) */
    private volatile double totalBalance;

    /** Funds available for bidding */
    private volatile double availableFunds;

    /** Funds blocked for active bids */
    private volatile double blockedFunds;

    /** Lock object for synchronizing balance updates */
    private final Object balanceLock = new Object();

    /** List of completed purchases */
    private final List<Purchase> purchases =
            Collections.synchronizedList(new ArrayList<>());

    /**
     * Represents a completed purchase by the agent.
     * This record is immutable and thread-safe.
     */
    public static class Purchase {
        /** The ID of the auction house where the item was purchased */
        public final int auctionHouseId;

        /** The unique item ID */
        public final int itemId;

        /** Description of the purchased item */
        public final String description;

        /** Final purchase price */
        public final double price;

        /**
         * Constructs a new Purchase record.
         *
         * @param auctionHouseId the ID of the auction house
         * @param itemId the unique item ID
         * @param description the item description
         * @param price the final purchase price
         */
        public Purchase(int auctionHouseId, int itemId,
                        String description, double price) {
            this.auctionHouseId = auctionHouseId;
            this.itemId = itemId;
            this.description = description;
            this.price = price;
        }
    }

    /**
     * Callback interface for UI updates.
     * Implement this interface to receive real-time notifications about
     * balance changes, item updates, bid status changes, and purchase history.
     */
    public interface AgentUICallback {
        /**
         * Called when the agent's balance is updated.
         *
         * @param total the total balance (available + blocked)
         * @param available funds available for new bids
         * @param blocked funds blocked for active bids
         */
        void onBalanceUpdated(double total, double available, double blocked);

        /**
         * Called when the item list from an auction house is updated.
         *
         * @param items array of current auction items
         */
        void onItemsUpdated(AuctionItem[] items);

        /**
         * Called when a bid status changes (accepted, outbid, winner, etc.).
         *
         * @param itemId the item ID
         * @param status the new status string
         * @param message a descriptive message about the status change
         */
        void onBidStatusChanged(int itemId, String status, String message);

        /**
         * Called when the purchase history is updated.
         *
         * @param purchases the updated list of purchases
         */
        default void onPurchasesUpdated(List<Purchase> purchases) { }
    }

    /**
     * Constructs a new Agent and registers it with the bank server.
     * <p>
     * This constructor performs the following operations:
     * <ol>
     *   <li>Establishes a connection to the bank server</li>
     *   <li>Sends a registration request with initial balance</li>
     *   <li>Receives an account number assignment</li>
     *   <li>Retrieves the list of available auction houses</li>
     * </ol>
     *
     * @param agentName the display name for this agent
     * @param initialBalance the starting account balance
     * @param bankHost the hostname or IP address of the bank server
     * @param bankPort the port number of the bank server
     * @throws IOException if network communication fails
     * @throws ClassNotFoundException if message deserialization fails
     * @throws IllegalStateException if registration is rejected by the bank
     */
    public Agent(String agentName, double initialBalance, String bankHost, int bankPort)
            throws IOException, ClassNotFoundException {
        this.agentName = agentName;
        this.auctionHouseConnections = new ConcurrentHashMap<>();
        this.auctionHouses = new ConcurrentHashMap<>();
        this.listenerThreads = new ConcurrentHashMap<>();
        this.responseQueues = new ConcurrentHashMap<>();

        this.bankClient = new NetworkClient(bankHost, bankPort);

        // Register with bank
        BankMessages.RegisterAgentRequest request =
                new BankMessages.RegisterAgentRequest(agentName, initialBalance);
        bankClient.sendMessage(request);
        BankMessages.RegisterAgentResponse response =
                (BankMessages.RegisterAgentResponse) bankClient.receiveMessage();
        if (response.success) {
            this.accountNumber = response.accountNumber;
            synchronized (balanceLock) {
                this.totalBalance = initialBalance;
                this.availableFunds = initialBalance;
                this.blockedFunds = 0;
            }
            // Store auction houses
            for (AuctionHouseInfo info : response.auctionHouses) {
                auctionHouses.put(info.auctionHouseId, info);
            }

            System.out.println("[AGENT] Registered: " + agentName
                    + " (Account: " + accountNumber + ")");
            System.out.println("[AGENT] Available auction houses: "
                    + auctionHouses.size());
        } else {
            throw new IOException("Failed to register agent: " + response.message);
        }
    }
    /**
     * Checks if the agent is connected to the specified auction house.
     *
     * <p>This method verifies both that a connection exists and that the
     * underlying network connection is still active.
     *
     * @param auctionHouseId the unique ID of the auction house
     * @return true if connected and connection is active, false otherwise
     */
    public boolean isConnectedToAuctionHouse(int auctionHouseId) {
        NetworkClient connection = auctionHouseConnections.get(auctionHouseId);
        return connection != null && connection.isConnected();
    }

    /**
     * Sets the UI callback for receiving real-time updates.
     *
     * @param callback the callback implementation, or null to disable callbacks
     */
    public void setUICallback(AgentUICallback callback) {
        this.uiCallback = callback;
    }

    /**
     * Returns the unique account number assigned by the bank.
     *
     * @return the account number
     */
    public int getAccountNumber() {
        return accountNumber;
    }

    /**
     * Returns the display name of this agent.
     *
     * @return the agent name
     */
    public String getAgentName() {
        return agentName;
    }

    /**
     * Returns the total account balance (available + blocked funds).
     * This method is thread-safe.
     *
     * @return the total balance
     */
    public double getTotalBalance() {
        synchronized (balanceLock) {
            return totalBalance;
        }
    }

    /**
     * Returns the funds available for placing new bids.
     * This method is thread-safe.
     *
     * @return the available funds
     */
    public double getAvailableFunds() {
        synchronized (balanceLock) {
            return availableFunds;
        }
    }

    /**
     * Returns the funds currently blocked for active bids.
     * This method is thread-safe.
     *
     * @return the blocked funds
     */
    public double getBlockedFunds() {
        synchronized (balanceLock) {
            return blockedFunds;
        }
    }

    /**
     * Returns a thread-safe copy of the purchase history.
     *
     * @return a new list containing all purchases
     */
    public List<Purchase> getPurchases() {
        synchronized (purchases) {
            return new ArrayList<>(purchases);
        }
    }

    /**
     * Returns an array of all registered auction houses.
     *
     * @return array of AuctionHouseInfo objects
     */
    public AuctionHouseInfo[] getAuctionHouses() {
        return auctionHouses.values().toArray(new AuctionHouseInfo[0]);
    }

    /**
     * Establishes a connection to the specified auction house if not already connected.
     * This method is idempotent and thread-safe.
     * <p>
     * Also initializes the response queue for handling non-notification messages
     * from this auction house.
     *
     * @param auctionHouseId the unique ID of the auction house
     * @throws IOException if the connection cannot be established
     * @throws RuntimeException if the auction house ID is invalid
     */
    public void connectToAuctionHouse(int auctionHouseId) throws IOException {
        auctionHouseConnections.computeIfAbsent(auctionHouseId, id -> {
            try {
                AuctionHouseInfo info = auctionHouses.get(id);
                if (info == null) {
                    throw new RuntimeException("Auction house " + id + " not found");
                }
                NetworkClient connection = new NetworkClient(info.host, info.port);
                System.out.println("[AGENT] Connected to auction house " + id
                        + " at " + info.host + ":" + info.port);
                responseQueues.putIfAbsent(id, new LinkedBlockingQueue<>());
                return connection;
            } catch (IOException e) {
                throw new RuntimeException("Failed to connect: "
                        + e.getMessage(), e);
            }
        });
    }

    /**
     * Starts a dedicated listener thread for receiving asynchronous notifications
     * from the specified auction house.
     * <p>
     * The listener thread handles:
     * <ul>
     *   <li>Bid status notifications (ACCEPTED, OUTBID, WINNER)</li>
     *   <li>Response messages for synchronous requests</li>
     *   <li>Connection recovery with up to 3 retry attempts</li>
     * </ul>
     * <p>
     * If a listener thread is already running for this auction house, this method
     * returns without starting a new thread.
     *
     * @param auctionHouseId the unique ID of the auction house
     */
    public void startListeningForNotifications(int auctionHouseId) {
        // Check if listener is already running
        Thread existingThread = listenerThreads.get(auctionHouseId);
        if (existingThread != null && existingThread.isAlive()) {
            System.out.println("[AGENT] Listener already running for auction house "
                    + auctionHouseId);
            return; // Don't restart
        }

        // Remove any dead thread from map
        if (existingThread != null) {
            System.out.println("[AGENT] Previous listener thread for auction house "
                    + auctionHouseId + " is dead, starting new one");
            listenerThreads.remove(auctionHouseId);
        }

        Thread listenerThread = new Thread(() -> {
            int retries = 0;
            while (retries < 3 && !Thread.currentThread().isInterrupted()) {
                try {
                    // Get connection and store reference to avoid race condition
                    NetworkClient connection = auctionHouseConnections.get(auctionHouseId);
                    if (connection == null) {
                        System.out.println("[AGENT] No connection to auction house "
                                + auctionHouseId);
                        return;
                    }

                    System.out.println("[AGENT] Started listening for notifications from auction house "
                            + auctionHouseId);

                    // Snapshot the connection reference to avoid issues if another thread removes
                    // it from the map while we're using it. We check it's still the same one
                    // in the loop condition.
                    NetworkClient localConnection = connection;
                    while (localConnection.isConnected()
                            && !Thread.currentThread().isInterrupted()
                            && auctionHouseConnections.get(auctionHouseId) == localConnection) {
                        try {
                            Message message = localConnection.receiveMessage();

                            if (message instanceof AuctionMessages.BidStatusNotification) {
                                AuctionMessages.BidStatusNotification n =
                                        (AuctionMessages.BidStatusNotification) message;

                                System.out.println("[AGENT] Notification for item "
                                        + n.itemId + ": " + n.status + " - " + n.message);

                                if (uiCallback != null) {
                                    uiCallback.onBidStatusChanged(
                                            n.itemId, n.status, n.message);
                                }

                                // Winner notifications need special handling - we have to transfer money
                                // and confirm with the auction house. This can take a while, so do it
                                // in a background thread to keep the listener responsive.
                                if ("WINNER".equals(n.status)) {
                                    // Capture these in final variables for the lambda
                                    final int ahId = auctionHouseId;
                                    final int itId = n.itemId;
                                    final double price = n.finalPrice;
                                    final int ahAccount = n.auctionHouseAccountNumber;
                                    final String desc = n.itemDescription;
                                    
                                    new Thread(() -> {
                                        confirmWinner(ahId, itId, price, ahAccount, desc);
                                    }).start();
                                }
                                // For other notifications (OUTBID, ITEM_SOLD, etc), we don't update balance
                                // here to avoid blocking. The balance will get refreshed when the user
                                // places another bid or when confirmWinner finishes.
                            } else {
                                // This isn't a notification - it's a response to something we requested
                                // (like GetItemsResponse or PlaceBidResponse). Put it in the queue so
                                // the waiting thread can pick it up.
                                BlockingQueue<Message> queue = responseQueues.get(auctionHouseId);
                                if (queue != null) {
                                    boolean offered = queue.offer(message, 5, TimeUnit.SECONDS);
                                    if (!offered) {
                                        // Queue is full or we got interrupted - this shouldn't happen normally
                                        System.out.println("[AGENT] WARNING: Failed to queue response message type: "
                                                + message.getMessageType() + " - queue may be full or interrupted");
                                    }
                                } else {
                                    // No queue means no one is waiting for this response - drop it
                                    System.out.println("[AGENT] WARNING: No response queue for auction house "
                                            + auctionHouseId + ", dropping message: " + message.getMessageType());
                                }
                            }
                        } catch (java.net.SocketTimeoutException e) {
                            // Timeout is OK - just means no messages, connection still alive
                            // Don't log this, it's normal
                            continue;
                        } catch (IOException e) {
                            String errorMsg = e.getMessage();
                            System.out.println("[AGENT] I/O error reading from auction house "
                                    + auctionHouseId + ": " + errorMsg);
                            
                            // Check for stream corruption errors - these indicate the connection is unusable
                            if (errorMsg != null && (errorMsg.contains("invalid type code") 
                                    || errorMsg.contains("invalid handle") 
                                    || errorMsg.contains("StreamCorruptedException")
                                    || errorMsg.contains("Stream corrupted")
                                    || errorMsg.contains("Connection closed (EOF)"))) {
                                System.out.println("[AGENT] Stream corrupted - closing connection to auction house " + auctionHouseId);
                                // Close the corrupted connection
                                // Use the local connection reference to avoid race condition
                                NetworkClient conn = auctionHouseConnections.get(auctionHouseId);
                                if (conn != null && conn == localConnection) {
                                    try {
                                        conn.close();
                                    } catch (IOException closeEx) {
                                        // Ignore close errors
                                    }
                                    auctionHouseConnections.remove(auctionHouseId);
                                }
                                break; // Fatal error - exit listener
                            }
                            
                            // For other IOExceptions (like "Socket closed", "Connection reset"), 
                            // check if connection is still valid
                            // Re-check connection from map to ensure it hasn't been replaced
                            NetworkClient currentConnection = auctionHouseConnections.get(auctionHouseId);
                            if (currentConnection != localConnection || !localConnection.isConnected()) {
                                System.out.println("[AGENT] Connection to auction house " + auctionHouseId + " is closed or replaced");
                                break; // Connection closed or replaced - exit listener
                            }
                            
                            // For transient errors, log and continue listening
                            System.out.println("[AGENT] Transient I/O error, continuing to listen...");
                            try {
                                Thread.sleep(100); // Brief pause before retrying
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                            continue; // Continue listening instead of breaking
                        } catch (ClassNotFoundException e) {
                            System.out.println("[AGENT] Invalid message from auction house "
                                    + auctionHouseId + ": " + e.getMessage());
                            // Don't break on ClassNotFoundException - might be a one-off issue
                            // Continue listening for other messages
                            continue;
                        }
                    }

                    System.out.println("[AGENT] Listener stopped for auction house " + auctionHouseId);
                    return; // Exit successfully

                } catch (Exception e) {
                    retries++;
                    System.out.println("[AGENT] Listener error (attempt " + retries + "/3): "
                            + e.getMessage());
                    if (retries < 3) {
                        try {
                            Thread.sleep(2000); // Wait 2 seconds before retry
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                }
            }
            System.out.println("[AGENT] Listener failed after " + retries + " attempts");
        });

        listenerThread.setDaemon(true);
        listenerThread.setName("AH-" + auctionHouseId + "-Listener");
        listenerThreads.put(auctionHouseId, listenerThread);
        listenerThread.start();

        // Give the listener thread a moment to start reading messages
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }


    /**
     * Processes a winning bid by transferring funds and confirming the purchase
     * with the auction house.
     * <p>
     * This method performs the following steps:
     * <ol>
     *   <li>Requests a fund transfer from the bank (blocked funds → auction house)</li>
     *   <li>Sends a winner confirmation request to the auction house</li>
     *   <li>Waits for confirmation response (10-second timeout)</li>
     *   <li>Records the purchase in the purchase history</li>
     *   <li>Updates the local balance from the bank</li>
     * </ol>
     * <p>
     * This method handles errors gracefully and logs detailed status information.
     *
     * @param auctionHouseId the ID of the auction house
     * @param itemId the won item's ID
     * @param finalPrice the winning bid amount
     * @param auctionHouseAccountNumber the auction house's bank account number
     * @param itemDescription the item description for the purchase record
     */
    private void confirmWinner(int auctionHouseId, int itemId,
                               double finalPrice,
                               int auctionHouseAccountNumber,
                               String itemDescription) {
        System.out.println("[AGENT] ========================================");
        System.out.println("[AGENT] Processing WINNER confirmation for Item " + itemId);
        System.out.println("[AGENT] Final Price: $" + finalPrice);
        System.out.println("[AGENT] ========================================");

        try {
            // Step 1: Transfer the money first. This is the critical part - once we transfer,
            // we're committed to the purchase even if something goes wrong later.
            System.out.println("[AGENT] Step 1: Transferring $" + finalPrice + " to auction house...");
            BankMessages.TransferFundsRequest transferRequest =
                    new BankMessages.TransferFundsRequest(
                            accountNumber,
                            auctionHouseAccountNumber,
                            finalPrice);

            // Synchronize on bankClient to prevent concurrent bank operations
            synchronized (bankClient) {
                bankClient.sendMessage(transferRequest);
                BankMessages.TransferFundsResponse transferResponse =
                        (BankMessages.TransferFundsResponse) bankClient.receiveMessage();

                if (!transferResponse.success) {
                    System.out.println("[AGENT] ERROR: Failed to transfer funds for item "
                            + itemId + ": " + transferResponse.message);
                    return;
                }

                System.out.println("[AGENT] ✓ Transfer successful!");
            }

            // Step 2: Now notify the auction house that payment is complete.
            // If this fails, we still record the purchase since money was already transferred.
            NetworkClient connection = auctionHouseConnections.get(auctionHouseId);
            if (connection == null) {
                System.out.println("[AGENT] ERROR: No connection to confirm winner");
                // Funds were already transferred, so record purchase anyway
                System.out.println("[AGENT] WARNING: Funds transferred but no connection to confirm. "
                        + "Recording purchase to maintain history.");
                Purchase purchase = new Purchase(
                        auctionHouseId, itemId, itemDescription, finalPrice);
                purchases.add(purchase);
                if (uiCallback != null) {
                    uiCallback.onPurchasesUpdated(getPurchases());
                }
                updateBalance();
                return;
            }

            if (!connection.isConnected()) {
                System.out.println("[AGENT] ERROR: Connection closed, cannot confirm winner");
                // Funds were already transferred, so record purchase anyway
                System.out.println("[AGENT] WARNING: Funds transferred but connection closed. "
                        + "Recording purchase to maintain history.");
                Purchase purchase = new Purchase(
                        auctionHouseId, itemId, itemDescription, finalPrice);
                purchases.add(purchase);
                if (uiCallback != null) {
                    uiCallback.onPurchasesUpdated(getPurchases());
                }
                updateBalance();
                return;
            }

            BlockingQueue<Message> queue = responseQueues.get(auctionHouseId);
            if (queue == null) {
                System.out.println("[AGENT] ERROR: No response queue for auction house " + auctionHouseId);
                // Create queue if missing
                queue = responseQueues.computeIfAbsent(auctionHouseId, id -> new LinkedBlockingQueue<>());
            }

            // Make sure the listener thread is alive - we need it to receive the confirmation response.
            // If it died (maybe connection dropped), restart it.
            Thread listener = listenerThreads.get(auctionHouseId);
            if (listener == null || !listener.isAlive()) {
                System.out.println("[AGENT] Listener thread not running for auction house " + auctionHouseId
                        + ", restarting...");
                startListeningForNotifications(auctionHouseId);
                // Give it a moment to start up and begin listening
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    // Continue anyway - worst case we timeout waiting for response
                }
            }

            System.out.println("[AGENT] Step 2: Confirming winner with auction house...");

            synchronized (connection) {
                AuctionMessages.ConfirmWinnerRequest request =
                        new AuctionMessages.ConfirmWinnerRequest(itemId, accountNumber);
                connection.sendMessage(request);

                // Wait for response with timeout
                Message msg;
                try {
                    msg = queue.poll(10, TimeUnit.SECONDS); // 10-second timeout

                    if (msg == null) {
                        System.out.println("[AGENT] ERROR: Timeout waiting for confirmWinner response");
                        // Funds were already transferred, so record purchase anyway
                        System.out.println("[AGENT] WARNING: Funds transferred but confirmation timed out. "
                                + "Recording purchase to maintain history.");
                        Purchase purchase = new Purchase(
                                auctionHouseId, itemId, itemDescription, finalPrice);
                        purchases.add(purchase);
                        if (uiCallback != null) {
                            uiCallback.onPurchasesUpdated(getPurchases());
                        }
                        updateBalance();
                        return;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("[AGENT] ERROR: Interrupted while waiting for confirmWinner response");
                    // Funds were already transferred, so record purchase anyway
                    System.out.println("[AGENT] WARNING: Funds transferred but confirmation interrupted. "
                            + "Recording purchase to maintain history.");
                    Purchase purchase = new Purchase(
                            auctionHouseId, itemId, itemDescription, finalPrice);
                    purchases.add(purchase);
                    if (uiCallback != null) {
                        uiCallback.onPurchasesUpdated(getPurchases());
                    }
                    updateBalance();
                    return;
                }

                if (!(msg instanceof AuctionMessages.ConfirmWinnerResponse)) {
                    System.out.println("[AGENT] ERROR: Unexpected confirmWinner response type: "
                            + msg.getClass().getSimpleName());
                    // Funds were already transferred, so record purchase anyway
                    System.out.println("[AGENT] WARNING: Funds transferred but got unexpected response. "
                            + "Recording purchase to maintain history.");
                    Purchase purchase = new Purchase(
                            auctionHouseId, itemId, itemDescription, finalPrice);
                    purchases.add(purchase);
                    if (uiCallback != null) {
                        uiCallback.onPurchasesUpdated(getPurchases());
                    }
                    updateBalance();
                    return;
                }

                AuctionMessages.ConfirmWinnerResponse response =
                        (AuctionMessages.ConfirmWinnerResponse) msg;

                if (response.success) {
                    System.out.println("[AGENT] ✓ Winner confirmed for item "
                            + itemId + " (" + itemDescription + ")");

                    // Record purchase
                    Purchase purchase = new Purchase(
                            auctionHouseId, itemId, itemDescription, finalPrice);
                    purchases.add(purchase);
                    System.out.println("[AGENT] Purchase recorded: " + purchase.description 
                            + " for $" + purchase.price);

                    // Notify UI of purchase update
                    if (uiCallback != null) {
                        List<Purchase> purchaseList = getPurchases();
                        System.out.println("[AGENT] Notifying UI of " + purchaseList.size() + " purchases");
                        uiCallback.onPurchasesUpdated(purchaseList);
                    } else {
                        System.out.println("[AGENT] WARNING: No UI callback registered");
                    }

                    // Update balance
                    updateBalance();

                    System.out.println("[AGENT] ✓ Purchase complete! Item added to My Purchases");
                } else {
                    System.out.println("[AGENT] ERROR: Auction house refused to confirm winner: "
                            + response.message);
                    // Even though confirmation failed, funds were already transferred
                    // Record the purchase anyway to maintain accurate purchase history
                    System.out.println("[AGENT] WARNING: Funds were transferred but confirmation failed. "
                            + "Recording purchase anyway.");
                    Purchase purchase = new Purchase(
                            auctionHouseId, itemId, itemDescription, finalPrice);
                    purchases.add(purchase);
                    if (uiCallback != null) {
                        uiCallback.onPurchasesUpdated(getPurchases());
                    }
                    updateBalance();
                }
            }
        } catch (IOException e) {
            System.err.println("[AGENT] ERROR confirming winner (IOException): " + e.getMessage());
            e.printStackTrace();
            // Funds were already transferred, so record purchase anyway to maintain history
            System.out.println("[AGENT] WARNING: Funds transferred but IOException during confirmation. "
                    + "Recording purchase to maintain history.");
            Purchase purchase = new Purchase(
                    auctionHouseId, itemId, itemDescription, finalPrice);
            purchases.add(purchase);
            if (uiCallback != null) {
                uiCallback.onPurchasesUpdated(getPurchases());
            }
            updateBalance();
        } catch (ClassNotFoundException e) {
            System.err.println("[AGENT] ERROR confirming winner (ClassNotFoundException): " + e.getMessage());
            e.printStackTrace();
            // Funds were already transferred, so record purchase anyway to maintain history
            System.out.println("[AGENT] WARNING: Funds transferred but ClassNotFoundException during confirmation. "
                    + "Recording purchase to maintain history.");
            Purchase purchase = new Purchase(
                    auctionHouseId, itemId, itemDescription, finalPrice);
            purchases.add(purchase);
            if (uiCallback != null) {
                uiCallback.onPurchasesUpdated(getPurchases());
            }
            updateBalance();
        } catch (Exception e) {
            System.err.println("[AGENT] ERROR confirming winner (Unexpected): " + e.getMessage());
            e.printStackTrace();
            // Funds were already transferred, so record purchase anyway to maintain history
            System.out.println("[AGENT] WARNING: Funds transferred but unexpected error during confirmation. "
                    + "Recording purchase to maintain history.");
            Purchase purchase = new Purchase(
                    auctionHouseId, itemId, itemDescription, finalPrice);
            purchases.add(purchase);
            if (uiCallback != null) {
                uiCallback.onPurchasesUpdated(getPurchases());
            }
            updateBalance();
        }
    }

    /**
     * Retrieves the current list of auction items from the specified auction house.
     * <p>
     * This method establishes a connection if needed, sends a request for items,
     * and waits up to 10 seconds for a response.
     *
     * @param auctionHouseId the unique ID of the auction house
     * @return array of AuctionItem objects representing available items
     * @throws IOException if network communication fails or timeout occurs
     * @throws ClassNotFoundException if message deserialization fails
     */
    public AuctionItem[] getItemsFromAuctionHouse(int auctionHouseId)
            throws IOException, ClassNotFoundException {
        NetworkClient connection = auctionHouseConnections.computeIfAbsent(
                auctionHouseId,
                id -> {
                    try {
                        AuctionHouseInfo info = auctionHouses.get(id);
                        if (info == null) {
                            throw new RuntimeException("Auction house not found");
                        }
                        NetworkClient c = new NetworkClient(info.host, info.port);
                        responseQueues.putIfAbsent(id, new LinkedBlockingQueue<>());
                        System.out.println("[AGENT] Connected to auction house " + id
                                + " at " + info.host + ":" + info.port);
                        return c;
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to connect: "
                                + e.getMessage(), e);
                    }
                });

        BlockingQueue<Message> queue =
                responseQueues.computeIfAbsent(auctionHouseId,
                        id -> new LinkedBlockingQueue<>());

        // Ensure listener thread is running - restart if dead
        Thread listener = listenerThreads.get(auctionHouseId);
        if (listener == null || !listener.isAlive()) {
            System.out.println("[AGENT] Listener thread not running for auction house " + auctionHouseId
                    + ", restarting...");
            startListeningForNotifications(auctionHouseId);
            // Wait a moment for listener to start
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while starting listener", e);
            }
            // Verify it started
            listener = listenerThreads.get(auctionHouseId);
            if (listener == null || !listener.isAlive()) {
                throw new IOException("Failed to start listener thread for auction house " + auctionHouseId);
            }
        }

        // Check connection is still valid
        if (!connection.isConnected()) {
            // Connection was closed - remove it and throw error
            auctionHouseConnections.remove(auctionHouseId);
            throw new IOException("Connection to auction house " + auctionHouseId + " is closed");
        }
        
        // Double-check the connection is still in the map (might have been removed due to corruption)
        if (auctionHouseConnections.get(auctionHouseId) != connection) {
            throw new IOException("Connection to auction house " + auctionHouseId 
                    + " was removed (possibly due to stream corruption). Please reconnect.");
        }

        synchronized (connection) {
            AuctionMessages.GetItemsRequest request =
                    new AuctionMessages.GetItemsRequest();
            System.out.println("[AGENT] Sending GetItemsRequest to auction house " + auctionHouseId);
            connection.sendMessage(request);

            Message msg;
            try {
                // Use poll with timeout instead of take
                System.out.println("[AGENT] Waiting for GetItemsResponse in queue...");
                msg = queue.poll(10, TimeUnit.SECONDS);

                if (msg == null) {
                    System.out.println("[AGENT] ERROR: Timeout waiting for GetItemsResponse. "
                            + "Listener thread alive: " + (listener != null && listener.isAlive())
                            + ", Connection connected: " + connection.isConnected()
                            + ", Queue size: " + queue.size());
                    throw new IOException("Timeout waiting for items response");
                }
                System.out.println("[AGENT] Received response: " + msg.getClass().getSimpleName());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for items", e);
            }

            if (!(msg instanceof AuctionMessages.GetItemsResponse)) {
                throw new IOException("Unexpected response type: "
                        + msg.getClass().getSimpleName());
            }

            AuctionMessages.GetItemsResponse response =
                    (AuctionMessages.GetItemsResponse) msg;
            return response.items;
        }
    }

    /**
     * Places a bid on the specified item at the given auction house.
     * <p>
     * This method:
     * <ol>
     *   <li>Sends a bid request to the auction house</li>
     *   <li>Waits for a synchronous response (not the async notification)</li>
     *   <li>Updates the local balance if the bid is accepted</li>
     *   <li>Returns true if bid was accepted, false otherwise</li>
     * </ol>
     * <p>
     * Note: Even if this method returns true, the agent may later be outbid.
     * Use the listener thread notifications to track ongoing bid status.
     *
     * @param auctionHouseId the unique ID of the auction house
     * @param itemId the item ID to bid on
     * @param bidAmount the bid amount (must meet minimum increment requirements)
     * @return true if the bid was accepted, false if rejected
     * @throws IOException if network communication fails
     * @throws ClassNotFoundException if message deserialization fails
     */
    public boolean placeBid(int auctionHouseId, int itemId, double bidAmount)
            throws IOException, ClassNotFoundException {
        NetworkClient connection = auctionHouseConnections.computeIfAbsent(
                auctionHouseId,
                id -> {
                    try {
                        AuctionHouseInfo info = auctionHouses.get(id);
                        if (info == null) {
                            throw new RuntimeException("Auction house not found");
                        }
                        NetworkClient c = new NetworkClient(info.host, info.port);
                        responseQueues.putIfAbsent(id, new LinkedBlockingQueue<>());
                        System.out.println("[AGENT] Connected to auction house " + id
                                + " at " + info.host + ":" + info.port);
                        return c;
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to connect: "
                                + e.getMessage(), e);
                    }
                });

        BlockingQueue<Message> queue =
                responseQueues.computeIfAbsent(auctionHouseId,
                        id -> new LinkedBlockingQueue<>());

        synchronized (connection) {
            AuctionMessages.PlaceBidRequest request =
                    new AuctionMessages.PlaceBidRequest(itemId, accountNumber, bidAmount);
            connection.sendMessage(request);

            Message msg;
            try {
                msg = queue.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for bid response", e);
            }

            if (!(msg instanceof AuctionMessages.PlaceBidResponse)) {
                throw new IOException("Unexpected response type: "
                        + msg.getClass().getSimpleName());
            }

            AuctionMessages.PlaceBidResponse response =
                    (AuctionMessages.PlaceBidResponse) msg;

            if (response.success && "ACCEPTED".equals(response.status)) {
                System.out.println("[AGENT] Bid placed: Item " + itemId
                        + " for $" + bidAmount);
                updateBalance();
                return true;
            } else {
                System.out.println("[AGENT] Bid rejected: " + response.message);
                return false;
            }
        }
    }

    /**
     * Queries the bank for the latest account balance information and updates
     * local balance fields.
     * <p>
     * This method synchronizes with the bank server to get authoritative
     * balance data (total, available, and blocked funds) and notifies the
     * UI callback if registered.
     * <p>
     * This method is thread-safe and can be called concurrently.
     */
    public void updateBalance() {
        try {
            BankMessages.GetAccountInfoRequest request =
                    new BankMessages.GetAccountInfoRequest(accountNumber);
            bankClient.sendMessage(request);
            BankMessages.GetAccountInfoResponse response =
                    (BankMessages.GetAccountInfoResponse) bankClient.receiveMessage();
            if (response.success) {
                synchronized (balanceLock) {
                    this.totalBalance = response.totalBalance;
                    this.availableFunds = response.availableFunds;
                    this.blockedFunds = response.blockedFunds;
                }
                System.out.println("[AGENT] Balance updated - Total: $"
                        + totalBalance + ", Available: $"
                        + availableFunds + ", Blocked: $"
                        + blockedFunds);
                if (uiCallback != null) {
                    uiCallback.onBalanceUpdated(
                            totalBalance, availableFunds, blockedFunds);
                }
            } else {
                System.out.println("[AGENT] Failed to update balance: "
                        + response.message);
            }
        } catch (IOException e) {
            System.out.println("[AGENT] Failed to update balance: " + e.getMessage());
        } catch (ClassNotFoundException e) {
            System.out.println("[AGENT] Invalid balance response: " + e.getMessage());
        }
    }

    /**
     * Cleanly disconnects the agent from the auction system.
     * <p>
     * This method performs shutdown in the following order:
     * <ol>
     *   <li>Interrupts all listener threads</li>
     *   <li>Closes all auction house connections</li>
     *   <li>Sends a deregistration request to the bank</li>
     *   <li>Closes the bank connection</li>
     * </ol>
     * <p>
     * After calling this method, the Agent object should not be reused.
     */
    public void disconnect() {
        System.out.println("[AGENT] Disconnecting...");

        // Interrupt all listener threads
        for (Thread thread : listenerThreads.values()) {
            if (thread != null && thread.isAlive()) {
                thread.interrupt();
            }
        }
        listenerThreads.clear();

        // Close auction house connections
        for (NetworkClient connection : auctionHouseConnections.values()) {
            try {
                if (connection != null) {
                    connection.close();
                }
            } catch (IOException e) {
                System.out.println("[AGENT] Error closing connection: "
                        + e.getMessage());
            }
        }
        auctionHouseConnections.clear();
        responseQueues.clear();

        // Deregister with bank
        if (bankClient != null) {
            try {
                BankMessages.DeregisterRequest request =
                        new BankMessages.DeregisterRequest(accountNumber, "AGENT");
                bankClient.sendMessage(request);
                bankClient.receiveMessage();
                bankClient.close();
                System.out.println("[AGENT] Disconnected from system");
            } catch (IOException | ClassNotFoundException e) {
                System.out.println("[AGENT] Error during deregistration: "
                        + e.getMessage());
            }
        }
    }

    /**
     * Refreshes the local list of auction houses by querying the bank server.
     * <p>
     * This method should be called periodically or when the user requests
     * an updated list of available auction houses. New auction houses that
     * register after the agent starts will not be visible until this method
     * is called.
     * <p>
     * Existing connections to auction houses are not affected by this operation.
     */
    public void refreshAuctionHouses() {
        try {
            BankMessages.GetAuctionHousesRequest request =
                    new BankMessages.GetAuctionHousesRequest();
            bankClient.sendMessage(request);
            BankMessages.GetAuctionHousesResponse response =
                    (BankMessages.GetAuctionHousesResponse) bankClient.receiveMessage();
            if (response.success) {
                auctionHouses.clear();
                for (AuctionHouseInfo info : response.auctionHouses) {
                    auctionHouses.put(info.auctionHouseId, info);
                }
                System.out.println("[AGENT] Refreshed auction houses: "
                        + auctionHouses.size());
            } else {
                System.out.println("[AGENT] Failed to refresh auction houses: "
                        + response.message);
            }
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("[AGENT] Failed to refresh auction houses: "
                    + e.getMessage());
        }
    }
    public void closeAuctionHouseConnection(int auctionHouseId) {
        // Stop the listener thread first
        Thread listener = listenerThreads.remove(auctionHouseId);
        if (listener != null && listener.isAlive()) {
            listener.interrupt();
            // Wait for the listener thread to stop (with timeout)
            try {
                listener.join(2000); // Wait up to 2 seconds
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Close the connection
        NetworkClient connection = auctionHouseConnections.remove(auctionHouseId);
        if (connection != null) {
            try {
                connection.close();
            } catch (IOException e) {
                // Ignore close errors
            }
        }

        // Clear response queue
        BlockingQueue<Message> queue = responseQueues.remove(auctionHouseId);
        if (queue != null) {
            queue.clear();
        }
    }
}
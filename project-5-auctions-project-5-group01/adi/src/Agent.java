import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Represents an agent (bidder) in the auction system
 * Synchronization, exception handling, thread management,
 * message routing, and agent-initiated transfers.
 */
public class Agent {

    private int accountNumber;
    private String agentName;

    private NetworkClient bankClient;

    // auctionHouseId -> NetworkClient to that house
    private Map<Integer, NetworkClient> auctionHouseConnections;
    // auctionHouseId -> AuctionHouseInfo
    private Map<Integer, AuctionHouseInfo> auctionHouses;
    // auctionHouseId -> listener thread
    private Map<Integer, Thread> listenerThreads;

    // auctionHouseId -> queue of non-notification responses
    private Map<Integer, BlockingQueue<Message>> responseQueues;

    private AgentUICallback uiCallback;

    private volatile double totalBalance;
    private volatile double availableFunds;
    private volatile double blockedFunds;
    private final Object balanceLock = new Object();

    // Purchases list for "My Purchases" view
    public static class Purchase {
        public final int auctionHouseId;
        public final int itemId;
        public final String description;
        public final double price;

        public Purchase(int auctionHouseId, int itemId,
                        String description, double price) {
            this.auctionHouseId = auctionHouseId;
            this.itemId = itemId;
            this.description = description;
            this.price = price;
        }
    }

    private final List<Purchase> purchases =
            Collections.synchronizedList(new ArrayList<>());

    public interface AgentUICallback {
        void onBalanceUpdated(double total, double available, double blocked);
        void onItemsUpdated(AuctionItem[] items);
        void onBidStatusChanged(int itemId, String status, String message);
        default void onPurchasesUpdated(List<Purchase> purchases) { }
    }

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

    public void setUICallback(AgentUICallback callback) {
        this.uiCallback = callback;
    }

    public int getAccountNumber() {
        return accountNumber;
    }

    public String getAgentName() {
        return agentName;
    }

    public double getTotalBalance() {
        synchronized (balanceLock) {
            return totalBalance;
        }
    }

    public double getAvailableFunds() {
        synchronized (balanceLock) {
            return availableFunds;
        }
    }

    public double getBlockedFunds() {
        synchronized (balanceLock) {
            return blockedFunds;
        }
    }

    public List<Purchase> getPurchases() {
        synchronized (purchases) {
            return new ArrayList<>(purchases);
        }
    }

    public AuctionHouseInfo[] getAuctionHouses() {
        return auctionHouses.values().toArray(new AuctionHouseInfo[0]);
    }

    // Ensure connection and response queue exist for this auction house
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

    // Start one listener thread per auction house to handle all incoming messages
    public void startListeningForNotifications(int auctionHouseId) {
        // Stop and wait for existing listener to terminate
        Thread existingThread = listenerThreads.get(auctionHouseId);
        if (existingThread != null && existingThread.isAlive()) {
            System.out.println("[AGENT] Listener already running for auction house "
                    + auctionHouseId);
            return; // Don't restart
        }

        // Remove any dead thread from map
        listenerThreads.remove(auctionHouseId);

        Thread listenerThread = new Thread(() -> {
            int retries = 0;
            while (retries < 3 && !Thread.currentThread().isInterrupted()) {
                try {
                    NetworkClient connection = auctionHouseConnections.get(auctionHouseId);
                    if (connection == null) {
                        System.out.println("[AGENT] No connection to auction house "
                                + auctionHouseId);
                        return;
                    }

                    System.out.println("[AGENT] Started listening for notifications from auction house "
                            + auctionHouseId);

                    while (connection.isConnected()
                            && !Thread.currentThread().isInterrupted()) {
                        try {
                            Message message = connection.receiveMessage();

                            if (message instanceof AuctionMessages.BidStatusNotification) {
                                AuctionMessages.BidStatusNotification n =
                                        (AuctionMessages.BidStatusNotification) message;
                                System.out.println("[AGENT] Notification for item "
                                        + n.itemId + ": " + n.status + " - " + n.message);

                                if (uiCallback != null) {
                                    uiCallback.onBidStatusChanged(
                                            n.itemId, n.status, n.message);
                                }

                                // For the winner: handle payment and then update balance inside confirmWinner()
                                if ("WINNER".equals(n.status)) {
                                    confirmWinner(auctionHouseId, n.itemId, n.finalPrice,
                                            n.auctionHouseAccountNumber, n.itemDescription);
                                } else {
                                    // For OUTBID / ITEM_SOLD / REJECTED etc:
                                    // Bank may have unblocked or changed funds, so refresh our view.
                                    updateBalance();
                                }
                            }

                            else {
                                BlockingQueue<Message> queue =
                                        responseQueues.get(auctionHouseId);
                                if (queue != null) {
                                    try {
                                        queue.put(message);
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                        return;
                                    }
                                } else {
                                    System.out.println("[AGENT] WARNING: Dropping message type "
                                            + message.getMessageType()
                                            + " - no response queue for AH " + auctionHouseId);
                                }
                            }
                        } catch (IOException e) {
                            System.out.println("[AGENT] Connection lost to auction house "
                                    + auctionHouseId);
                            break;
                        } catch (ClassNotFoundException e) {
                            System.out.println("[AGENT] Invalid message received from auction house "
                                    + auctionHouseId + ": " + e.getMessage());
                        }
                    }

                    return;

                } catch (Exception e) {
                    retries++;
                    System.out.println("[AGENT] Error listening for notifications (attempt "
                            + retries + "/3): " + e.getMessage());
                    if (retries >= 3) {
                        System.out.println("[AGENT] Failed to maintain connection after 3 attempts");
                        return;
                    }
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        });

        listenerThread.setDaemon(true);  //  Use daemon threads for cleanup
        listenerThread.setName("Listener-AH-" + auctionHouseId);
        listenerThreads.put(auctionHouseId, listenerThread);
        listenerThread.start();

        // Give the thread a moment to start before returning
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Agent-initiated transfer + winner confirmation.
     * IMPROVED: Better error handling and timeout management
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
            // 1) Ask BANK to transfer blocked funds
            System.out.println("[AGENT] Step 1: Transferring $" + finalPrice + " to auction house...");
            BankMessages.TransferFundsRequest transferRequest =
                    new BankMessages.TransferFundsRequest(
                            accountNumber,
                            auctionHouseAccountNumber,
                            finalPrice);

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

            // 2) Notify auction house that transfer is done
            NetworkClient connection = auctionHouseConnections.get(auctionHouseId);
            if (connection == null) {
                System.out.println("[AGENT] ERROR: No connection to confirm winner");
                return;
            }

            if (!connection.isConnected()) {
                System.out.println("[AGENT] ERROR: Connection closed, cannot confirm winner");
                return;
            }

            BlockingQueue<Message> queue = responseQueues.get(auctionHouseId);
            if (queue == null) {
                System.out.println("[AGENT] ERROR: No response queue for auction house " + auctionHouseId);
                return;
            }

            System.out.println("[AGENT] Step 2: Confirming winner with auction house...");

            synchronized (connection) {
                AuctionMessages.ConfirmWinnerRequest request =
                        new AuctionMessages.ConfirmWinnerRequest(itemId, accountNumber);
                connection.sendMessage(request);

                // Wait for response with timeout
                Message msg;
                try {
                    msg = queue.poll(10, TimeUnit.SECONDS); // ⭐ 10-second timeout

                    if (msg == null) {
                        System.out.println("[AGENT] ERROR: Timeout waiting for confirmWinner response");
                        return;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("[AGENT] ERROR: Interrupted while waiting for confirmWinner response");
                    return;
                }

                if (!(msg instanceof AuctionMessages.ConfirmWinnerResponse)) {
                    System.out.println("[AGENT] ERROR: Unexpected confirmWinner response type: "
                            + msg.getClass().getSimpleName());
                    return;
                }

                AuctionMessages.ConfirmWinnerResponse response =
                        (AuctionMessages.ConfirmWinnerResponse) msg;

                if (response.success) {
                    System.out.println("[AGENT] ✓ Winner confirmed for item "
                            + itemId + " (" + itemDescription + ")");

                    // Record purchase
                    purchases.add(new Purchase(
                            auctionHouseId, itemId, itemDescription, finalPrice));

                    if (uiCallback != null) {
                        uiCallback.onPurchasesUpdated(getPurchases());
                    }

                    // Update balance
                    updateBalance();

                    System.out.println("[AGENT] ✓ Purchase complete! Item added to My Purchases");
                } else {
                    System.out.println("[AGENT] ERROR: Auction house refused to confirm winner: "
                            + response.message);
                }
            }
        } catch (IOException e) {
            System.err.println("[AGENT] ERROR confirming winner (IOException): " + e.getMessage());
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            System.err.println("[AGENT] ERROR confirming winner (ClassNotFoundException): " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("[AGENT] ERROR confirming winner (Unexpected): " + e.getMessage());
            e.printStackTrace();
        }
    }

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

        synchronized (connection) {
            AuctionMessages.GetItemsRequest request =
                    new AuctionMessages.GetItemsRequest();
            connection.sendMessage(request);

            Message msg;
            try {
                // ⭐ Use poll with timeout instead of take
                msg = queue.poll(10, TimeUnit.SECONDS);

                if (msg == null) {
                    throw new IOException("Timeout waiting for items response");
                }
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

    // Place bid and read response via response queue
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

    // Cleanly disconnect: stop listeners, close connections, deregister from bank
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

    // Refresh auction houses dynamically
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
}

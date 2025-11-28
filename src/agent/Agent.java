package agent;

import bank.BankClient;
import common.AuctionHouseInfo;
import common.AuctionItem; // If using items
import common.Message;
import common.NetworkClient;
import messages.AuctionMessages;
import messages.BankMessages;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

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

    private final List<Purchase> purchases = Collections.synchronizedList(new ArrayList<>());

    public interface AgentUICallback {
        void onBalanceUpdated(double total, double available, double blocked);

        void onItemsUpdated(AuctionItem[] items);

        void onBidStatusChanged(int itemId, String status, String message);

        default void onPurchasesUpdated(List<Purchase> purchases) {
        }
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

    /**
     * Opens a connection and prepares a response queue for the given auction house.
     * Does nothing if already connected.
     *
     * @param auctionHouseId ID of the auction house to connect to
     * @throws IOException if unable to connect
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
     * Starts a dedicated thread for listening for notifications on the given auction house connection.
     * Automatically restarts on connection loss or errors (with retry).
     * Only one thread runs per auction house.
     *
     * @param auctionHouseId the auction house to listen to
     */
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
                                if ("WINNER".equals(n.status)) {
                                    confirmWinner(auctionHouseId, n.itemId, n.finalPrice,
                                            n.auctionHouseAccountNumber, n.itemDescription);
                                }
                            } else {
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
     */
    private void confirmWinner(int auctionHouseId, int itemId, double finalPrice, int auctionHouseAccountNumber,
                               String itemDescription) {
        try {
            // 1) Ask BANK to transfer blocked funds
            BankMessages.TransferFundsRequest transferRequest =
                    new BankMessages.TransferFundsRequest(
                            accountNumber,
                            auctionHouseAccountNumber,
                            finalPrice);
            bankClient.sendMessage(transferRequest);
            BankMessages.TransferFundsResponse transferResponse =
                    (BankMessages.TransferFundsResponse) bankClient.receiveMessage();

            if (!transferResponse.success) {
                System.out.println("[AGENT] Failed to transfer funds for item "
                        + itemId + ": " + transferResponse.message);
                return;
            }

            // 2) Notify auction house that transfer is done
            NetworkClient connection = auctionHouseConnections.get(auctionHouseId);
            if (connection == null) {
                System.out.println("[AGENT] No connection to confirm winner");
                return;
            }

            if (!connection.isConnected()) {  //  Additional check
                System.out.println("[AGENT] Connection closed, cannot confirm winner");
                return;
            }

            BlockingQueue<Message> queue = responseQueues.get(auctionHouseId);
            if (queue == null) {  // Check before using
                System.out.println("[AGENT] No response queue for auction house " + auctionHouseId);
                return;
            }

            System.out.println("[AGENT] Confirming winner for item "
                    + itemId + " after bank transfer...");

            synchronized (connection) {
                AuctionMessages.ConfirmWinnerRequest request =
                        new AuctionMessages.ConfirmWinnerRequest(
                                itemId, accountNumber);
                connection.sendMessage(request);

                Message msg;
                try {
                    msg = queue.take();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("[AGENT] Interrupted while waiting for confirmWinner response");
                    return;
                }

                if (!(msg instanceof AuctionMessages.ConfirmWinnerResponse)) {
                    System.out.println("[AGENT] Unexpected confirmWinner response type: "
                            + msg.getClass().getSimpleName());
                    return;
                }

                AuctionMessages.ConfirmWinnerResponse response =
                        (AuctionMessages.ConfirmWinnerResponse) msg;
                if (response.success) {
                    System.out.println("[AGENT] Winner confirmed for item "
                            + itemId + " (" + itemDescription + ")");
                    // Record purchase
                    purchases.add(new Purchase(
                            auctionHouseId, itemId, itemDescription, finalPrice));
                    if (uiCallback != null) {
                        uiCallback.onPurchasesUpdated(getPurchases());
                    }
                    updateBalance();
                } else {
                    System.out.println("[AGENT] Auction house refused to confirm winner: "
                            + response.message);
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("[AGENT] Error confirming winner: " + e.getMessage());
        }
    }

    /**
     * Returns the item list from an auction house.
     * Opens a connection and sends a GetItems request, waiting for the response.
     * @param auctionHouseId ID for the auction house to query
     * @return array of {@link AuctionItem} available at that house
     * @throws IOException if an I/O or connection failure occurs
     * @throws ClassNotFoundException if the server response is invalid
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

        synchronized (connection) {
            AuctionMessages.GetItemsRequest request =
                    new AuctionMessages.GetItemsRequest();
            connection.sendMessage(request);

            Message msg;
            try {
                msg = queue.take();
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
     * Places a bid on the specified item at the specified auction house.
     * Sends a PlaceBid request and waits for the response.
     * @param auctionHouseId which auction house to send the bid to
     * @param itemId         which item to bid on
     * @param bidAmount      how much to bid
     * @return true if bid is accepted, false if rejected
     * @throws IOException if communication fails
     * @throws ClassNotFoundException if server reply is invalid
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
     * Fetches updated account information from the bank and notifies the UI callback.
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
     * Disconnects cleanly from all auction houses and the bank.
     * Interrupts notification listeners, closes sockets, and notifies the bank of deregistration.
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
     * Refreshes the known set of auction houses by querying the bank.
     * Replaces the current auctionHouses cache with updated information.
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

    /**
     * Agent-initiated funds transfer and winner confirmation after winning an auction.
     * Not public API; invoked internally after a WINNER notification.
     * @param auctionHouseId Auction house ID
     * @param itemId         Item ID
     * @param finalPrice     Winning price
     * @param auctionHouseAccountNumber Auction house's bank account
     * @param itemDescription Item description for purchase tracking
     */
    private void confirmWinner(int auctionHouseId,
                               int itemId,
                               double finalPrice,
                               int auctionHouseAccountNumber,
                               String itemDescription) {
        try {
            // 1) Ask BANK to transfer blocked funds
            BankMessages.TransferFundsRequest transferRequest =
                    new BankMessages.TransferFundsRequest(
                            accountNumber,
                            auctionHouseAccountNumber,
                            finalPrice);
            bankClient.sendMessage(transferRequest);
            BankMessages.TransferFundsResponse transferResponse =
                    (BankMessages.TransferFundsResponse) bankClient.receiveMessage();

            if (!transferResponse.success) {
                System.out.println("[AGENT] Failed to transfer funds for item "
                        + itemId + ": " + transferResponse.message);
                return;
            }

            // 2) Notify auction house that transfer is done
            NetworkClient connection = auctionHouseConnections.get(auctionHouseId);
            if (connection == null) {
                System.out.println("[AGENT] No connection to confirm winner");
                return;
            }

            BlockingQueue<Message> queue =
                    responseQueues.computeIfAbsent(auctionHouseId,
                            id -> new LinkedBlockingQueue<>());

            System.out.println("[AGENT] Confirming winner for item "
                    + itemId + " after bank transfer...");

            synchronized (connection) {
                AuctionMessages.ConfirmWinnerRequest request =
                        new AuctionMessages.ConfirmWinnerRequest(
                                itemId, accountNumber);
                connection.sendMessage(request);

                Message msg;
                try {
                    msg = queue.take();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("[AGENT] Interrupted while waiting for confirmWinner response");
                    return;
                }

                if (!(msg instanceof AuctionMessages.ConfirmWinnerResponse)) {
                    System.out.println("[AGENT] Unexpected confirmWinner response type: "
                            + msg.getClass().getSimpleName());
                    return;
                }

                AuctionMessages.ConfirmWinnerResponse response =
                        (AuctionMessages.ConfirmWinnerResponse) msg;
                if (response.success) {
                    System.out.println("[AGENT] Winner confirmed for item "
                            + itemId + " (" + itemDescription + ")");
                    // Record purchase
                    purchases.add(new Purchase(
                            auctionHouseId, itemId, itemDescription, finalPrice));
                    if (uiCallback != null) {
                        uiCallback.onPurchasesUpdated(getPurchases());
                    }
                    updateBalance();
                } else {
                    System.out.println("[AGENT] Auction house refused to confirm winner: "
                            + response.message);
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("[AGENT] Error confirming winner: " + e.getMessage());
        }
    }
    /**
     * Sets the UI callback for this agent. Used to notify the controller/view layer about updates.
     * @param callback UI callback
     */
    public void setUICallback(AgentUICallback callback) {
        this.uiCallback = callback;
    }

    /** @return this agent's unique bank account number */
    public int getAccountNumber() { return accountNumber; }

    /** @return this agent's display name */
    public String getAgentName() { return agentName; }

    /** @return current total balance from the bank (including available and blocked) */
    public double getTotalBalance() {
        synchronized (balanceLock) {
            return totalBalance;
        }
    }

    /** @return current available balance (not blocked) */
    public double getAvailableFunds() {
        synchronized (balanceLock) {
            return availableFunds;
        }
    }

    /** @return total funds currently blocked in active bids */
    public double getBlockedFunds() {
        synchronized (balanceLock) {
            return blockedFunds;
        }
    }

    /**
     * Returns a copy of the purchases made so far by this agent.
     * @return list of past winning purchases
     */
    public List<Purchase> getPurchases() {
        synchronized (purchases) {
            return new ArrayList<>(purchases);
        }
    }

    /**
     * Gets the set of auction houses currently known/advertised by the bank.
     * @return array of {@link AuctionHouseInfo}
     */
    public AuctionHouseInfo[] getAuctionHouses() {
        return auctionHouses.values().toArray(new AuctionHouseInfo[0]);
    }
}


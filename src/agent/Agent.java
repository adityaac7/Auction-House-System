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

    private volatile double totalBalance;
    private volatile double availableFunds;
    private volatile double blockedFunds;
    private final Object balanceLock = new Object();


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
     * @param auctionHouseId the auction house to listen to
     */
    public void startListeningForNotifications(int auctionHouseId) {
        // Stop existing listener if present
        Thread existingThread = listenerThreads.get(auctionHouseId);
        if (existingThread != null && existingThread.isAlive()) {
            existingThread.interrupt();
        }

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
                                        + n.itemId + ": "
                                        + n.status + " - "
                                        + n.message);
                                if (uiCallback != null) {
                                    uiCallback.onBidStatusChanged(
                                            n.itemId, n.status, n.message);
                                }
                                // If we won, perform bank transfer then confirm
                                if ("WINNER".equals(n.status)) {
                                    confirmWinner(auctionHouseId,
                                            n.itemId,
                                            n.finalPrice,
                                            n.auctionHouseAccountNumber,
                                            n.itemDescription);
                                }
                            } else {
                                // Route message into response queue for RPC-style calls
                                BlockingQueue<Message> queue =
                                        responseQueues.get(auctionHouseId);
                                if (queue != null) {
                                    queue.offer(message);
                                } else {
                                    System.out.println(
                                            "[AGENT] Dropping message type "
                                                    + message.getMessageType()
                                                    + " - no response queue for AH "
                                                    + auctionHouseId);
                                }
                            }
                        } catch (IOException e) {
                            System.out.println("[AGENT] Connection lost to auction house "
                                    + auctionHouseId);
                            break;
                        } catch (ClassNotFoundException e) {
                            System.out.println("[AGENT] Invalid message received from auction house "
                                    + auctionHouseId + ": "
                                    + e.getMessage());
                        }
                    }

                    return; // exit loop if connection closed

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

        listenerThread.setDaemon(false);
        listenerThread.setName("Listener-AH-" + auctionHouseId);
        listenerThreads.put(auctionHouseId, listenerThread);
        listenerThread.start();
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

}
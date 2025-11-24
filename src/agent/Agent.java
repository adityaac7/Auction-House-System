package agent;

import bank.BankClient;
import common.AuctionHouseInfo;
import common.AuctionItem; // If using items
import common.Message;
import common.NetworkClient;

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
    }
}
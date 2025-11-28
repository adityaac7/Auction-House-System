package auctionhouse;

import common.AuctionItem; // If referenced directly
import common.NetworkServer; // If using the utility
// If using BankClient to connect:
import bank.BankClient;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import messages.AuctionMessages; // It is message between Agent and Auction House
import messages.BankMessages; // Between AuctionHouse and Bank

/**
 * The Auction House.
 * Acts as a Client to the Bank and a Server to Agents.
 * **
 * Talking to bank thru BankCLient at needed.
 * Accepting Agent connection and handling their requests.
 */
public class AuctionHouse {
        private final int auctionHouseId;
        private int auctionHouseAccountNumber;
        private final String bankHost;
        private final int bankPort;

        private final ItemManager itemManager; //manages items AND bidding
        private BankClient bankClient;      // connects to Bank
        private NetworkServer agentServer; //   It listen to agent and gives accepted nptes to us.


    //It starts the auction House and decides the HouseId, Bank_Host, Bank_Port

    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: java AuctionHouse [HouseId] [Bank_Host] [Bank_Port]");
            return;
        }

        int auctionHouseId = Integer.parseInt(args[0]);
        String bankHost = args[1];
        int bankPort = Integer.parseInt(args[2]);

        AuctionHouse house = new AuctionHouse(auctionHouseId, bankHost, bankPort);
        house.start();
    }

    /**
     * It stores configs:
     * @param auctionHouseId
     * @param bankHost
     * @param bankPort
     * Also Create ItemManger for the auction House.
     */

    public AuctionHouse(int auctionHouseId, String bankHost, int bankPort) {
        this.auctionHouseId = auctionHouseId;
        this.bankHost = bankHost;
        this.bankPort = bankPort;
        this.auctionHouseAccountNumber = -1;
        this.itemManager = new ItemManager(auctionHouseId); // todo

        System.out.println("[AUCTION HOUSE " + auctionHouseId + "] Created for bank "
                + bankHost + ":" + bankPort);
    }

    /**
     * IT connect to bank
     * register the Auction House with the bank
     * Start server for the Agents
     */


    private void start() {
        try {
            connectToBank();

            // 2. Register House with Bank
            // 3. Start Server for Agents
            startAgentServer();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Handle GetItems from Agent.
     * make into string message by forwarding it to ItemManger.
     */
    public AuctionMessages.GetItemsResponse handleGetItemsRequest() {
        Collection<AuctionItem> items = itemManager.getAllItems();
        AuctionItem[] array = items.toArray(new AuctionItem[0]);
        return new AuctionMessages.GetItemsResponse(true, array, "OK");// May add error states later if req.

    }

    // Handle a bid, form an agent
    // Asking Item Manager if bid is valid.
    // ItemManger then return a status.

    public AuctionMessages.PlaceBidResponse handlePlaceBidRequest(
            int itemId,
            int agentAccountNumber,
            double bidAmount) {

        // ItemManager shows status;
        // "ACCEPTED", "ITEM_NOT_FOUND", "BID_TOO_LOW",
        // "NOT_HIGH_ENOUGH", "AUCTION_CLOSED"
        String status = itemManager.placeBid(
                itemId,
                String.valueOf(agentAccountNumber),
                bidAmount
        );

        boolean success = "ACCEPTED".equals(status);
        String message;

        switch (status) {
            case "ITEM_NOT_FOUND":
                message = "Item not found";
                break;
            case "BID_TOO_LOW":
                message = "Bid below minimum";
                break;
            case "NOT_HIGH_ENOUGH":
                message = "Bid not higher than current";
                break;
            case "AUCTION_CLOSED":
                message = "Auction already closed";
                break;
            case "ACCEPTED":
                message = "Bid accepted";
                break;
            default:
                message = status;
                break;
        }

        return new AuctionMessages.PlaceBidResponse(
                success,
                status,
                message,
                bidAmount
        );
    }

    /**Confirming calling agent is the winner
     * if fixed no more bidding is happening here
     * Then, ask Bank to transfer fund from agento to AuctionHouse
    */
    public AuctionMessages.ConfirmWinnerResponse handleConfirmWinnerRequest(int itemId,
                                                                            int agentAccountNumber) {
        AuctionItem item = itemManager.getItem(itemId);
        if (item == null) {
            return new AuctionMessages.ConfirmWinnerResponse(false, "Item not found");
        }

        if (item.getCurrentBidderAccount() != agentAccountNumber) {
            return new AuctionMessages.ConfirmWinnerResponse(false, "You are not the highest bidder");
        }

        double finalPrice = item.getCurrentBid();

        // no more bids here be taken lock th auction
        itemManager.closeItem(itemId);

        // from agentAccountNumber to auctionHouseAccountNumber thru Ban, resulting in a finalPrice

        if (bankClient != null && auctionHouseAccountNumber > 0) {
            try {
                boolean ok = bankClient.transferFunds(
                        agentAccountNumber,
                        auctionHouseAccountNumber,
                        finalPrice
                );
                if (!ok) {
                    //IN this step if we see transfer dailed, we dont finally declare a winner.
                    return new AuctionMessages.ConfirmWinnerResponse(
                            false,
                            "Bank transfer failed; winner not finalized"
                    );
                }
            } catch (IOException | ClassNotFoundException e) {
                return new AuctionMessages.ConfirmWinnerResponse(
                        false,
                        "Error contacting Bank for transfer: " + e.getMessage()
                );
            }
        }

        return new AuctionMessages.ConfirmWinnerResponse(
                true,
                "Winner confirmed at price " + finalPrice
        );

    }
    /**
     * Connecting to bank server a client
     * Here BankClient Wrap a NetworkClient
     * */

private void connectToBank() {
        // TODO: Implement socket connection to BankServer
        System.out.println("Connecting to Bank...");
    try {
        // Create BankClient to the Bank server
        this.bankClient = new BankClient(bankHost, bankPort);

        // Open the TCP connection which uses NetworkClient inside it
        bankClient.connect();

        System.out.println("[AUCTION HOUSE " + auctionHouseId + "] Connected to Bank at "
                + bankHost + ":" + bankPort);
        } catch (IOException e) {
        System.err.println("[AUCTION HOUSE " + auctionHouseId
                + "] Failed to connect to Bank: " + e.getMessage());
        // When Bank is unreach, there is no meaning of starting the house
        throw new RuntimeException("Unable to connect to Bank", e);
        }
    }

    /**
     * Starting a Server listen for Agent Connections
     * First, STarting a NetworkServer on a 0 port
     * Then, Asking bank to register this auction House with the host and port
     * ACCEPT LOOP THAT spawns AuctionHouseClientHandler for each agent
     */

    private void startAgentServer() {
        System.out.println("Starting Agent Server...");
        try {
            //Creating a NetworkServer on an ephemeral port
            this.agentServer = new NetworkServer(0);
            this.agentServer.start();

            // Finding which hpst and port we are listening.
            String host;
            try {
                host = agentServer.getHost();
            } catch (UnknownHostException e) {
                //Fallback if host fails
                host = "127.0.0.1";
            }
            int port = agentServer.getPort();

            System.out.println("[AUCTION HOUSE " + auctionHouseId + "] Listening for Agents on "
                    + host + ":" + port);

            // Registering this Auction House with the Bank
            // Then, Agents can discover us from the Bank side.
            if (bankClient != null) {
                try {
                    BankMessages.RegisterAuctionHouseResponse resp =
                            bankClient.registerAuctionHouse(host, port);

                    if (!resp.success) {
                        throw new RuntimeException("Bank registration failed: " + resp.message);
                    }

                    this.auctionHouseAccountNumber = resp.accountNumber;

                    System.out.println("[AUCTION HOUSE " + auctionHouseId + "] Registered with Bank:");
                    System.out.println("    AuctionHouseId (Bank) = " + resp.auctionHouseId);
                    System.out.println("    AccountNumber         = " + auctionHouseAccountNumber);
                } catch (IOException | ClassNotFoundException e) {
                    throw new RuntimeException("Error registering Auction House with Bank", e);
                }
            } else {
                System.err.println("[AUCTION HOUSE " + auctionHouseId
                        + "] BankClient is null; cannot register with Bank.");
            }

            // Creating thread that continually accepts Agent connections
            Thread acceptThread = new Thread(() -> {
                System.out.println("[AUCTION HOUSE " + auctionHouseId + "] Accept thread started");
                while (agentServer.isRunning() && !agentServer.isClosed()) {
                    try {
                        //This block untl agnet connect
                        Socket clientSocket = agentServer.acceptConnection();
                        System.out.println("[AUCTION HOUSE " + auctionHouseId + "] Agent connected from "
                                + clientSocket.getInetAddress());
                        // fOR EACH AGENT SOCKET, it create a new handler thread.

                        //Implementing request handling AuctionHouseClientHandler.
                        AuctionHouseClientHandler handler =
                                new AuctionHouseClientHandler(clientSocket, this);
                        handler.start();
                    } catch (IOException e) {
                        if (agentServer.isRunning()) {
                            System.err.println("[AUCTION HOUSE " + auctionHouseId
                                    + "] Error accepting agent connection: " + e.getMessage());
                        }
                    }
                }
                System.out.println("[AUCTION HOUSE " + auctionHouseId + "] Accept thread terminating");
            });

            acceptThread.setDaemon(true);
            acceptThread.start();

        } catch (IOException e) {
            throw new RuntimeException("Failed to start Agent Server", e);
        }
    }

}
package bank;

import common.NetworkClient;
import messages.BankMessages;
import common.Message;
import common.AuctionHouseInfo;

import java.io.IOException;

/**
 * Helper class to handle raw socket communication with the Bank.
 * Used by both AuctionHouse (to register/block funds) and Agent (to register/transfer).
 */
public class BankClient {
    private NetworkClient net;
    private final String host;
    private final int port;

    public BankClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * Connects to the Bank server.
     */
    public void connect() throws IOException {
        this.net = new NetworkClient(host, port);
    }

    /**
     * Sends a request to register an Agent.
     */
    public BankMessages.RegisterAgentResponse registerAgent(String name, double balance) throws IOException, ClassNotFoundException {
        net.sendMessage(new BankMessages.RegisterAgentRequest(name, balance));
        return (BankMessages.RegisterAgentResponse) net.receiveMessage();
    }

    /**
     * Sends a request to register an Auction House.
     */
    public BankMessages.RegisterAuctionHouseResponse registerAuctionHouse(String host, int port) throws IOException, ClassNotFoundException {
        net.sendMessage(new BankMessages.RegisterAuctionHouseRequest(host, port));
        return (BankMessages.RegisterAuctionHouseResponse) net.receiveMessage();
    }

    /**
     * Helper to send a generic request and get a response.
     * Useful for blocking funds, unblocking, etc.
     */
    public Message sendRequest(Message request) throws IOException, ClassNotFoundException {
        net.sendMessage(request);
        return net.receiveMessage();
    }

    public void disconnect() throws IOException {
        if (net != null) {
            net.close();
        }
    }

    public boolean isConnected() {
        return net != null && net.isConnected();
    }
}
package bank;

import common.NetworkClient;
import messages.BankMessages;
import common.Message;
import common.AuctionHouseInfo;

import java.io.IOException;

/**
 * A client-side helper class that abstracts the raw socket communication with the Bank.
 * This class is designed to be used by both the Agent and Auction House components
 * to perform financial operations and registration without handling object streams directly.
 */
public class BankClient {
    private NetworkClient net;
    private final String host;
    private final int port;

    /**
     * Creates a new BankClient instance targeted at a specific host and port.
     *
     * @param host The hostname of the Bank Server (e.g., "localhost").
     * @param port The port number the Bank Server is listening on.
     */
    public BankClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * Establishes a TCP connection to the Bank Server.
     * Must be called before performing any other operations.
     *
     * @throws IOException If the connection cannot be established.
     */
    public void connect() throws IOException {
        this.net = new NetworkClient(host, port);
    }

    /**
     * Registers a new Agent account with the Bank.
     *
     * @param name The display name of the agent.
     * @param balance The initial starting balance for the account.
     * @return The response containing the new Account ID and list of Auction Houses.
     * @throws IOException If a network error occurs.
     * @throws ClassNotFoundException If the server response cannot be deserialized.
     */
    public BankMessages.RegisterAgentResponse registerAgent(String name, double balance) throws IOException, ClassNotFoundException {
        net.sendMessage(new BankMessages.RegisterAgentRequest(name, balance));
        return (BankMessages.RegisterAgentResponse) net.receiveMessage();
    }

    /**
     * Registers a new Auction House account with the Bank.
     *
     * @param host The hostname where this Auction House is listening for Agents.
     * @param port The port where this Auction House is listening for Agents.
     * @return The response containing the assigned Auction House ID.
     * @throws IOException If a network error occurs.
     * @throws ClassNotFoundException If the server response cannot be deserialized.
     */
    public BankMessages.RegisterAuctionHouseResponse registerAuctionHouse(String host, int port) throws IOException, ClassNotFoundException {
        net.sendMessage(new BankMessages.RegisterAuctionHouseRequest(host, port));
        return (BankMessages.RegisterAuctionHouseResponse) net.receiveMessage();
    }

    /**
     * Requests the Bank to block funds in a specific account.
     * Used by Auction Houses to secure a bid.
     *
     * @param accountNumber The account ID to block funds from.
     * @param amount The amount to block.
     * @return true if funds were successfully blocked; false otherwise.
     */
    public boolean blockFunds(int accountNumber, double amount) throws IOException, ClassNotFoundException {
        net.sendMessage(new BankMessages.BlockFundsRequest(accountNumber, amount));
        BankMessages.BlockFundsResponse response = (BankMessages.BlockFundsResponse) net.receiveMessage();
        return response.success;
    }

    /**
     * Requests the Bank to unblock previously blocked funds.
     * Used when an Agent is outbid.
     *
     * @param accountNumber The account ID to unblock funds for.
     * @param amount The amount to release back to available balance.
     * @return true if the operation was processed successfully.
     */
    public boolean unblockFunds(int accountNumber, double amount) throws IOException, ClassNotFoundException {
        net.sendMessage(new BankMessages.UnblockFundsRequest(accountNumber, amount));
        BankMessages.UnblockFundsResponse response = (BankMessages.UnblockFundsResponse) net.receiveMessage();
        return response.success;
    }

    /**
     * Transfers funds from a sender's blocked balance to a receiver's total balance.
     * Used to finalize a winning bid.
     *
     * @param fromAccount The Agent account ID (sender).
     * @param toAccount The Auction House account ID (receiver).
     * @param amount The amount to transfer.
     * @return true if the transfer was successful.
     */
    public boolean transferFunds(int fromAccount, int toAccount, double amount) throws IOException, ClassNotFoundException {
        net.sendMessage(new BankMessages.TransferFundsRequest(fromAccount, toAccount, amount));
        BankMessages.TransferFundsResponse response = (BankMessages.TransferFundsResponse) net.receiveMessage();
        return response.success;
    }

    /**
     * Retrieves the current financial status of an account.
     *
     * @param accountNumber The account ID to query.
     * @return A response object containing total, available, and blocked funds.
     */
    public BankMessages.GetAccountInfoResponse getAccountInfo(int accountNumber) throws IOException, ClassNotFoundException {
        net.sendMessage(new BankMessages.GetAccountInfoRequest(accountNumber));
        return (BankMessages.GetAccountInfoResponse) net.receiveMessage();
    }

    /**
     * Requests the latest list of registered Auction Houses.
     *
     * @return An array of AuctionHouseInfo objects.
     */
    public AuctionHouseInfo[] getAuctionHouses() throws IOException, ClassNotFoundException {
        net.sendMessage(new BankMessages.GetAuctionHousesRequest());
        BankMessages.GetAuctionHousesResponse response = (BankMessages.GetAuctionHousesResponse) net.receiveMessage();
        return response.auctionHouses;
    }

    /**
     * Deregisters an account from the Bank, removing it from active lists.
     *
     * @param accountNumber The account ID to remove.
     * @param type The type of account ("AGENT" or "AUCTION_HOUSE").
     * @return true if deregistration was successful.
     */
    public boolean deregister(int accountNumber, String type) throws IOException, ClassNotFoundException {
        net.sendMessage(new BankMessages.DeregisterRequest(accountNumber, type));
        BankMessages.DeregisterResponse response = (BankMessages.DeregisterResponse) net.receiveMessage();
        return response.success;
    }

    /**
     * Closes the underlying network connection.
     *
     * @throws IOException If an error occurs during closure.
     */
    public void disconnect() throws IOException {
        if (net != null) {
            net.close();
        }
    }

    /**
     * Checks if the client is currently connected to the server.
     *
     * @return true if connected, false otherwise.
     */
    public boolean isConnected() {
        return net != null && net.isConnected();
    }
}
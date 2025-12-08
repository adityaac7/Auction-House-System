import java.io.*;
import java.net.Socket;

/**
 * Handles individual client connections to the bank
 * FIXED: Added support for GetAuctionHouses message
 */
public class BankClientHandler implements Runnable {
    private Bank bank;
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    public BankClientHandler(Bank bank, Socket socket) throws IOException {
        this.bank = bank;
        this.socket = socket;
        this.out = new ObjectOutputStream(socket.getOutputStream());
        this.out.flush();
        this.in = new ObjectInputStream(socket.getInputStream());
    }

    @Override
    public void run() {
        try {
            while (true) {
                Message message = (Message) in.readObject();
                Message response = handleMessage(message);

                synchronized (out) {
                    out.writeObject(response);
                    out.flush();
                }
            }
        } catch (EOFException e) {
            System.out.println("[BANK] Client disconnected");
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("[BANK] Error handling client: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }

    private Message handleMessage(Message message) {
        String messageType = message.getMessageType();

        switch (messageType) {
            case "REGISTER_AGENT":
                BankMessages.RegisterAgentRequest agentReq =
                        (BankMessages.RegisterAgentRequest) message;
                return bank.registerAgent(agentReq.agentName, agentReq.initialBalance);

            case "REGISTER_AUCTION_HOUSE":
                BankMessages.RegisterAuctionHouseRequest ahReq =
                        (BankMessages.RegisterAuctionHouseRequest) message;
                return bank.registerAuctionHouse(ahReq.host, ahReq.port);

            case "BLOCK_FUNDS":
                BankMessages.BlockFundsRequest blockReq =
                        (BankMessages.BlockFundsRequest) message;
                return bank.blockFunds(blockReq.accountNumber, blockReq.amount);

            case "UNBLOCK_FUNDS":
                BankMessages.UnblockFundsRequest unblockReq =
                        (BankMessages.UnblockFundsRequest) message;
                return bank.unblockFunds(unblockReq.accountNumber, unblockReq.amount);

            case "TRANSFER_FUNDS":
                BankMessages.TransferFundsRequest transferReq =
                        (BankMessages.TransferFundsRequest) message;
                return bank.transferFunds(transferReq.fromAccount, transferReq.toAccount,
                        transferReq.amount);

            case "GET_ACCOUNT_INFO":
                BankMessages.GetAccountInfoRequest infoReq =
                        (BankMessages.GetAccountInfoRequest) message;
                return bank.getAccountInfo(infoReq.accountNumber);

            case "DEREGISTER":
                BankMessages.DeregisterRequest deregReq =
                        (BankMessages.DeregisterRequest) message;
                return bank.deregister(deregReq.accountNumber, deregReq.accountType);

            case "GET_AUCTION_HOUSES":
                return bank.getAuctionHouses();

            default:
                System.out.println("[BANK] Unknown message type: " + messageType);
                return new BankMessages.DeregisterResponse(false, "Unknown message type");
        }
    }
}

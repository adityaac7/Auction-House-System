import java.io.*;
import java.net.Socket;

/**
 * Utility class for sending and receiving messages over network
 * FIXED: Improved synchronization to prevent deadlocks
 */
public class NetworkClient {
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private final Object readLock = new Object();
    private final Object writeLock = new Object();

    public NetworkClient(String host, int port) throws IOException {
        this.socket = new Socket(host, port);
        this.socket.setSoTimeout(30000); // 30-second timeout
        this.out = new ObjectOutputStream(socket.getOutputStream());
        this.out.flush();
        this.in = new ObjectInputStream(socket.getInputStream());
    }

    public NetworkClient(Socket socket, ObjectOutputStream out, ObjectInputStream in) {
        this.socket = socket;
        this.out = out;
        this.in = in;
    }

    public void sendMessage(Message message) throws IOException {
        synchronized (writeLock) {
            out.writeObject(message);
            out.flush();
        }
    }

    public Message receiveMessage() throws IOException, ClassNotFoundException {
        synchronized (readLock) {
            return (Message) in.readObject();
        }
    }

    public void close() throws IOException {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }
}

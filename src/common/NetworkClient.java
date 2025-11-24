package common;

import java.io.*;
import java.net.Socket;

/**
 * Utility class for sending and receiving messages over a network connection.
 * Uses an underlying TCP {@link Socket} and Java object streams to exchange serialized {@code Message} objects.
 * Thread-safe for concurrent send/receive, with internal synchronization to prevent deadlocks.
 */
public class NetworkClient {
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private final Object readLock = new Object();
    private final Object writeLock = new Object();

    /**
     * Establishes a client connection to the given host and port,
     * and initializes the object streams for bidirectional communication.
     *
     * @param host remote IP address or hostname
     * @param port remote TCP port to connect to
     * @throws IOException if the connection or the streams fail to open
     */
    public NetworkClient(String host, int port) throws IOException {
        this.socket = new Socket(host, port);
        this.socket.setSoTimeout(30000); // 30-second timeout
        this.out = new ObjectOutputStream(socket.getOutputStream());
        this.out.flush();
        this.in = new ObjectInputStream(socket.getInputStream());
    }
    /**
     * Constructs a {@code NetworkClient} from an existing connection and existing streams.
     * Useful for wrapping an accepted server-side socket with pre-initialized streams.
     * @param socket the underlying socket
     * @param out    the active {@link ObjectOutputStream} for writing
     * @param in     the active {@link ObjectInputStream} for reading
     */
    public NetworkClient(Socket socket, ObjectOutputStream out, ObjectInputStream in) {
        this.socket = socket;
        this.out = out;
        this.in = in;
    }
    /**
     * Serializes and sends a {@link Message} object to the remote server or client.
     * This method is thread-safe; callers can safely send from multiple threads.
     * @param message the {@code Message} to send; must be {@link Serializable}
     * @throws IOException if the underlying output stream fails
     */
    public void sendMessage(Message message) throws IOException {
        synchronized (writeLock) {
            out.writeObject(message);
            out.flush();
        }
    }
    /**
     * Reads and returns the next {@link Message} object from the remote peer.
     * <p>
     * This method blocks until a message arrives or the stream/socket closes.
     * Thread-safe; may be called concurrently with {@link #sendMessage(Message)}.
     *
     * @return the deserialized {@code Message} object received
     * @throws IOException if the stream is closed or a network error occurs
     * @throws ClassNotFoundException if the received object type is unknown
     */
    public Message receiveMessage() throws IOException, ClassNotFoundException {
        synchronized (readLock) {
            return (Message) in.readObject();
        }
    }
}
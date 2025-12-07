package common;

import java.io.*;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;

/**
 * Utility class for handling simple TCP client connections.
 * Wraps a {@link Socket} and provides methods for sending/receiving
 * serialized {@link Message} objects.
 */
public class NetworkClient {
    private Socket socket;
    private ObjectOutputStream outputStream;
    private ObjectInputStream inputStream;
    
    // Locks for thread-safe read/write operations
    private final Object readLock = new Object();
    private final Object writeLock = new Object();

    /**
     * Creates a new {@code NetworkClient} connected to the given host and port.
     * Configures socket with appropriate timeouts and keep-alive settings.
     *
     * @param host the hostname or IP address to connect to
     * @param port the port number to connect to
     * @throws IOException if the connection cannot be established
     */
    public NetworkClient(String host, int port) throws IOException {
        this.socket = new Socket();

        // Configure socket timeouts and options
        socket.setSoTimeout(30000);  // 30 second read timeout
        socket.setKeepAlive(true);   // Enable TCP keep-alive
        socket.setTcpNoDelay(true);  // Disable Nagle's algorithm for faster response

        // Connect with timeout
        socket.connect(new InetSocketAddress(host, port), 10000);  // 10 second connection timeout

        // Important: Create input stream first, then output stream. ObjectOutputStream
        // writes a header when created, and if the server is also waiting for us to
        // create our output stream first, we'll deadlock. Creating input first avoids this.
        this.inputStream = new ObjectInputStream(socket.getInputStream());
        this.outputStream = new ObjectOutputStream(socket.getOutputStream());
        this.outputStream.flush();
    }

    /**
     * Creates a new {@code NetworkClient} from an existing socket connection.
     * This is used when accepting incoming connections on the server side.
     *
     * @param socket the already-connected socket
     * @param outputStream the output stream for sending messages
     * @param inputStream the input stream for receiving messages
     * @throws IOException if an I/O error occurs during initialization
     */
    public NetworkClient(Socket socket, ObjectOutputStream outputStream,
                         ObjectInputStream inputStream) throws IOException {
        this.socket = socket;
        this.outputStream = outputStream;
        this.inputStream = inputStream;

        // Configure socket options for existing connection
        socket.setSoTimeout(30000);  // 30 second read timeout
        socket.setKeepAlive(true);   // Enable TCP keep-alive
        socket.setTcpNoDelay(true);  // Disable Nagle's algorithm
    }

    /**
     * Sends a message over the connection.
     *
     * @param message the message to send
     * @throws IOException if an I/O error occurs during transmission
     */
    public void sendMessage(Message message) throws IOException {
        synchronized (writeLock) {
            outputStream.writeObject(message);
            outputStream.flush();
            // Reset clears the object cache - without this, ObjectOutputStream keeps
            // references to all objects we've sent, causing memory leaks over time
            outputStream.reset();
        }
    }

    /**
     * Receives a message from the connection.
     * This call blocks until a message is available or timeout occurs.
     *
     * @return the received message
     * @throws IOException if an I/O error occurs or connection is closed
     * @throws ClassNotFoundException if the message class cannot be found
     */
    public Message receiveMessage() throws IOException, ClassNotFoundException {
        synchronized (readLock) {
            try {
                return (Message) inputStream.readObject();
            } catch (SocketTimeoutException e) {
                // Re-throw timeout as regular IOException with clear message
                throw new IOException("Read timeout - no response from server", e);
            } catch (java.io.StreamCorruptedException e) {
                // Stream corruption - connection is unusable
                throw new IOException("Stream corrupted - connection unusable: " + e.getMessage(), e);
            } catch (java.io.EOFException e) {
                // End of stream - connection closed
                throw new IOException("Connection closed (EOF)", e);
            } catch (IOException e) {
                // Check for stream corruption in the error message
                String msg = e.getMessage();
                if (msg != null && (msg.contains("invalid type code") || msg.contains("invalid handle"))) {
                    throw new IOException("Stream corrupted: " + msg, e);
                }
                throw e;
            }
        }
    }

    /**
     * Checks if the socket connection is still open.
     *
     * @return {@code true} if connected, {@code false} otherwise
     */
    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    /**
     * Closes the connection and releases associated resources.
     *
     * @throws IOException if an I/O error occurs during closure
     */
    public void close() throws IOException {
        if (inputStream != null) {
            try {
                inputStream.close();
            } catch (IOException e) {
                // Ignore close errors
            }
        }
        if (outputStream != null) {
            try {
                outputStream.close();
            } catch (IOException e) {
                // Ignore close errors
            }
        }
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }
}

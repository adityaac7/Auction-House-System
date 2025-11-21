import java.io.*;
import java.net.Socket;

/**
 * Utility class for sending and receiving messages over a network connection.
 * Uses an underlying TCP {@link Socket} and Java object streams to exchange serialized {@code Message} objects.
 * <p>
 * Thread-safe for concurrent send/receive, with internal synchronization to prevent deadlocks.
 */
public class NetworkClient {
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private final Object readLock = new Object();
    private final Object writeLock = new Object();
}
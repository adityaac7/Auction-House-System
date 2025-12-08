import java.io.Serializable;

/**
 * Base class for all network messages in the auction system.
 */
public abstract class Message implements Serializable {
    private static final long serialVersionUID = 1L;
    private String messageType;

    public Message(String messageType) {
        this.messageType = messageType;
    }

    public String getMessageType() {
        return messageType;
    }
}

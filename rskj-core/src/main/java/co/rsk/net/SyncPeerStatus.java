package co.rsk.net;

/**
 * Created by ajlopez on 29/08/2017.
 */
public class SyncPeerStatus {
    MessageSender sender;
    Status status;

    public SyncPeerStatus(MessageSender sender, Status status) {
        this.sender = sender;
        this.status = status;
    }

    public MessageSender getSender() { return this.sender; }

    public Status getStatus() { return this.status; }
}

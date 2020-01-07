package co.rsk.net;

import co.rsk.net.messages.Message;

public class MessageTask {
    private Peer sender;
    private Message message;

    public MessageTask(Peer sender, Message message) {
        this.sender = sender;
        this.message = message;
    }

    public Peer getSender() {
        return this.sender;
    }

    public Message getMessage() {
        return this.message;
    }

    @Override
    public String toString() {
        return "MessageTask{" +
                "sender=" + sender +
                ", message=" + message +
                '}';
    }
}

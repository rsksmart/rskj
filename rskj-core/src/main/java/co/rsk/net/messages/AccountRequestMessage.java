package co.rsk.net.messages;

import org.ethereum.util.RLP;

public class AccountRequestMessage extends MessageWithId {
    private byte[] blockHash;
    private byte[] address;
    private long id;

    public AccountRequestMessage(long id, byte[] blockHash, byte[] address) {
        this.blockHash = blockHash.clone();
        this.address = address.clone();
        this.id = id;
    }

    public byte[] getBlockHash() {
        return blockHash.clone();
    }

    public byte[] getAddress() {
        return address.clone();
    }

    @Override
    public MessageType getMessageType() { return MessageType.ACCOUNT_REQUEST_MESSAGE; }

    @Override
    public MessageType getResponseMessageType() { return MessageType.ACCOUNT_RESPONSE_MESSAGE; }

    @Override
    public long getId() {
        return this.id;
    }

    @Override
    public byte[] getEncodedMessageWithoutId() {
        byte[] rlpBlockHash = RLP.encodeElement(this.blockHash);
        byte[] rlpAddress = RLP.encodeElement(this.address);
        return RLP.encodeList(rlpBlockHash, rlpAddress);
    }

    @Override
    public void accept(MessageVisitor v) { v.apply(this); }
}
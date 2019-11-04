package co.rsk.net.messages;

import org.ethereum.util.RLP;

public class AccountRequestMessage extends MessageWithId {
    private byte[] blockHash;
    private byte[] addressHash;
    private long id;

    public AccountRequestMessage(long id, byte[] blockHash, byte[] addressHash) {
        this.blockHash = blockHash.clone();
        this.addressHash = addressHash.clone();
        this.id = id;
    }

    public byte[] getBlockHash() {
        return blockHash.clone();
    }

    public byte[] getAddressHash() {
        return addressHash.clone();
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
        byte[] rlpAddressHash = RLP.encodeElement(this.addressHash);
        return RLP.encodeList(rlpBlockHash, rlpAddressHash);
    }

    @Override
    public void accept(MessageVisitor v) { v.apply(this); }
}
package co.rsk.net.messages;

import org.ethereum.util.RLP;

public class AccountResponseMessage extends MessageWithId {
    private long id;
    private final byte[] merkleProof;
    private final byte[] nonce;
    private final byte[] balance;
    private final byte[] codeHash;
    private final byte[] storageRoot;

    public AccountResponseMessage(long id, byte[] merkleProof, byte[] nonce, byte[] balance, byte[] codeHash, byte[] storageRoot) {
        this.id = id;
        this.merkleProof = merkleProof;
        this.nonce = nonce;
        this.balance = balance;
        this.codeHash = codeHash;
        this.storageRoot = storageRoot;
    }

    @Override
    public long getId() {
        return id;
    }

    public byte[] getMerkleProof() {
        return merkleProof;
    }

    public byte[] getNonce() {
        return nonce;
    }

    public byte[] getBalance() {
        return balance;
    }

    public byte[] getCodeHash() {
        return codeHash;
    }

    public byte[] getStorageRoot() {
        return storageRoot;
    }

    @Override
    protected byte[] getEncodedMessageWithoutId() {
        byte[] rlpMerkleProof = RLP.encodeElement(this.merkleProof);
        byte[] rlpNonce = RLP.encodeElement(this.nonce);
        byte[] rlpBalance = RLP.encodeElement(this.balance);
        byte[] rlpCodeHash = RLP.encodeElement(this.codeHash);
        byte[] rlpStorageRoot = RLP.encodeElement(this.storageRoot);
        return RLP.encodeList(rlpMerkleProof, rlpNonce, rlpBalance, rlpCodeHash, rlpStorageRoot);
    }

    @Override
    public MessageType getMessageType() { return MessageType.ACCOUNT_RESPONSE_MESSAGE; }

    @Override
    public void accept(MessageVisitor v) { v.apply(this); }
}

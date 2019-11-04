package co.rsk.net.messages;

public class AccountResponseMessage extends MessageWithId {
    private long id;
    private final byte[] merkleProof;
    private final long nonce;
    private final long balance;
    private final byte[] codeHash;
    private final byte[] storageRoot;

    public AccountResponseMessage(long id, byte[] merkleProof, long nonce, long balance, byte[] codeHash, byte[] storageRoot) {
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

    public long getNonce() {
        return nonce;
    }

    public long getBalance() {
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
        return new byte[0];
    }

    @Override
    public MessageType getMessageType() { return MessageType.ACCOUNT_RESPONSE_MESSAGE; }

    @Override
    public void accept(MessageVisitor v) { v.apply(this); }
}

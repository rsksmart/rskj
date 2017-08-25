package co.rsk.net.messages;

import org.ethereum.util.RLP;

import java.math.BigInteger;

/**
 * Created by ajlopez on 23/08/2017.
 */
public class BlockHeadersRequestMessage extends Message {
    private long id;
    private byte[] hash;
    private int count;

    public BlockHeadersRequestMessage(long id, byte[] hash, int count) {
        this.id = id;
        this.hash = hash;
        this.count = count;
    }

    public long getId() { return this.id; }

    public byte[] getHash() { return this.hash; }

    public int getCount() { return this.count; }

    @Override
    public byte[] getEncodedMessage() {
        byte[] rlpId = RLP.encodeBigInteger(BigInteger.valueOf(this.id));
        byte[] rlpHash = RLP.encodeElement(this.hash);
        byte[] rlpCount = RLP.encodeInt(this.count);

        return RLP.encodeList(rlpId, rlpHash, rlpCount);
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.BLOCK_HEADERS_REQUEST_MESSAGE;
    }
}


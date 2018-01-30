package co.rsk.net.messages;

import co.rsk.core.commons.Keccak256;
import org.ethereum.util.RLP;

/**
 * Created by ajlopez on 23/08/2017.
 */
public class BlockHeadersRequestMessage extends MessageWithId {
    /**
     * Id to identify request/response correlation
     */
    private long id;

    /**
     * Hash of the first header to retrieve
     */
    private Keccak256 hash;

    /**
     * Count of headers to retrieve
     */
    private int count;

    public BlockHeadersRequestMessage(long id, Keccak256 hash, int count) {
        if (count < 0) {
            throw new IllegalArgumentException();
        }

        this.id = id;
        this.hash = hash;
        this.count = count;
    }

    public long getId() { return this.id; }

    public Keccak256 getHash() { return this.hash; }

    public int getCount() { return this.count; }

    @Override
    public byte[] getEncodedMessageWithoutId() {
        byte[] rlpHash = RLP.encodeElement(this.hash.getBytes());
        byte[] rlpCount = RLP.encodeInt(this.count);

        return RLP.encodeList(rlpHash, rlpCount);
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.BLOCK_HEADERS_REQUEST_MESSAGE;
    }

    @Override
    public MessageType getResponseMessageType() {
        return MessageType.BLOCK_HEADERS_RESPONSE_MESSAGE;
    }
}


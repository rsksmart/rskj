package co.rsk.net.messages;

import org.ethereum.core.BlockHeader;
import org.ethereum.util.RLP;

import java.math.BigInteger;
import java.util.List;

/**
 * Created by ajlopez on 24/08/2017.
 */
public class BlockHeadersByHashMessage extends Message {
    private long id;
    private List<BlockHeader> blockHeaders;

    public BlockHeadersByHashMessage(long id, List<BlockHeader> headers) {
        this.id = id;
        this.blockHeaders = headers;
    }

    public long getId() { return this.id; }

    public List<BlockHeader> getBlockHeaders() { return this.blockHeaders; }

    @Override
    public byte[] getEncodedMessage() {
        return null;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.BLOCK_HEADERS_BY_HASH_MESSAGE;
    }
}

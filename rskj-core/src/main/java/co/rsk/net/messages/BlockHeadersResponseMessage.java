package co.rsk.net.messages;

import org.ethereum.core.BlockHeader;
import org.ethereum.util.RLP;

import java.util.List;

/**
 * Created by ajlopez on 24/08/2017.
 */
public class BlockHeadersResponseMessage extends MessageWithId {
    /**
     * Id to identify request/response correlation
     */
    private long id;

    /**
     * List of block headers from the peer
     */
    private List<BlockHeader> blockHeaders;

    public BlockHeadersResponseMessage(long id, List<BlockHeader> headers) {
        this.id = id;
        this.blockHeaders = headers;
    }

    @Override
    public long getId() { return this.id; }

    public List<BlockHeader> getBlockHeaders() { return this.blockHeaders; }

    @Override
    protected byte[] getEncodedMessageWithoutId() {
        byte[][] rlpHeaders = this.blockHeaders.stream()
                .map(BlockHeader::getEncodedForHeaderMessage)
                .toArray(byte[][]::new);

        return RLP.encodeList(RLP.encodeList(rlpHeaders));
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.BLOCK_HEADERS_RESPONSE_MESSAGE;
    }

    @Override
    public void accept(MessageVisitor v) {
        v.apply(this);
    }
}

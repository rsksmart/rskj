package co.rsk.net.messages;

import org.ethereum.core.BlockHeader;
import org.ethereum.util.RLP;

import java.math.BigInteger;
import java.util.List;

/**
 * Created by ajlopez on 24/08/2017.
 */
public class BlockHeadersResponseMessage extends Message {
    private long id;
    private List<BlockHeader> blockHeaders;

    public BlockHeadersResponseMessage(long id, List<BlockHeader> headers) {
        this.id = id;
        this.blockHeaders = headers;
    }

    public long getId() { return this.id; }

    public List<BlockHeader> getBlockHeaders() { return this.blockHeaders; }

    @Override
    public byte[] getEncodedMessage() {
        byte[] rlpId = RLP.encodeBigInteger(BigInteger.valueOf(this.id));
        byte[][] rlpHeaders = new byte[this.blockHeaders.size()][];

        for (int k = 0; k < rlpHeaders.length; k++)
            rlpHeaders[k] = this.blockHeaders.get(k).getEncoded();

        return RLP.encodeList(rlpId, RLP.encodeList(rlpHeaders));
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.BLOCK_HEADERS_RESPONSE_MESSAGE;
    }
}

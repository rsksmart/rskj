package co.rsk.net.messages;


import com.google.common.collect.Lists;
import org.bouncycastle.util.BigIntegers;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;

public class SnapBlocksResponseMessage extends Message {
    private final List<Block> blocks;

    public List<Block> getBlocks() {
        return this.blocks;
    }


    public SnapBlocksResponseMessage(List<Block> blocks) {
        this.blocks = blocks;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.SNAP_BLOCKS_RESPONSE_MESSAGE;
    }

    @Override
    public byte[] getEncodedMessage() {
        List<byte[]> rlpBlocks = this.blocks.stream().map(Block::getEncoded).map(RLP::encode).collect(Collectors.toList());
        return RLP.encodeList(rlpBlocks.toArray(new byte[][]{}));
    }

    public static Message decodeMessage(BlockFactory blockFactory, RLPList list) {
        List<Block> blocks = Lists.newArrayList();
        for (int i = 0; i < list.size(); i++) {
            blocks.add(blockFactory.decodeBlock(list.get(i).getRLPData()));
        }
        return new SnapBlocksResponseMessage(blocks);
    }

    @Override
    public void accept(MessageVisitor v) {
        v.apply(this);
    }
}

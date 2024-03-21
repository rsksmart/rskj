package co.rsk.net.messages;


import co.rsk.core.BlockDifficulty;
import com.google.common.collect.Lists;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;

public class SnapBlocksResponseMessage extends Message {
    private final List<Block> blocks;
    private final List<BlockDifficulty> difficulties;


    public SnapBlocksResponseMessage(List<Block> blocks, List<BlockDifficulty> difficulties) {
        this.blocks = blocks;
        this.difficulties = difficulties;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.SNAP_BLOCKS_RESPONSE_MESSAGE;
    }

    public List<BlockDifficulty> getDifficulties() {
        return difficulties;
    }

    public List<Block> getBlocks() {
        return this.blocks;
    }

    @Override
    public byte[] getEncodedMessage() {
        List<byte[]> rlpBlocks = this.blocks.stream().map(Block::getEncoded).map(RLP::encode).collect(Collectors.toList());
        List<byte[]> rlpDifficulties = this.difficulties.stream().map(BlockDifficulty::getBytes).map(RLP::encode).collect(Collectors.toList());
        return RLP.encodeList(RLP.encodeList(rlpBlocks.toArray(new byte[][]{})),
                RLP.encodeList(rlpDifficulties.toArray(new byte[][]{})));
    }

    public static Message decodeMessage(BlockFactory blockFactory, RLPList list) {
        List<Block> blocks = Lists.newArrayList();
        List<BlockDifficulty> blockDifficulties = Lists.newArrayList();
        RLPList blocksRLP = RLP.decodeList(list.get(0).getRLPData());
        for (int i = 0; i < blocksRLP.size(); i++) {
            blocks.add(blockFactory.decodeBlock(blocksRLP.get(i).getRLPData()));
        }
        RLPList difficultiesRLP = RLP.decodeList(list.get(1).getRLPData());
        for (int i = 0; i < difficultiesRLP.size(); i++) {
            blockDifficulties.add(new BlockDifficulty(new BigInteger(difficultiesRLP.get(i).getRLPData())));
        }
        return new SnapBlocksResponseMessage(blocks, blockDifficulties);
    }

    @Override
    public void accept(MessageVisitor v) {
        v.apply(this);
    }
}

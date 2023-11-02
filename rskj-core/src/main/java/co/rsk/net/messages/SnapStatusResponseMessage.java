package co.rsk.net.messages;


import co.rsk.core.BlockDifficulty;
import com.google.common.collect.Lists;
import org.bouncycastle.util.BigIntegers;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;

public class SnapStatusResponseMessage extends Message {
    private final List<Block> blocks;
    private final List<BlockDifficulty> difficulties;
    private final long trieSize;

    public List<Block> getBlocks() {
        return this.blocks;
    }

    public long getTrieSize() {
        return this.trieSize;
    }

    public SnapStatusResponseMessage(List<Block> blocks, List<BlockDifficulty> difficulties, long trieSize) {
        this.blocks = blocks;
        this.difficulties = difficulties;
        this.trieSize = trieSize;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.SNAP_STATUS_RESPONSE_MESSAGE;
    }

    public List<BlockDifficulty> getDifficulties() {
        return difficulties;
    }

    @Override
    public byte[] getEncodedMessage() {
        List<byte[]> rlpBlocks = this.blocks.stream().map(Block::getEncoded).map(RLP::encode).collect(Collectors.toList());
        List<byte[]> rlpDifficulties = this.difficulties.stream().map(BlockDifficulty::getBytes).map(RLP::encode).collect(Collectors.toList());
        byte[] rlpTrieSize = RLP.encodeBigInteger(BigInteger.valueOf(this.trieSize));

        return RLP.encodeList(RLP.encodeList(rlpBlocks.toArray(new byte[][]{})), RLP.encodeList(rlpDifficulties.toArray(new byte[][]{})), rlpTrieSize);
    }

    public static Message decodeMessage(BlockFactory blockFactory, RLPList list) {
        RLPList rlpBlocks = RLP.decodeList(list.get(0).getRLPData());
        RLPList rlpDifficulties = RLP.decodeList(list.get(1).getRLPData());
        List<Block> blocks = Lists.newArrayList();
        List<BlockDifficulty> difficulties = Lists.newArrayList();
        for (int i = 0; i < rlpBlocks.size(); i++) {
            blocks.add(blockFactory.decodeBlock(rlpBlocks.get(i).getRLPData()));
        }
        for (int i = 0; i < rlpDifficulties.size(); i++) {
            difficulties.add(new BlockDifficulty(new BigInteger(rlpDifficulties.get(i).getRLPData())));
        }

        byte[] rlpTrieSize = list.get(2).getRLPData();
        long trieSize = rlpTrieSize == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpTrieSize).longValue();

        return new SnapStatusResponseMessage(blocks, difficulties, trieSize);
    }

    @Override
    public void accept(MessageVisitor v) {
        v.apply(this);
    }
}

/*
 * This file is part of RskJ
 * Copyright (C) 2023 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

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

public class SnapBlocksResponseMessage extends MessageWithId {
    private final long id;

    private final List<Block> blocks;
    private final List<BlockDifficulty> difficulties;

    public SnapBlocksResponseMessage(long id, List<Block> blocks, List<BlockDifficulty> difficulties) {
        this.id = id;
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
    public long getId() {
        return this.id;
    }

    @Override
    protected byte[] getEncodedMessageWithoutId() {
        List<byte[]> rlpBlocks = this.blocks.stream().map(Block::getEncoded).map(RLP::encode).collect(Collectors.toList());
        List<byte[]> rlpDifficulties = this.difficulties.stream().map(BlockDifficulty::getBytes).map(RLP::encode).collect(Collectors.toList());
        return RLP.encodeList(RLP.encodeList(rlpBlocks.toArray(new byte[][]{})),
                RLP.encodeList(rlpDifficulties.toArray(new byte[][]{})));
    }

    public static Message decodeMessage(BlockFactory blockFactory, RLPList list) {
        byte[] rlpId = list.get(0).getRLPData();
        long id = rlpId == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpId).longValue();

        RLPList message = (RLPList)RLP.decode2(list.get(1).getRLPData()).get(0);
        List<Block> blocks = Lists.newArrayList();
        List<BlockDifficulty> blockDifficulties = Lists.newArrayList();
        RLPList blocksRLP = RLP.decodeList(message.get(0).getRLPData());
        for (int i = 0; i < blocksRLP.size(); i++) {
            blocks.add(blockFactory.decodeBlock(blocksRLP.get(i).getRLPData()));
        }
        RLPList difficultiesRLP = RLP.decodeList(message.get(1).getRLPData());
        for (int i = 0; i < difficultiesRLP.size(); i++) {
            blockDifficulties.add(new BlockDifficulty(new BigInteger(difficultiesRLP.get(i).getRLPData())));
        }
        return new SnapBlocksResponseMessage(id, blocks, blockDifficulties);
    }

    @Override
    public void accept(MessageVisitor v) {
        v.apply(this);
    }
}

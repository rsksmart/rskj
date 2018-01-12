/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

package org.ethereum.jsontestsuite.builder;

import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Transaction;
import org.ethereum.jsontestsuite.Env;
import org.ethereum.jsontestsuite.model.BlockHeaderTck;
import org.ethereum.jsontestsuite.model.TransactionTck;
import org.ethereum.util.ByteUtil;

import java.util.ArrayList;
import java.util.List;

import static org.ethereum.crypto.HashUtil.EMPTY_TRIE_HASH;
import static org.ethereum.util.ByteUtil.byteArrayToLong;

public class BlockBuilder {
    private static final byte[] ZERO_BYTE_ARRAY = new byte[]{0};

    public static Block build(BlockHeaderTck header,
                              List<TransactionTck> transactionsTck,
                              List<BlockHeaderTck> unclesTck) {

        if (header == null) return null;

        List<BlockHeader> uncles = new ArrayList<>();
        if (unclesTck != null) for (BlockHeaderTck uncle : unclesTck)
            uncles.add(BlockHeaderBuilder.build(uncle));

        List<Transaction> transactions = new ArrayList<>();
        if (transactionsTck != null) for (TransactionTck tx : transactionsTck)
            transactions.add(TransactionBuilder.build(tx));

        BlockHeader blockHeader = BlockHeaderBuilder.build(header);
        Block block = new Block(
                blockHeader,
                transactions, uncles);

        return block;
    }


    public static Block build(Env env){

        Block block = new Block(
                ByteUtil.EMPTY_BYTE_ARRAY,
                ByteUtil.EMPTY_BYTE_ARRAY,
                env.getCurrentCoinbase(),
                ByteUtil.EMPTY_BYTE_ARRAY,
                env.getCurrentDifficulty(),
                byteArrayToLong(env.getCurrentNumber()),
                env.getCurrentGasLimit(),
                0L,
                byteArrayToLong(env.getCurrentTimestamp()),
                new byte[32],
                ZERO_BYTE_ARRAY,
                ZERO_BYTE_ARRAY,
                ZERO_BYTE_ARRAY,
                ZERO_BYTE_ARRAY,
                ZERO_BYTE_ARRAY,
                EMPTY_TRIE_HASH,
                EMPTY_TRIE_HASH,
                EMPTY_TRIE_HASH,
                null, null, null);

        return block;
    }
}

/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package co.rsk.core;

import co.rsk.peg.PegTestUtils;
import org.ethereum.TestUtils;
import org.ethereum.core.Block;
import org.ethereum.core.Bloom;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.RLP;
import org.junit.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by SDL on 12/5/2017.
 */
public class BlockEncodingTest {
    private static final byte[] EMPTY_LIST_HASH = HashUtil.keccak256(RLP.encodeList());

    @Test(expected = ArithmeticException.class)
    public void testBadBlockEncoding1() {

        List<Transaction> txs = new ArrayList<>();

        Transaction tx = new Transaction(
                BigInteger.ZERO.toByteArray(),
                BigInteger.ONE.toByteArray(),
                BigInteger.valueOf(21000).toByteArray(),
                new ECKey().getAddress(),
                BigInteger.valueOf(1000).toByteArray(),
                null);

        txs.add(tx);

        byte[] bigBadByteArray = new byte[10000];

        Arrays.fill(bigBadByteArray , (byte) -1);

        FreeBlock fblock = new FreeBlock(
                PegTestUtils.createHash3().getBytes(),          // parent hash
                EMPTY_LIST_HASH,       // uncle hash
                TestUtils.randomAddress().getBytes(),            // coinbase
                new Bloom().getData(),          // logs bloom
                BigInteger.ONE.toByteArray(),    // difficulty
                bigBadByteArray ,
                bigBadByteArray , // gasLimit
                bigBadByteArray ,// gasUsed
                bigBadByteArray , //timestamp
                new byte[0],                    // extraData
                new byte[0],                    // mixHash
                new byte[]{0},         // provisory nonce
                HashUtil.EMPTY_TRIE_HASH,       // receipts root
                Block.getTxTrieRoot(txs, false), // transaction root
                HashUtil.EMPTY_TRIE_HASH,    //EMPTY_TRIE_HASH,   // state root
                txs,                            // transaction list
                null,  // uncle list
                BigInteger.TEN.toByteArray(),
                new byte[0]
        );

        // Now decode, and re-encode
        Block parsedBlock = new Block(fblock.getEncoded());
        // must throw java.lang.ArithmeticException
        parsedBlock.getGasLimit(); // forced parse

    }
}

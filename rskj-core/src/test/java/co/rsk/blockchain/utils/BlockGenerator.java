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

package co.rsk.blockchain.utils;

import co.rsk.config.TestSystemProperties;
import co.rsk.core.BlockDifficulty;
import co.rsk.core.Coin;
import co.rsk.core.DifficultyCalculator;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.BlockChainImpl;
import co.rsk.mine.MinimumGasPriceCalculator;
import co.rsk.peg.PegTestUtils;
import co.rsk.peg.simples.SimpleBlock;
import co.rsk.peg.simples.SimpleRskTransaction;
import org.apache.commons.collections4.CollectionUtils;
import org.bouncycastle.pqc.math.linearalgebra.ByteUtils;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.*;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPList;

import java.math.BigInteger;
import java.util.*;

import static org.ethereum.core.Genesis.getZeroHash;
import static org.ethereum.crypto.HashUtil.EMPTY_TRIE_HASH;

/**
 * Created by ajlopez on 5/10/2016.
 */
public class BlockGenerator {

    private static final byte[] EMPTY_LIST_HASH = HashUtil.keccak256(RLP.encodeList());

    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    private static final Block[] blockCache = new Block[5];

    private final DifficultyCalculator difficultyCalculator;
    private int count = 0;
    private TestSystemProperties config;

    public BlockGenerator() {
        this(new TestSystemProperties());
    }

    public BlockGenerator(TestSystemProperties config) {
        this.config = config;
        this.difficultyCalculator = new DifficultyCalculator(this.config);
    }

    public Genesis getGenesisBlock() {
        return getNewGenesisBlock(3141592, Collections.emptyMap(), new byte[] { 2, 0, 0});
    }

    private Genesis getNewGenesisBlock(long initialGasLimit, Map<byte[], BigInteger> preMineMap, byte[] difficulty) {
        /* Unimportant address. Because there is no subsidy
        ECKey ecKey;
        byte[] address;
        SecureRandom rand =new InsecureRandom(0);
        ecKey = new ECKey(rand);
        address = ecKey.getAddress();
        */
        byte[] coinbase    = Hex.decode("e94aef644e428941ee0a3741f28d80255fddba7f");

        long   timestamp         = 0; // predictable timeStamp

        byte[] parentHash  = EMPTY_BYTE_ARRAY;
        byte[] extraData   = EMPTY_BYTE_ARRAY;

        long   gasLimit         = initialGasLimit;

        byte[] bitcoinMergedMiningHeader = null;
        byte[] bitcoinMergedMiningMerkleProof = null;
        byte[] bitcoinMergedMiningCoinbaseTransaction = null;

        Map<RskAddress, AccountState> accounts = new HashMap<>();
        for (Map.Entry<byte[], BigInteger> accountEntry : preMineMap.entrySet()) {
            AccountState acctState = new AccountState(BigInteger.valueOf(0), new Coin(accountEntry.getValue()));
            accounts.put(new RskAddress(accountEntry.getKey()), acctState);
        }

        return new Genesis(parentHash, EMPTY_LIST_HASH, coinbase, getZeroHash(),
                difficulty, 0, gasLimit, 0, timestamp, extraData,
                bitcoinMergedMiningHeader, bitcoinMergedMiningMerkleProof,
                bitcoinMergedMiningCoinbaseTransaction, BigInteger.valueOf(100L).toByteArray(), accounts, Collections.emptyMap(), Collections.emptyMap());
    }

    public Block getBlock(int number) {
        if (blockCache[number] != null) {
            return blockCache[number];
        }

        synchronized (blockCache) {
            for (int k = 0; k <= number; k++) {
                if (blockCache[k] == null) {
                    if (k == 0) {
                        blockCache[0] = this.getGenesisBlock();
                    }
                    else {
                        blockCache[k] = this.createChildBlock(blockCache[k - 1]);
                    }
                }
            }

            return blockCache[number];
        }
    }

    public Block createChildBlock(Block parent) {
        return createChildBlock(parent, 0);
    }

    public Block createChildBlock(Block parent, long fees, List<BlockHeader> uncles, byte[] difficulty) {
        List<Transaction> txs = new ArrayList<>();
        byte[] unclesListHash = HashUtil.keccak256(BlockHeader.getUnclesEncodedEx(uncles));

        return new Block(
                parent.getHash().getBytes(), // parent hash
                unclesListHash, // uncle hash
                parent.getCoinbase().getBytes(),
                ByteUtils.clone(new Bloom().getData()),
                difficulty, // difficulty
                parent.getNumber() + 1,
                parent.getGasLimit(),
                parent.getGasUsed(),
                parent.getTimestamp() + ++count,
                EMPTY_BYTE_ARRAY,   // extraData
                EMPTY_BYTE_ARRAY,   // mixHash
                BigInteger.ZERO.toByteArray(),  // provisory nonce
                EMPTY_TRIE_HASH,   // receipts root
                BlockChainImpl.calcTxTrie(txs),  // transaction root
                ByteUtils.clone(parent.getStateRoot()), //EMPTY_TRIE_HASH,   // state root
                txs,       // transaction list
                uncles,        // uncle list
                null,
                Coin.valueOf(fees)
        );
//        return createChildBlock(parent, 0);
    }

    public Block createChildBlock(Block parent, List<Transaction> txs, byte[] stateRoot) {
        return createChildBlock(parent, txs, stateRoot, parent.getCoinbase().getBytes());
    }

    public Block createChildBlock(Block parent, List<Transaction> txs, byte[] stateRoot, byte[] coinbase) {
        Bloom logBloom = new Bloom();

        if (txs == null) {
            txs = new ArrayList<>();
        }

        return new Block(
                parent.getHash().getBytes(), // parent hash
                EMPTY_LIST_HASH, // uncle hash
                coinbase, // coinbase
                logBloom.getData(), // logs bloom
                parent.getDifficulty().getBytes(), // difficulty
                parent.getNumber() + 1,
                parent.getGasLimit(),
                parent.getGasUsed(),
                parent.getTimestamp() + ++count,
                EMPTY_BYTE_ARRAY,   // extraData
                EMPTY_BYTE_ARRAY,   // mixHash
                BigInteger.ZERO.toByteArray(),  // provisory nonce
                EMPTY_TRIE_HASH,   // receipts root
                BlockChainImpl.calcTxTrie(txs),  // transaction root
                stateRoot, //EMPTY_TRIE_HASH,   // state root
                txs,       // transaction list
                null,        // uncle list
                null,
                Coin.ZERO
        );
    }

    public Block createChildBlock(Block parent, int ntxs) {
        return createChildBlock(parent, ntxs, parent.getDifficulty().asBigInteger().longValue());
    }

    public Block createChildBlock(Block parent, int ntxs, long difficulty) {
        List<Transaction> txs = new ArrayList<>();

        for (int ntx = 0; ntx < ntxs; ntx++) {
            txs.add(new SimpleRskTransaction(null));
        }

        List<BlockHeader> uncles = new ArrayList<>();

        return createChildBlock(parent, txs, uncles, difficulty, null);
    }

    public Block createChildBlock(Block parent, List<Transaction> txs) {
        return createChildBlock(parent, txs, new ArrayList<>(), parent.getDifficulty().asBigInteger().longValue(), null);
    }

    public Block createChildBlock(Block parent, List<Transaction> txs, List<BlockHeader> uncles,
                                  long difficulty, BigInteger minGasPrice) {
        return createChildBlock(parent, txs, uncles, difficulty, minGasPrice, parent.getGasLimit());
    }

    public Block createChildBlock(Block parent, List<Transaction> txs, List<BlockHeader> uncles,
                                  long difficulty, BigInteger minGasPrice, byte[] gasLimit) {
        if (txs == null) {
            txs = new ArrayList<>();
        }

        if (uncles == null) {
            uncles = new ArrayList<>();
        }

        byte[] unclesListHash = HashUtil.keccak256(BlockHeader.getUnclesEncodedEx(uncles));

        BlockHeader newHeader = new BlockHeader(parent.getHash().getBytes(),
                unclesListHash,
                parent.getCoinbase().getBytes(),
                ByteUtils.clone(new Bloom().getData()),
                new byte[]{1},
                parent.getNumber()+1,
                gasLimit,
                0,
                parent.getTimestamp() + ++count,
                new byte[]{},
                new byte[]{},
                new byte[]{},
                new byte[]{},
                (minGasPrice != null) ? minGasPrice.toByteArray() : null,
                CollectionUtils.size(uncles)
        );

        if (difficulty == 0) {
            newHeader.setDifficulty(difficultyCalculator.calcDifficulty(newHeader, parent.getHeader()));
        }
        else {
            newHeader.setDifficulty(new BlockDifficulty(BigInteger.valueOf(difficulty)));
        }

        newHeader.setTransactionsRoot(Block.getTxTrie(txs).getHash().getBytes());

        newHeader.setStateRoot(ByteUtils.clone(parent.getStateRoot()));

        Block newBlock = new Block(newHeader, txs, uncles);

        return newBlock;
    }

    public Block createBlock(int number, int ntxs) {
        Bloom logBloom = new Bloom();
        Block parent = getGenesisBlock();

        List<Transaction> txs = new ArrayList<>();

        for (int ntx = 0; ntx < ntxs; ntx++) {
            txs.add(new SimpleRskTransaction(null));
        }

        Coin previousMGP = parent.getMinimumGasPrice() != null ? parent.getMinimumGasPrice() : Coin.valueOf(10L);
        Coin minimumGasPrice = new MinimumGasPriceCalculator().calculate(previousMGP, Coin.valueOf(100L));

        return new Block(
                parent.getHash().getBytes(), // parent hash
                EMPTY_LIST_HASH, // uncle hash
                parent.getCoinbase().getBytes(), // coinbase
                logBloom.getData(), // logs bloom
                parent.getDifficulty().getBytes(), // difficulty
                number,
                parent.getGasLimit(),
                parent.getGasUsed(),
                parent.getTimestamp() + ++count,
                EMPTY_BYTE_ARRAY,   // extraData
                EMPTY_BYTE_ARRAY,   // mixHash
                BigInteger.ZERO.toByteArray(),  // provisory nonce
                EMPTY_TRIE_HASH,   // receipts root
                EMPTY_TRIE_HASH,  // transaction receipts
                EMPTY_TRIE_HASH,   // state root
                txs,       // transaction list
                null,        // uncle list
                minimumGasPrice.getBytes(),
                Coin.ZERO
        );
    }

    public Block createSimpleChildBlock(Block parent, int ntxs) {
        Bloom logBloom = new Bloom();

        List<Transaction> txs = new ArrayList<>();

        for (int ntx = 0; ntx < ntxs; ntx++) {
            txs.add(new SimpleRskTransaction(PegTestUtils.createHash3().getBytes()));
        }

        return new SimpleBlock(
                parent.getHash().getBytes(), // parent hash
                EMPTY_LIST_HASH, // uncle hash
                parent.getCoinbase().getBytes(), // coinbase
                logBloom.getData(), // logs bloom
                parent.getDifficulty().getBytes(), // difficulty
                parent.getNumber() + 1,
                parent.getGasLimit(),
                parent.getGasUsed(),
                parent.getTimestamp() + ++count,
                EMPTY_BYTE_ARRAY,   // extraData
                EMPTY_BYTE_ARRAY,   // mixHash
                BigInteger.ZERO.toByteArray(),  // provisory nonce
                EMPTY_TRIE_HASH,   // receipts root
                EMPTY_TRIE_HASH,  // transaction receipts
                EMPTY_TRIE_HASH,   // state root
                txs,       // transaction list
                null        // uncle list
        );
    }

    public List<Block> getBlockChain(int size) {
        return getBlockChain(getGenesisBlock(), size);
    }

    public List<Block> getBlockChain(Block parent, int size, long difficulty) {
        return getBlockChain(parent, size,0,false, difficulty);
    }

    public List<Block> getBlockChain(Block parent, int size) {
        return getBlockChain(parent, size, 0);
    }

    public List<Block> getMinedBlockChain(Block parent, int size) {
        return getBlockChain(parent, size, 0, false, true, null);
    }

    public List<Block> getSimpleBlockChain(Block parent, int size) {
        return getSimpleBlockChain(parent, size, 0);
    }

    public List<Block> getBlockChain(Block parent, int size, int ntxs) {
        return getBlockChain(parent, size, ntxs, false);
    }

    public List<Block> getBlockChain(Block parent, int size, int ntxs, boolean withUncles) {
        return getBlockChain(parent, size, ntxs, withUncles, null);
    }

    public List<Block> getBlockChain(Block parent, int size, int ntxs, boolean withUncles, Long difficulty) {
        return getBlockChain(parent, size, ntxs, false, false, difficulty);
    }

    public List<Block> getBlockChain(Block parent, int size, int ntxs, boolean withUncles, boolean withMining, Long difficulty) {
        List<Block> chain = new ArrayList<Block>();
        List<BlockHeader> uncles = new ArrayList<>();
        int chainSize = 0;

        while (chainSize < size) {
            List<Transaction> txs = new ArrayList<>();

            for (int ntx = 0; ntx < ntxs; ntx++) {
                txs.add(new SimpleRskTransaction(null));
            }

            if (difficulty == null) {
                difficulty = 0l;
            }

            Block newblock = createChildBlock(
                    parent, txs, uncles,
                    difficulty,
                    BigInteger.valueOf(1));

            if (withMining) {
                newblock = new BlockMiner(config).mineBlock(newblock);
            }

            chain.add(newblock);

            if (withUncles) {
                uncles = new ArrayList<>();

                Block newuncle = createChildBlock(parent, ntxs);
                chain.add(newuncle);
                uncles.add(newuncle.getHeader());

                newuncle = createChildBlock(parent, ntxs);
                chain.add(newuncle);
                uncles.add(newuncle.getHeader());
            }

            parent = newblock;
            chainSize++;
        }

        return chain;
    }

    public List<Block> getSimpleBlockChain(Block parent, int size, int ntxs) {
        List<Block> chain = new ArrayList<Block>();

        while (chain.size() < size) {
            Block newblock = createSimpleChildBlock(parent, ntxs);
            chain.add(newblock);
            parent = newblock;
        }

        return chain;
    }

    public Block getNewGenesisBlock(long initialGasLimit, Map<byte[], BigInteger> preMineMap) {
        return getNewGenesisBlock(initialGasLimit,preMineMap, new byte[] { 0 });
    }

    private static byte[] nullReplace(byte[] e) {
        if (e == null) {
            return new byte[0];
        }

        return e;
    }

    private static byte[] removeLastElement(byte[] rlpEncoded) {
        ArrayList<RLPElement> params = RLP.decode2(rlpEncoded);
        RLPList block = (RLPList) params.get(0);
        RLPList header = (RLPList) block.get(0);
        if (header.size() < 20) {
            return rlpEncoded;
        }

        header.remove(header.size() - 1); // remove last element
        header.remove(header.size() - 1); // remove second last element

        List<byte[]> newHeader = new ArrayList<>();
        for (int i = 0; i < header.size(); i++) {
            byte[] e = nullReplace(header.get(i).getRLPData());
            if ((e.length > 32) && (i == 15))// fix bad feePaid
                e = new byte[32];

            newHeader.add(RLP.encodeElement(e));
        }

        byte[][] newHeaderElements = newHeader.toArray(new byte[newHeader.size()][]);
        byte[] newEncodedHeader = RLP.encodeList(newHeaderElements);
        return RLP.encodeList(
                newEncodedHeader,
                // If you request the .getRLPData() of a list you DO get the encoding prefix.
                // very weird.
                nullReplace(block.get(1).getRLPData()),
                nullReplace(block.get(2).getRLPData()));
    }
}

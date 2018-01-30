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

import co.rsk.config.RskSystemProperties;
import co.rsk.core.DifficultyCalculator;
import co.rsk.core.bc.BlockChainImpl;
import co.rsk.core.commons.Keccak256;
import co.rsk.mine.MinimumGasPriceCalculator;
import co.rsk.peg.PegTestUtils;
import co.rsk.peg.simples.SimpleBlock;
import co.rsk.peg.simples.SimpleRskTransaction;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieImpl;
import org.apache.commons.collections4.CollectionUtils;
import org.ethereum.core.*;
import org.ethereum.core.genesis.InitialAddressState;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.util.BIUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPList;
import org.spongycastle.pqc.math.linearalgebra.ByteUtils;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.ethereum.core.Genesis.getZeroHash;
import static org.ethereum.crypto.HashUtil.EMPTY_TRIE_HASH;
import static org.ethereum.util.ByteUtil.wrap;

/**
 * Created by ajlopez on 5/10/2016.
 */
public class BlockGenerator {
    private static final BlockGenerator INSTANCE = new BlockGenerator();

    private static final Keccak256 EMPTY_LIST_HASH = Keccak256.emptyListHash();

    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    private static final Block[] blockCache = new Block[5];

    /**
     * @deprecated
     * Using this singleton instance is a bad idea because {@link #count} will be shared by all tests.
     * This dependency makes tests flaky and prevents us from running tests in parallel or unordered.
     */
    public static BlockGenerator getInstance() {
        return INSTANCE;
    }

    private final DifficultyCalculator difficultyCalculator = new DifficultyCalculator(new RskSystemProperties());
    private int count = 0;

    public Genesis getGenesisBlock() {
        return getNewGenesisBlock(3141592, null, new byte[] { 2, 0, 0});
    }

    private Genesis getNewGenesisBlock(long initialGasLimit, Map<byte[], BigInteger> preMineMap, byte[] difficulty) {

        byte[] nonce       = new byte[]{0};
        byte[] mixHash     = new byte[]{0};

        /* Unimportant address. Because there is no subsidy
        ECKey ecKey;
        byte[] address;
        SecureRandom rand =new InsecureRandom(0);
        ecKey = new ECKey(rand);
        address = ecKey.getAddress();
        */
        byte[] coinbase = Hex.decode("e94aef644e428941ee0a3741f28d80255fddba7f");

        long   timestamp = 0; // predictable timeStamp

        Keccak256 parentHash = Keccak256.zeroHash();
        byte[] extraData = EMPTY_BYTE_ARRAY;

        long   gasLimit = initialGasLimit;

        byte[] bitcoinMergedMiningHeader = null;
        byte[] bitcoinMergedMiningMerkleProof = null;
        byte[] bitcoinMergedMiningCoinbaseTransaction = null;

        Genesis genesis = new Genesis(parentHash, EMPTY_LIST_HASH, coinbase, getZeroHash(),
                difficulty, 0, gasLimit, 0, timestamp, extraData,
                mixHash, nonce, bitcoinMergedMiningHeader, bitcoinMergedMiningMerkleProof,
                bitcoinMergedMiningCoinbaseTransaction, BigInteger.valueOf(100L).toByteArray());

        if (preMineMap != null) {
            Map<ByteArrayWrapper, InitialAddressState> preMineMap2 = generatePreMine(preMineMap);
            genesis.setPremine(preMineMap2);

            genesis.setStateRoot(generateRootHash(preMineMap2));
        }

        return genesis;
    }

    private Keccak256 generateRootHash(Map<ByteArrayWrapper, InitialAddressState> premine){
        Trie state = new TrieImpl(null, true);

        for (ByteArrayWrapper key : premine.keySet())
            state = state.put(key.getData(), premine.get(key).getAccountState().getEncoded());

        return new Keccak256(state.getHash());
    }

    private Map<ByteArrayWrapper, InitialAddressState> generatePreMine(Map<byte[], BigInteger> alloc){
        Map<ByteArrayWrapper, InitialAddressState> premine = new HashMap<>();

        for (byte[] key : alloc.keySet()) {
            AccountState acctState = new AccountState(BigInteger.valueOf(0), alloc.get(key));
            premine.put(wrap(key), new InitialAddressState(acctState, null));
        }

        return premine;
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
                parent.getHash(), // parent hash
                new Keccak256(unclesListHash), // uncle hash
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
                parent.getStateRoot(), //EMPTY_TRIE_HASH,   // state root
                txs,       // transaction list
                uncles,        // uncle list
                null,
                BigInteger.valueOf(fees)
        );
//        return createChildBlock(parent, 0);
    }

    public Block createChildBlock(Block parent, List<Transaction> txs, Keccak256 stateRoot) {
        return createChildBlock(parent, txs, stateRoot, parent.getCoinbase().getBytes());
    }

    public Block createChildBlock(Block parent, List<Transaction> txs, Keccak256 stateRoot, byte[] coinbase) {
        Bloom logBloom = new Bloom();

        if (txs == null) {
            txs = new ArrayList<>();
        }

        return new Block(
                parent.getHash(), // parent hash
                EMPTY_LIST_HASH, // uncle hash
                coinbase, // coinbase
                logBloom.getData(), // logs bloom
                parent.getDifficulty(), // difficulty
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
                BigInteger.ZERO
        );
    }

    public Block createChildBlock(Block parent, int ntxs) {
        return createChildBlock(parent, ntxs, BIUtil.toBI(parent.getDifficulty()).longValue());
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
        return createChildBlock(parent, txs, new ArrayList<>(), BIUtil.toBI(parent.getDifficulty()).longValue(), null);
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

        BlockHeader newHeader = new BlockHeader(parent.getHash(),
                new Keccak256(unclesListHash),
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
            newHeader.setDifficulty(difficultyCalculator.calcDifficulty(newHeader, parent.getHeader()).toByteArray());
        }
        else {
            newHeader.setDifficulty(BigInteger.valueOf(difficulty).toByteArray());
        }

        newHeader.setTransactionsRoot(Block.getTxTrie(txs).getHash());

        newHeader.setStateRoot(parent.getStateRoot());

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

        byte[] parentMGP = (parent.getMinimumGasPrice() != null) ? parent.getMinimumGasPrice() : BigInteger.valueOf(10L).toByteArray();
        BigInteger minimumGasPrice = new MinimumGasPriceCalculator().calculate(new BigInteger(1, parentMGP)
                , BigInteger.valueOf(100L));

        return new Block(
                parent.getHash(), // parent hash
                EMPTY_LIST_HASH, // uncle hash
                parent.getCoinbase().getBytes(), // coinbase
                logBloom.getData(), // logs bloom
                parent.getDifficulty(), // difficulty
                number,
                parent.getGasLimit(),
                parent.getGasUsed(),
                parent.getTimestamp() + ++count,
                EMPTY_BYTE_ARRAY,   // extraData
                EMPTY_BYTE_ARRAY,   // mixHash
                BigInteger.ZERO.toByteArray(),  // provisory nonce
                EMPTY_TRIE_HASH,   // receipts root
                EMPTY_TRIE_HASH,  // transaction receipts
                new Keccak256(EMPTY_TRIE_HASH),   // state root
                txs,       // transaction list
                null,        // uncle list
                minimumGasPrice.toByteArray(),
                BigInteger.ZERO
        );
    }

    public Block createSimpleChildBlock(Block parent, int ntxs) {
        Bloom logBloom = new Bloom();

        List<Transaction> txs = new ArrayList<>();

        for (int ntx = 0; ntx < ntxs; ntx++) {
            txs.add(new SimpleRskTransaction(PegTestUtils.createHash3()));
        }

        return new SimpleBlock(
                parent.getHash(), // parent hash
                EMPTY_LIST_HASH, // uncle hash
                parent.getCoinbase().getBytes(), // coinbase
                logBloom.getData(), // logs bloom
                parent.getDifficulty(), // difficulty
                parent.getNumber() + 1,
                parent.getGasLimit(),
                parent.getGasUsed(),
                parent.getTimestamp() + ++count,
                EMPTY_BYTE_ARRAY,   // extraData
                EMPTY_BYTE_ARRAY,   // mixHash
                BigInteger.ZERO.toByteArray(),  // provisory nonce
                EMPTY_TRIE_HASH,   // receipts root
                EMPTY_TRIE_HASH,  // transaction receipts
                new Keccak256(EMPTY_TRIE_HASH),   // state root
                txs,       // transaction list
                null        // uncle list
        );
    }

    public Block createFallbackMinedChildBlockWithTimeStamp(Block parent, byte[] difficulty, long timeStamp, boolean goodSig) {
        List<Transaction> txs = new ArrayList<>();
        Block block = new Block(
                parent.getHash(), // parent hash
                EMPTY_LIST_HASH, // uncle hash
                parent.getCoinbase().getBytes(),
                ByteUtils.clone(new Bloom().getData()),
                difficulty, // difficulty
                parent.getNumber() + 1,
                parent.getGasLimit(),
                parent.getGasUsed(),
                timeStamp,
                EMPTY_BYTE_ARRAY,   // extraData
                EMPTY_BYTE_ARRAY,   // mixHash
                BigInteger.ZERO.toByteArray(),  // provisory nonce
                EMPTY_TRIE_HASH,   // receipts root
                BlockChainImpl.calcTxTrie(txs),  // transaction root
                parent.getStateRoot(), //EMPTY_TRIE_HASH,   // state root
                txs,       // transaction list
                null,        // uncle list
                null,
                BigInteger.ZERO
        );

        ECKey fallbackMiningKey0 = ECKey.fromPrivate(BigInteger.TEN);
        ECKey fallbackMiningKey1 = ECKey.fromPrivate(BigInteger.TEN.add(BigInteger.ONE));

        ECKey fallbackKey;

        if (block.getNumber() % 2 == 0) {
            fallbackKey = fallbackMiningKey0;
        } else {
            fallbackKey = fallbackMiningKey1;
        }

        byte[] signature = fallbackSign(block.getHashForMergedMining().getBytes(), fallbackKey);

        if (!goodSig) {
            // just make it a little bad
            signature[5] = (byte) (signature[5]+1);
        }

        block.setBitcoinMergedMiningHeader(signature);
        return block;
    }

    byte[] fallbackSign(byte[] hash, ECKey privKey) {
        ECKey.ECDSASignature signature = privKey.sign(hash);

        byte vdata = signature.v;
        byte[] rdata = signature.r.toByteArray();
        byte[] sdata = signature.s.toByteArray();

        byte[] vencoded =  RLP.encodeByte(vdata);
        byte[] rencoded = RLP.encodeElement(rdata);
        byte[] sencoded = RLP.encodeElement(sdata);

        return RLP.encodeList(vencoded, rencoded, sencoded);
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
                    null);

            if (withMining) {
                newblock = BlockMiner.mineBlock(newblock);
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

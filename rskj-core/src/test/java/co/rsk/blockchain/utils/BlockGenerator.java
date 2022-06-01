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

import co.rsk.config.MiningConfig;
import co.rsk.core.BlockDifficulty;
import co.rsk.core.Coin;
import co.rsk.core.DifficultyCalculator;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.BlockHashesHelper;
import co.rsk.crypto.Keccak256;
import co.rsk.mine.MinimumGasPriceCalculator;
import co.rsk.peg.PegTestUtils;
import co.rsk.peg.simples.SimpleRskTransaction;
import org.bouncycastle.pqc.math.linearalgebra.ByteUtils;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.*;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPList;

import java.math.BigInteger;
import java.util.*;

import static org.ethereum.core.Genesis.getZeroHash;

/**
 * Created by ajlopez on 5/10/2016.
 */
public class BlockGenerator {

    private static final byte[] EMPTY_LIST_HASH = HashUtil.keccak256(RLP.encodeList());

    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    private final Block[] blockCache = new Block[5];

    private final DifficultyCalculator difficultyCalculator;
    private final BlockFactory blockFactory;
    private int count = 0;
    private ActivationConfig activationConfig;

    public BlockGenerator() {
        this(Constants.regtest(), ActivationConfigsForTest.regtest());
    }

    public BlockGenerator(Constants networkConstants, ActivationConfig activationConfig) {
        this.activationConfig = activationConfig;
        this.difficultyCalculator = new DifficultyCalculator(activationConfig, networkConstants);
        this.blockFactory = new BlockFactory(activationConfig);
    }

    public Genesis getGenesisBlock() {
        return getNewGenesisBlock(3141592, Collections.emptyMap(), new byte[] { 2, 0, 0});
    }

    public Genesis getGenesisBlock(long gasLimit) {
        return getNewGenesisBlock(gasLimit, Collections.emptyMap(), new byte[] { 2, 0, 0});
    }

    public Genesis getGenesisBlock(Map<RskAddress, AccountState> accounts) {
        return getNewGenesisBlock(3141592, accounts, new byte[] {2, 0, 0});
    }

    private Genesis getNewGenesisBlock(long initialGasLimit, Map<RskAddress, AccountState> accounts, byte[] difficulty) {
        /* Unimportant address. Because there is no subsidy
        ECKey ecKey;
        byte[] address;
        SecureRandom rand =new InsecureRandom(0);
        ecKey = new ECKey(rand);
        address = ecKey.getAddress();
        */
        byte[] coinbase    = Hex.decode("e94aef644e428941ee0a3741f28d80255fddba7f");

        long   timestamp         = 0; // predictable timeStamp

        byte[] parentHash  = Keccak256.ZERO_HASH.getBytes();
        byte[] extraData   = EMPTY_BYTE_ARRAY;

        long   gasLimit         = initialGasLimit;

        boolean isRskip126Enabled = activationConfig.isActive(ConsensusRule.RSKIP126, 0);
        boolean useRskip92Encoding = activationConfig.isActive(ConsensusRule.RSKIP92, 0);
        return new Genesis(
                isRskip126Enabled, accounts, Collections.emptyMap(), Collections.emptyMap(), new GenesisHeader(
                parentHash,
                EMPTY_LIST_HASH,
                getZeroHash(),
                difficulty,
                0,
                        ByteUtil.longToBytes(gasLimit),
                0,
                timestamp,
                extraData,
                null,
                null,
                null,
                BigInteger.valueOf(100L).toByteArray(),
                useRskip92Encoding,
                coinbase)
        );
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
        byte[] unclesListHash = HashUtil.keccak256(BlockHeader.getUnclesEncodedEx(uncles));

        long blockNumber = parent.getNumber() + 1;

        byte[] ummRoot = activationConfig.isActive(ConsensusRule.RSKIPUMM, blockNumber) ? new byte[0] : null;

        BlockHeader newHeader = blockFactory.getBlockHeaderBuilder()
                .setParentHashFromKeccak256(parent.getHash())
                .setUnclesHash(unclesListHash).setCoinbase(parent.getCoinbase())
                .setStateRoot(ByteUtils.clone(parent.getStateRoot()))
                .setEmptyLogsBloom()
                .setEmptyReceiptTrieRoot()
                .setDifficultyFromBytes(difficulty)
                .setNumber(parent.getNumber()+1)
                .setGasLimit(parent.getGasLimit())
                .setGasUsed( parent.getGasUsed()) // why ? I just copied the value before
                .setTimestamp(parent.getTimestamp() + ++count)
                .setPaidFees(Coin.valueOf(fees))
                .setEmptyMergedMiningForkDetectionData()
                .setUncleCount(uncles.size())
                .setUmmRoot(ummRoot)
                .setCreateParallelCompliantHeader(activationConfig.isActive(ConsensusRule.RSKIP144, blockNumber))
                .build();

        return blockFactory.newBlock(
                newHeader,
                Collections.emptyList(),
                uncles
        );
    }

    public Block createChildBlock(Block parent, List<Transaction> txs, byte[] stateRoot) {
        return createChildBlock(parent, txs, stateRoot, parent.getCoinbase().getBytes());
    }

    public Block createChildBlock(Block parent, List<Transaction> txs, byte[] stateRoot, byte[] coinbase) {
        Bloom logBloom = new Bloom();

        boolean isRskip126Enabled = activationConfig.isActive(ConsensusRule.RSKIP126, 0);

        long blockNumber = parent.getNumber() + 1;

        byte[] ummRoot = activationConfig.isActive(ConsensusRule.RSKIPUMM, blockNumber) ? new byte[0] : null;

        BlockHeader newHeader = blockFactory.getBlockHeaderBuilder()
                .setParentHashFromKeccak256(parent.getHash())
                .setTxTrieRoot(BlockHashesHelper.getTxTrieRoot(txs, isRskip126Enabled))
                .setCoinbase(parent.getCoinbase())
                .setStateRoot(ByteUtils.clone(parent.getStateRoot()))
                .setEmptyLogsBloom()
                .setEmptyReceiptTrieRoot()
                .setDifficulty(parent.getDifficulty())
                .setNumber(parent.getNumber()+1)
                .setGasLimit(parent.getGasLimit())
                .setGasUsed( parent.getGasUsed()) // why ? I just copied the value before
                .setTimestamp(parent.getTimestamp() + ++count)
                .setEmptyMergedMiningForkDetectionData()
                .setUmmRoot(ummRoot)
                .setCreateParallelCompliantHeader(activationConfig.isActive(ConsensusRule.RSKIP144, blockNumber))
                .build();

        return blockFactory.newBlock(
                newHeader,
                txs,
                Collections.emptyList(),
                false
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

    public Block createChildBlock(
            Block parent, List<Transaction> txs, List<BlockHeader> uncles,
            long difficulty, BigInteger minGasPrice, byte[] gasLimit, RskAddress coinbase) {
        short[] edges = activationConfig.isActive(ConsensusRule.RSKIP144, parent.getNumber() + 1) ? new short[0] : null;
        return createChildBlock(parent, txs, uncles, difficulty, minGasPrice, gasLimit, coinbase, edges);
    }

    public Block createChildBlock(Block parent, List<Transaction> txs, List<BlockHeader> uncles,
                                  long difficulty, BigInteger minGasPrice, byte[] gasLimit, RskAddress coinbase, short[] edges) {
        if (txs == null) {
            txs = new ArrayList<>();
        }

        if (uncles == null) {
            uncles = new ArrayList<>();
        }

        byte[] unclesListHash = HashUtil.keccak256(BlockHeader.getUnclesEncodedEx(uncles));

        byte[] miningForkDetectionData = parent.getNumber() + 1 > MiningConfig.REQUIRED_NUMBER_OF_BLOCKS_FOR_FORK_DETECTION_CALCULATION ?
                new byte[12] :
                new byte[0];

        long blockNumber = parent.getNumber() + 1;

        byte[] ummRoot = activationConfig.isActive(ConsensusRule.RSKIPUMM, blockNumber) ? new byte[0] : null;

        Coin coinMinGasPrice = (minGasPrice != null) ? new Coin(minGasPrice) : null;
        BlockHeader newHeader = blockFactory.getBlockHeaderBuilder()
                .setParentHash(parent.getHash().getBytes())
                .setUnclesHash(unclesListHash)
                .setCoinbase(coinbase)
                .setLogsBloom(ByteUtils.clone(new Bloom().getData()))
                .setDifficulty(BlockDifficulty.ONE)
                .setNumber(parent.getNumber()+1)
                .setGasLimit(gasLimit)
                .setGasUsed(0)
                .setTimestamp(parent.getTimestamp() + ++count)
                .setMergedMiningForkDetectionData(miningForkDetectionData)
                .setMinimumGasPrice(coinMinGasPrice)
                .setUncleCount(uncles.size())
                .setUmmRoot(ummRoot)
                .setTxExecutionListsEdges(edges)
                .build();

        if (difficulty == 0) {
            newHeader.setDifficulty(difficultyCalculator.calcDifficulty(newHeader, parent.getHeader()));
        }
        else {
            newHeader.setDifficulty(new BlockDifficulty(BigInteger.valueOf(difficulty)));
        }

        boolean isRskip126Enabled = activationConfig.isActive(ConsensusRule.RSKIP126, 0);
        newHeader.setTransactionsRoot(BlockHashesHelper.getTxTrieRoot(txs, isRskip126Enabled));

        newHeader.setStateRoot(ByteUtils.clone(parent.getStateRoot()));

        return blockFactory.newBlock(newHeader, txs, uncles, false);
    }

    public Block createChildBlock(Block parent, List<Transaction> txs, List<BlockHeader> uncles,
                                  long difficulty, BigInteger minGasPrice, byte[] gasLimit) {
        return createChildBlock(parent, txs, uncles, difficulty, minGasPrice, gasLimit, parent.getCoinbase());
    }

    public Block createBlock(int number, int ntxs) {
        return createBlock(number, ntxs, null);
    }

    public Block createBlock(int number, int ntxs, Long gasLimit){
        Block parent = gasLimit == null ? getGenesisBlock() : getGenesisBlock(gasLimit);

        List<Transaction> txs = new ArrayList<>();

        for (int ntx = 0; ntx < ntxs; ntx++) {
            txs.add(new SimpleRskTransaction(null));
        }

        Coin previousMGP = parent.getMinimumGasPrice() != null ? parent.getMinimumGasPrice() : Coin.valueOf(10L);
        Coin minimumGasPrice = new MinimumGasPriceCalculator(Coin.valueOf(100L)).calculate(previousMGP);

        boolean isRskip126Enabled = activationConfig.isActive(ConsensusRule.RSKIP126, 0);

        byte[] ummRoot = activationConfig.isActive(ConsensusRule.RSKIPUMM, number) ? new byte[0] : null;

        BlockHeader newHeader = blockFactory.getBlockHeaderBuilder()
                .setParentHashFromKeccak256(parent.getHash())
                .setCoinbase(parent.getCoinbase())
                .setTxTrieRoot(BlockHashesHelper.getTxTrieRoot(txs, isRskip126Enabled))
                .setStateRoot(ByteUtils.clone(parent.getStateRoot()))
                .setEmptyLogsBloom()
                .setEmptyReceiptTrieRoot()
                .setDifficulty(parent.getDifficulty())
                .setNumber(number)
                .setGasLimit(parent.getGasLimit())
                .setGasUsed( parent.getGasUsed()) // why ? I just copied the value before
                .setTimestamp(parent.getTimestamp() + ++count)
                .setEmptyMergedMiningForkDetectionData()
                .setMinimumGasPrice(minimumGasPrice)
                .setUmmRoot(ummRoot)
                .setCreateParallelCompliantHeader(activationConfig.isActive(ConsensusRule.RSKIP144, number))
                .build();

        return blockFactory.newBlock(
                newHeader,
                txs,
                Collections.emptyList()
        );
    }

    public Block createSimpleChildBlock(Block parent, int ntxs) {
        Bloom logBloom = new Bloom();

        List<Transaction> txs = new ArrayList<>();

        for (int ntx = 0; ntx < ntxs; ntx++) {
            txs.add(new SimpleRskTransaction(PegTestUtils.createHash3().getBytes()));
        }

        long blockNumber = parent.getNumber() + 1;

        byte[] ummRoot = activationConfig.isActive(ConsensusRule.RSKIPUMM, blockNumber) ? new byte[0] : null;

        BlockHeader newHeader = blockFactory.getBlockHeaderBuilder()
                .setParentHashFromKeccak256(parent.getHash())
                .setCoinbase(parent.getCoinbase())
                .setStateRoot(ByteUtils.clone(parent.getStateRoot()))
                .setEmptyTxTrieRoot()
                .setEmptyLogsBloom()
                .setEmptyReceiptTrieRoot()
                .setDifficulty(parent.getDifficulty())
                .setNumber(parent.getNumber() + 1)
                .setGasLimit(parent.getGasLimit())
                .setGasUsed( parent.getGasUsed()) // why ? I just copied the value before
                .setTimestamp(parent.getTimestamp() + ++count)
                .setEmptyMergedMiningForkDetectionData()
                .setMinimumGasPrice(Coin.valueOf(10))
                .setUmmRoot(ummRoot)
                .setCreateParallelCompliantHeader(activationConfig.isActive(ConsensusRule.RSKIP144, blockNumber))
                .build();

        return blockFactory.newBlock(
                newHeader,
                txs,
                Collections.emptyList()
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
                newblock = new BlockMiner(activationConfig).mineBlock(newblock);
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
        Map<RskAddress, AccountState> accounts = new HashMap<>();
        for (Map.Entry<byte[], BigInteger> accountEntry : preMineMap.entrySet()) {
            AccountState acctState = new AccountState(BigInteger.valueOf(0), new Coin(accountEntry.getValue()));
            accounts.put(new RskAddress(accountEntry.getKey()), acctState);
        }

        return getNewGenesisBlock(initialGasLimit, accounts, new byte[] { 0 });
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

        // TODO Fix
        //header.remove(header.size() - 1); // remove last element
        //header.remove(header.size() - 1); // remove second last element

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

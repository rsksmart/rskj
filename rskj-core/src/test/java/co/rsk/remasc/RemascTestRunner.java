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

package co.rsk.remasc;

import co.rsk.core.BlockDifficulty;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.TransactionExecutorFactory;
import co.rsk.core.bc.BlockChainImpl;
import co.rsk.core.bc.BlockExecutor;
import co.rsk.core.bc.BlockHashesHelper;
import co.rsk.crypto.Keccak256;
import co.rsk.db.RepositorySnapshot;
import co.rsk.peg.BridgeSupportFactory;
import co.rsk.peg.PegTestUtils;
import co.rsk.peg.RepositoryBtcBlockStoreWithCache;
import co.rsk.test.builders.BlockChainBuilder;
import org.ethereum.TestUtils;
import org.ethereum.config.Constants;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.RLP;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by martin.medina on 1/5/17.
 */
class RemascTestRunner {
    private static final byte[] EMPTY_LIST_HASH = HashUtil.keccak256(RLP.encodeList());

    private ECKey txSigningKey;

    private long txValue;

    private long minerFee;

    private long gasPrice;

    private int initialHeight;

    private List<SiblingElement> siblingElements;

    private List<Block> addedSiblings;

    private Blockchain blockchain;

    private BlockChainBuilder builder;

    private Block genesis;
    private RskAddress fixedCoinbase;

    public RemascTestRunner(BlockChainBuilder blockchainBuilder, Block genesis) {
        this.builder = blockchainBuilder;
        this.genesis = genesis;
    }

    public RemascTestRunner txSigningKey(ECKey txSigningKey) {
        this.txSigningKey = txSigningKey;
        return this;
    }

    public RemascTestRunner txValue(long txValue) {
        this.txValue = txValue;
        return this;
    }

    public RemascTestRunner minerFee(long minerFee) {
        this.minerFee = minerFee;
        return this;
    }

    public RemascTestRunner gasPrice(long gasPrice) {
        this.gasPrice = gasPrice;
        return this;
    }

    public RemascTestRunner initialHeight(int initialHeight) {
        this.initialHeight = initialHeight;
        return this;
    }

    public RemascTestRunner siblingElements(List<SiblingElement> siblingElements) {
        this.siblingElements = siblingElements;
        return this;
    }

    public RemascTestRunner setFixedCoinbase(RskAddress fixedCoinbase) {
        this.fixedCoinbase = fixedCoinbase;
        return this;
    }

    public List<Block> getAddedSiblings() {
        return addedSiblings;
    }

    public void start() {
        this.blockchain = this.builder.build();

        ((BlockChainImpl)this.blockchain).setNoValidation(true);

        this.addedSiblings = new ArrayList<>();
        List<Block> mainChainBlocks = new ArrayList<>();
        this.blockchain.tryToConnect(this.genesis);

        BlockFactory blockFactory = new BlockFactory(builder.getConfig().getActivationConfig());
        final ProgramInvokeFactoryImpl programInvokeFactory = new ProgramInvokeFactoryImpl();
        BlockTxSignatureCache blockTxSignatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(
                        builder.getConfig().getNetworkConstants().getBridgeConstants().getBtcParams()),
                builder.getConfig().getNetworkConstants().getBridgeConstants(),
                builder.getConfig().getActivationConfig());
        PrecompiledContracts precompiledContracts = new PrecompiledContracts(builder.getConfig(), bridgeSupportFactory);
        BlockExecutor blockExecutor = new BlockExecutor(
                builder.getConfig().getActivationConfig(),
                builder.getRepositoryLocator(),
                builder.getStateRootHandler(),
                new TransactionExecutorFactory(
                        builder.getConfig(),
                        builder.getBlockStore(),
                        null,
                        blockFactory,
                        programInvokeFactory,
                        precompiledContracts,
                        blockTxSignatureCache
                )
        );

        for(int i = 0; i <= this.initialHeight; i++) {
            int finalI = i;

            List<SiblingElement> siblingsForCurrentHeight = this.siblingElements.stream()
                    .filter(siblingElement -> siblingElement.getHeightToBeIncluded() == finalI)
                    .collect(Collectors.toList());

            List<BlockHeader> blockSiblings = new ArrayList<>();

            // Going to add siblings
            BlockDifficulty cummDifficulty = BlockDifficulty.ZERO;
            if (siblingsForCurrentHeight.size() > 0){
                cummDifficulty = blockchain.getTotalDifficulty();
            }

            for(SiblingElement sibling : siblingsForCurrentHeight) {
                RskAddress siblingCoinbase = TestUtils.randomAddress();
                Block mainchainSiblingParent = mainChainBlocks.get(sibling.getHeight() - 1);
                Block siblingBlock = createBlock(this.genesis, mainchainSiblingParent, PegTestUtils.createHash3(),
                        siblingCoinbase, Collections.emptyList(), minerFee, this.gasPrice, (long) i, this.txValue,
                        this.txSigningKey, null);

                blockSiblings.add(siblingBlock.getHeader());

                builder.getBlockStore().saveBlock(siblingBlock, cummDifficulty.add(siblingBlock.getCumulativeDifficulty()), false);
                this.addedSiblings.add(siblingBlock);
            }

            long txNonce = i;
            RskAddress coinbase = fixedCoinbase != null ? fixedCoinbase : TestUtils.randomAddress();
            Block block = createBlock(this.genesis, this.blockchain.getBestBlock(), PegTestUtils.createHash3(),
                                      coinbase, blockSiblings, minerFee, this.gasPrice, txNonce, this.txValue, this.txSigningKey, null);
            mainChainBlocks.add(block);

            blockExecutor.executeAndFillAll(block, this.blockchain.getBestBlock().getHeader());

            block.seal();
            ImportResult result = this.blockchain.tryToConnect(block);

            System.out.println(result);
        }
    }

    public Blockchain getBlockChain() {
        return this.blockchain;
    }

    public Coin getAccountBalance(RskAddress addr) {
        RepositorySnapshot repository = builder.getRepositoryLocator().snapshotAt(blockchain.getBestBlock().getHeader());
        return getAccountBalance(repository, addr);
    }

    public static Coin getAccountBalance(RepositorySnapshot repository, byte[] address) {
        return getAccountBalance(repository, new RskAddress(address));
    }

    public static Coin getAccountBalance(RepositorySnapshot repository, RskAddress addr) {
        AccountState accountState = repository.getAccountState(addr);

        return accountState == null ? null : repository.getAccountState(addr).getBalance();
    }

    public static Block createBlock(Block genesis, Block parentBlock, Keccak256 blockHash, RskAddress coinbase,
                                    List<BlockHeader> uncles, long gasLimit, long txNonce, long txValue,
                                    ECKey txSigningKey) {
        return createBlock(genesis, parentBlock, blockHash, coinbase, uncles, gasLimit, txNonce, txValue, txSigningKey, null);
    }

    public static Block createBlock(Block genesis, Block parentBlock, Keccak256 blockHash, RskAddress coinbase,
                                    List<BlockHeader> uncles, long gasLimit, long txNonce, long txValue,
                                    ECKey txSigningKey, Long difficulty) {
        return  createBlock(genesis, parentBlock, blockHash, coinbase, uncles, gasLimit, 1L, txNonce, txValue, txSigningKey, difficulty);
    }

    public static Block createBlock(Block genesis, Block parentBlock, Keccak256 blockHash, RskAddress coinbase,
                                    List<BlockHeader> uncles, long gasLimit, long gasPrice, long txNonce, long txValue,
                                    ECKey txSigningKey, Long difficulty) {
        if (gasLimit == 0) throw new IllegalArgumentException();
        Transaction tx = new Transaction(
                BigInteger.valueOf(txNonce).toByteArray(),
                BigInteger.valueOf(gasPrice).toByteArray(),
                BigInteger.valueOf(gasLimit).toByteArray(),
                new ECKey().getAddress() ,
                BigInteger.valueOf(txValue).toByteArray(),
                null,
                //TODO(mc): inject network chain id
                Constants.REGTEST_CHAIN_ID
        );

        tx.sign(txSigningKey.getPrivKeyBytes());
        //createBlook 1
        return createBlock(genesis, parentBlock, blockHash, coinbase, uncles, difficulty, tx);
    }

    public static Block createBlock(Block genesis, Block parentBlock, Keccak256 blockHash, RskAddress coinbase,
                                    List<BlockHeader> uncles, Long difficulty, Transaction... txsToInlcude) {
        List<Transaction> txs = new ArrayList<>();
        if (txsToInlcude != null) {
            for (Transaction tx : txsToInlcude) {
                txs.add(tx);
            }
        }

        Transaction remascTx = new RemascTransaction(parentBlock.getNumber() + 1);
        txs.add(remascTx);

        BigInteger difficultyAsBI = difficulty == null ? parentBlock.getDifficulty().asBigInteger() : BigInteger.valueOf(difficulty);

        if (difficultyAsBI.equals(BigInteger.ZERO)) {
            difficultyAsBI = BigInteger.ONE;
        }

        BlockDifficulty difficultyAsBD = new BlockDifficulty(difficultyAsBI);

        Coin paidFees = Coin.ZERO;
        for (Transaction tx : txs) {
            BigInteger gasLimit = new BigInteger(1, tx.getGasLimit());
            Coin gasPrice = tx.getGasPrice();
            paidFees = paidFees.add(gasPrice.multiply(gasLimit));
        }

        return new Block(
                new HardcodedHashBlockHeader(
                        parentBlock, coinbase, genesis, txs, difficultyAsBD, paidFees, uncles, blockHash
                ),
                txs,
                uncles,
                true,
                false
        );
    }

    private static class HardcodedHashBlockHeader extends BlockHeader {
        private final Keccak256 blockHash;

        public HardcodedHashBlockHeader(
                Block parentBlock, RskAddress coinbase, Block genesis, List<Transaction> txs,
                BlockDifficulty finalDifficulty, Coin paidFees, List<BlockHeader> uncles, Keccak256 blockHash) {
            super(
                    parentBlock.getHash().getBytes(), RemascTestRunner.EMPTY_LIST_HASH, coinbase,
                    genesis.getStateRoot(), BlockHashesHelper.getTxTrieRoot(txs, true),
                    HashUtil.EMPTY_TRIE_HASH, new Bloom().getData(), finalDifficulty, parentBlock.getNumber() + 1,
                    parentBlock.getGasLimit(), parentBlock.getGasUsed(), parentBlock.getTimestamp(), new byte[0],
                    paidFees, null, null, null, new byte[0],
                    Coin.valueOf(10), uncles.size(), false, true, false
            );
            this.blockHash = blockHash;
        }

        @Override
        public Keccak256 getHash() {
            return blockHash;
        }
    }
}

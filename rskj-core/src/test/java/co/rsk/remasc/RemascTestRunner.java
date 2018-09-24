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

import co.rsk.config.TestSystemProperties;
import co.rsk.core.BlockDifficulty;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.BlockChainImpl;
import co.rsk.core.bc.BlockExecutor;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.PegTestUtils;
import co.rsk.test.builders.BlockChainBuilder;
import org.ethereum.TestUtils;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.RLP;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;

import java.math.BigInteger;
import java.util.ArrayList;
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

        final ProgramInvokeFactoryImpl programInvokeFactory = new ProgramInvokeFactoryImpl();
        BlockExecutor blockExecutor = new BlockExecutor(blockchain.getRepository(),
                (tx, txindex, coinbase, track, block, totalGasUsed) -> new TransactionExecutor(
                    tx,
                    txindex,
                    block.getCoinbase(),
                    track,
                    blockchain.getBlockStore(),
                    null,
                    programInvokeFactory,
                    block,
                    null,
                    totalGasUsed,
                    builder.getConfig().getVmConfig(),
                    builder.getConfig().getBlockchainConfig(),
                    builder.getConfig().playVM(),
                    builder.getConfig().isRemascEnabled(),
                    builder.getConfig().vmTrace(),
                    new PrecompiledContracts(builder.getConfig()),
                    builder.getConfig().databaseDir(),
                    builder.getConfig().vmTraceDir(),
                    builder.getConfig().vmTraceCompressed())
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
                        siblingCoinbase, null, minerFee, this.gasPrice, Long.valueOf(i), this.txValue,
                        this.txSigningKey, null);

                blockSiblings.add(siblingBlock.getHeader());

                blockchain.getBlockStore().saveBlock(siblingBlock, cummDifficulty.add(siblingBlock.getCumulativeDifficulty()), false);
                this.addedSiblings.add(siblingBlock);
            }

            long txNonce = i;
            RskAddress coinbase = fixedCoinbase != null ? fixedCoinbase : TestUtils.randomAddress();
            Block block = createBlock(this.genesis, this.blockchain.getBestBlock(), PegTestUtils.createHash3(),
                                      coinbase, blockSiblings, minerFee, this.gasPrice, txNonce, this.txValue, this.txSigningKey, null);
            mainChainBlocks.add(block);

            blockExecutor.executeAndFillAll(block, this.blockchain.getBestBlock());

            block.seal();
            ImportResult result = this.blockchain.tryToConnect(block);

            System.out.println(result);
        }
    }

    public Blockchain getBlockChain() {
        return this.blockchain;
    }

    public Coin getAccountBalance(RskAddress addr) {
        return getAccountBalance(this.blockchain.getRepository(), addr);
    }

    public static Coin getAccountBalance(Repository repository, byte[] address) {
        return getAccountBalance(repository, new RskAddress(address));
    }

    public static Coin getAccountBalance(Repository repository, RskAddress addr) {
        AccountState accountState = repository.getAccountState(addr);

        return accountState == null ? null : repository.getAccountState(addr).getBalance();
    }

    public static Block createBlock(Block genesis, Block parentBlock, Keccak256 blockHash, RskAddress coinbase,
                                    List<BlockHeader> uncles, long gasLimit, long txNonce, long txValue,
                                    ECKey txSigningKey) {
        return createBlock(genesis, parentBlock, blockHash, coinbase, uncles, gasLimit, 1L,  txNonce,
                           txValue, txSigningKey, null);
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
                //TODO(lsebrie): remove this properties creation from method
                new TestSystemProperties().getBlockchainConfig().getCommonConstants().getChainId());

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

        long difficultyAsLong = difficulty == null ? parentBlock.getDifficulty().asBigInteger().longValue() : difficulty;

        if (difficultyAsLong == 0)
            difficultyAsLong = 1;

        byte[] diffBytes = BigInteger.valueOf(difficultyAsLong).toByteArray();

        Coin paidFees = Coin.ZERO;
        for (Transaction tx : txs) {
            BigInteger gasLimit = new BigInteger(1, tx.getGasLimit());
            Coin gasPrice = tx.getGasPrice();
            paidFees = paidFees.add(gasPrice.multiply(gasLimit));
        }

        Block block =  new Block(
                parentBlock.getHash().getBytes(),          // parent hash
                EMPTY_LIST_HASH,       // uncle hash
                coinbase.getBytes(),            // fixedCoinbase
                new Bloom().getData(),          // logs bloom
                diffBytes,    // difficulty
                parentBlock.getNumber() + 1,
                parentBlock.getGasLimit(),
                parentBlock.getGasUsed(),
                parentBlock.getTimestamp(),
                new byte[0],                    // extraData
                new byte[0],                    // mixHash
                BigInteger.ZERO.toByteArray(),         // provisory nonce
                HashUtil.EMPTY_TRIE_HASH,       // receipts root
                Block.getTxTrieRoot(txs, Block.isHardFork9999(parentBlock.getNumber() + 1)), // transaction root
                genesis.getStateRoot(),         //EMPTY_TRIE_HASH,   // state root
                txs,                            // transaction list
                uncles,                          // uncle list
                BigInteger.TEN.toByteArray(),
                paidFees
        ) {
            private BlockHeader harcodedHashHeader;

            @Override
            public BlockHeader getHeader() {
                if (harcodedHashHeader==null) {
                    harcodedHashHeader = new BlockHeader(super.getHeader().getEncoded(), false) {
                        @Override
                        public Keccak256 getHash() {
                            return blockHash;
                        }
                    };
                }
                return harcodedHashHeader;
            }

            @Override
            public Keccak256 getHash() {
                return blockHash;
            }

            @Override
            public void flushRLP() {
                if (harcodedHashHeader != null)
                    super.getHeader().setPaidFees(harcodedHashHeader.getPaidFees());

                super.flushRLP();

                harcodedHashHeader = null;
            }
        };

        return block;
    }
}

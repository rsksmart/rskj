/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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

package co.rsk.mine;

import co.rsk.config.MiningConfig;
import co.rsk.core.Coin;
import co.rsk.core.DifficultyCalculator;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.BlockExecutor;
import co.rsk.core.bc.BlockHashesHelper;
import co.rsk.core.bc.FamilyUtils;
import co.rsk.db.StateRootHandler;
import co.rsk.remasc.RemascTransaction;
import co.rsk.validators.BlockValidationRule;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.*;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.BlockStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.*;

import static org.ethereum.crypto.HashUtil.EMPTY_TRIE_HASH;

/**
 * This component helps build a new block to mine.
 * It can also be used to generate a new block from the pending state, which is useful
 * in places like Web3 with the 'pending' parameter.
 */
public class BlockToMineBuilder {
    private static final Logger logger = LoggerFactory.getLogger("blocktominebuilder");

    private final ActivationConfig activationConfig;
    private final MiningConfig miningConfig;
    private final Repository repository;
    private final StateRootHandler stateRootHandler;
    private final BlockStore blockStore;
    private final TransactionPool transactionPool;
    private final DifficultyCalculator difficultyCalculator;
    private final GasLimitCalculator gasLimitCalculator;
    private final BlockValidationRule validationRules;
    private final MinerClock clock;
    private final BlockFactory blockFactory;
    private final BlockExecutor executor;

    private final MinimumGasPriceCalculator minimumGasPriceCalculator;
    private final MinerUtils minerUtils;

    public BlockToMineBuilder(
            ActivationConfig activationConfig,
            MiningConfig miningConfig,
            Repository repository,
            StateRootHandler stateRootHandler,
            BlockStore blockStore,
            TransactionPool transactionPool,
            DifficultyCalculator difficultyCalculator,
            GasLimitCalculator gasLimitCalculator,
            BlockValidationRule validationRules,
            MinerClock clock,
            BlockFactory blockFactory,
            BlockExecutor blockExecutor) {
        this.activationConfig = Objects.requireNonNull(activationConfig);
        this.miningConfig = Objects.requireNonNull(miningConfig);
        this.repository = Objects.requireNonNull(repository);
        this.stateRootHandler = Objects.requireNonNull(stateRootHandler);
        this.blockStore = Objects.requireNonNull(blockStore);
        this.transactionPool = Objects.requireNonNull(transactionPool);
        this.difficultyCalculator = Objects.requireNonNull(difficultyCalculator);
        this.gasLimitCalculator = Objects.requireNonNull(gasLimitCalculator);
        this.validationRules = Objects.requireNonNull(validationRules);
        this.clock = Objects.requireNonNull(clock);
        this.blockFactory = blockFactory;
        this.executor = blockExecutor;
        this.minimumGasPriceCalculator = new MinimumGasPriceCalculator(Coin.valueOf(miningConfig.getMinGasPriceTarget()));
        this.minerUtils = new MinerUtils();
    }

    /**
     * Creates a new block to mine based on the previous mainchain blocks.
     *
     * @param mainchain last best chain blocks where 0 index is the best block and so on.
     * @param extraData extra data to pass to the block being built.
     */
    public Block build(List<Block> mainchain, byte[] extraData) {
        Block newBlockParent = mainchain.get(0);
        List<BlockHeader> uncles = FamilyUtils.getUnclesHeaders(
                blockStore,
                newBlockParent.getNumber() + 1,
                newBlockParent.getHash().getBytes(),
                miningConfig.getUncleGenerationLimit()
        );


        if (uncles.size() > miningConfig.getUncleListLimit()) {
            uncles = uncles.subList(0, miningConfig.getUncleListLimit());
        }

        Coin minimumGasPrice = minimumGasPriceCalculator.calculate(newBlockParent.getMinimumGasPrice());

        final List<Transaction> txsToRemove = new ArrayList<>();
        final List<Transaction> txs = getTransactions(txsToRemove, newBlockParent, minimumGasPrice);
        final Block newBlock = createBlock(mainchain, uncles, txs, minimumGasPrice, extraData);

        removePendingTransactions(txsToRemove);
        executor.executeAndFill(newBlock, newBlockParent.getHeader());
        return newBlock;
    }

    private List<Transaction> getTransactions(List<Transaction> txsToRemove, Block parent, Coin minGasPrice) {
        logger.debug("getting transactions from pending state");
        List<Transaction> txs = minerUtils.getAllTransactions(transactionPool);
        logger.debug("{} transaction(s) collected from pending state", txs.size());

        Transaction remascTx = new RemascTransaction(parent.getNumber() + 1);
        txs.add(remascTx);

        Map<RskAddress, BigInteger> accountNonces = new HashMap<>();

        Repository originalRepo = repository.getSnapshotTo(stateRootHandler.translate(parent.getHeader()).getBytes());

        return minerUtils.filterTransactions(txsToRemove, txs, accountNonces, originalRepo, minGasPrice);
    }

    private void removePendingTransactions(List<Transaction> transactions) {
        transactionPool.removeTransactions(transactions);
    }

    private Block createBlock(
            List<Block> mainchain,
            List<BlockHeader> uncles,
            List<Transaction> txs,
            Coin minimumGasPrice,
            byte[] extraData) {
        final BlockHeader newHeader = createHeader(mainchain, uncles, txs, minimumGasPrice, extraData);
        final Block newBlock = blockFactory.newBlock(newHeader, txs, uncles, false);
        return validationRules.isValid(newBlock) ? newBlock : blockFactory.newBlock(newHeader, txs, Collections.emptyList(), false);
    }

    private BlockHeader createHeader(
            List<Block> mainchain,
            List<BlockHeader> uncles,
            List<Transaction> txs,
            Coin minimumGasPrice,
            byte[] extraData) {
        final byte[] unclesListHash = HashUtil.keccak256(BlockHeader.getUnclesEncodedEx(uncles));

        Block newBlockParent = mainchain.get(0);
        final long timestampSeconds = clock.calculateTimestampForChild(newBlockParent);

        // Set gas limit before executing block
        BigInteger minGasLimit = BigInteger.valueOf(miningConfig.getGasLimit().getMininimum());
        BigInteger targetGasLimit = BigInteger.valueOf(miningConfig.getGasLimit().getTarget());
        BigInteger parentGasLimit = new BigInteger(1, newBlockParent.getGasLimit());
        BigInteger gasUsed = BigInteger.valueOf(newBlockParent.getGasUsed());
        boolean forceLimit = miningConfig.getGasLimit().isTargetForced();
        BigInteger gasLimit = gasLimitCalculator.calculateBlockGasLimit(parentGasLimit,
                                                                        gasUsed, minGasLimit, targetGasLimit, forceLimit);

        long blockNumber = newBlockParent.getNumber() + 1;
        final BlockHeader newHeader = blockFactory.newHeader(
                newBlockParent.getHash().getBytes(),
                unclesListHash,
                miningConfig.getCoinbaseAddress().getBytes(),
                EMPTY_TRIE_HASH,
                BlockHashesHelper.getTxTrieRoot(
                        txs, activationConfig.isActive(ConsensusRule.RSKIP126, blockNumber)
                ),
                EMPTY_TRIE_HASH,
                new Bloom().getData(),
                new byte[]{1},
                blockNumber,
                gasLimit.toByteArray(),
                0,
                timestampSeconds,
                extraData,
                Coin.ZERO,
                new byte[]{},
                new byte[]{},
                new byte[]{},
                minimumGasPrice.getBytes(),
                uncles.size()
        );
        newHeader.setDifficulty(difficultyCalculator.calcDifficulty(newHeader, newBlockParent.getHeader()));
        return newHeader;
    }
}

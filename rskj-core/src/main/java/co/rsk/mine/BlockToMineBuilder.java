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
import co.rsk.core.BlockDifficulty;
import co.rsk.core.Coin;
import co.rsk.core.DifficultyCalculator;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.BlockExecutor;
import co.rsk.core.bc.BlockHashesHelper;
import co.rsk.core.bc.BlockResult;
import co.rsk.core.bc.FamilyUtils;
import co.rsk.db.RepositoryLocator;
import co.rsk.db.RepositorySnapshot;
import co.rsk.panic.PanicProcessor;
import co.rsk.remasc.RemascTransaction;
import co.rsk.validators.BlockValidationRule;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.*;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.BlockStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.math.BigInteger;
import java.util.*;

/**
 * This component helps build a new block to mine.
 * It can also be used to generate a new block from the pending state, which is useful
 * in places like Web3 with the 'pending' parameter.
 */
public class BlockToMineBuilder {
    private static final Logger logger = LoggerFactory.getLogger("blocktominebuilder");

    private final ActivationConfig activationConfig;
    private final MiningConfig miningConfig;
    private final RepositoryLocator repositoryLocator;
    private final BlockStore blockStore;
    private final TransactionPool transactionPool;
    private final DifficultyCalculator difficultyCalculator;
    private final GasLimitCalculator gasLimitCalculator;
    private final BlockValidationRule validationRules;
    private final MinerClock clock;
    private final BlockFactory blockFactory;
    private final BlockExecutor executor;
    private static final PanicProcessor panicProcessor = new PanicProcessor();

    private final MinimumGasPriceCalculator minimumGasPriceCalculator;
    private final MinerUtils minerUtils;

    private final ForkDetectionDataCalculator forkDetectionDataCalculator;

    private final SignatureCache signatureCache;

    public BlockToMineBuilder(
            ActivationConfig activationConfig,
            MiningConfig miningConfig,
            RepositoryLocator repositoryLocator,
            BlockStore blockStore,
            TransactionPool transactionPool,
            DifficultyCalculator difficultyCalculator,
            GasLimitCalculator gasLimitCalculator,
            ForkDetectionDataCalculator forkDetectionDataCalculator,
            BlockValidationRule validationRules,
            MinerClock clock,
            BlockFactory blockFactory,
            BlockExecutor blockExecutor,
            MinimumGasPriceCalculator minimumGasPriceCalculator,
            MinerUtils minerUtils,
            SignatureCache signatureCache) {
        this.activationConfig = Objects.requireNonNull(activationConfig);
        this.miningConfig = Objects.requireNonNull(miningConfig);
        this.repositoryLocator = Objects.requireNonNull(repositoryLocator);
        this.blockStore = Objects.requireNonNull(blockStore);
        this.transactionPool = Objects.requireNonNull(transactionPool);
        this.difficultyCalculator = Objects.requireNonNull(difficultyCalculator);
        this.gasLimitCalculator = Objects.requireNonNull(gasLimitCalculator);
        this.forkDetectionDataCalculator = Objects.requireNonNull(forkDetectionDataCalculator);
        this.validationRules = Objects.requireNonNull(validationRules);
        this.clock = Objects.requireNonNull(clock);
        this.blockFactory = blockFactory;
        this.executor = blockExecutor;
        this.minimumGasPriceCalculator = minimumGasPriceCalculator;
        this.minerUtils = minerUtils;
        this.signatureCache = signatureCache;
    }
    
    /**
     * Creates a pending block based on the parent block header. Pending block is temporary, not connected to the chain and
     * includes txs from the mempool.
     *
     * @param parentHeader block header a "pending" block is based on.
     *
     * @return "pending" block result.
     * */
    @Nonnull
    public BlockResult buildPending(@Nonnull BlockHeader parentHeader) {
        Coin minimumGasPrice = minimumGasPriceCalculator.calculate(parentHeader.getMinimumGasPrice());
        List<BlockHeader> uncles = getUnclesHeaders(parentHeader);
        List<Transaction> txs = getTransactions(new ArrayList<>(), parentHeader, minimumGasPrice);
        Block newBlock = createBlock(Collections.singletonList(parentHeader), uncles, txs, minimumGasPrice, null);

        return executor.executeAndFill(newBlock, parentHeader);
    }

    /**
     * Creates a new block to mine based on the previous mainchain blocks.
     *
     * @param mainchainHeaders last best chain blocks where 0 index is the best block and so on.
     * @param extraData extra data to pass to the block being built.
     */
    public BlockResult build(List<BlockHeader> mainchainHeaders, byte[] extraData) {
        BlockHeader newBlockParentHeader = mainchainHeaders.get(0);
        List<BlockHeader> uncles = getUnclesHeaders(newBlockParentHeader);

        Coin minimumGasPrice = minimumGasPriceCalculator.calculate(newBlockParentHeader.getMinimumGasPrice());

        final List<Transaction> txsToRemove = new ArrayList<>();
        final List<Transaction> txs = getTransactions(txsToRemove, newBlockParentHeader, minimumGasPrice);
        final Block newBlock = createBlock(mainchainHeaders, uncles, txs, minimumGasPrice, extraData);

        removePendingTransactions(txsToRemove);
        return executor.executeAndFill(newBlock, newBlockParentHeader);
    }

    private List<BlockHeader> getUnclesHeaders(BlockHeader newBlockParentHeader) {
        List<BlockHeader> uncles = FamilyUtils.getUnclesHeaders(
                blockStore,
                newBlockParentHeader.getNumber() + 1,
                newBlockParentHeader.getHash(),
                miningConfig.getUncleGenerationLimit()
        );

        if (uncles.size() > miningConfig.getUncleListLimit()) {
            uncles = uncles.subList(0, miningConfig.getUncleListLimit());
        }

        return uncles;
    }

    private List<Transaction> getTransactions(List<Transaction> txsToRemove, BlockHeader parentHeader, Coin minGasPrice) {
        logger.debug("getting transactions from pending state");
        List<Transaction> txs = minerUtils.getAllTransactions(transactionPool, signatureCache);
        logger.debug("{} transaction(s) collected from pending state", txs.size());

        final long blockNumber = parentHeader.getNumber() + 1;
        Transaction remascTx = new RemascTransaction(blockNumber);
        txs.add(remascTx);

        Map<RskAddress, BigInteger> accountNonces = new HashMap<>();

        RepositorySnapshot originalRepo = repositoryLocator.snapshotAt(parentHeader);

        final boolean isRskip252Enabled = activationConfig.isActive(ConsensusRule.RSKIP252, blockNumber);

        return minerUtils.filterTransactions(txsToRemove, txs, accountNonces, originalRepo, minGasPrice, isRskip252Enabled, signatureCache);
    }

    private void removePendingTransactions(List<Transaction> transactions) {
        transactionPool.removeTransactions(transactions);
    }

    @VisibleForTesting
    public Block createBlock(
            List<BlockHeader> mainchainHeaders,
            List<BlockHeader> uncles,
            List<Transaction> txs,
            Coin minimumGasPrice,
            byte[] extraData) {
        BlockHeader newHeader = createHeader(mainchainHeaders, uncles, txs, minimumGasPrice, extraData);
        Block newBlock = blockFactory.newBlock(newHeader, txs, uncles, false);

        // TODO(nacho): The validation rules should accept a list of uncles and we should never build invalid blocks.
        if (validationRules.isValid(newBlock)) {
            return newBlock;
        }

        // Some validation rule failed (all validations run are related with uncles rules),
        // log the panic, and create again the block without uncles to avoid fail abruptly.
        panicProcessor.panic("buildBlock", "some validation failed trying to create a new block");

        newHeader = createHeader(mainchainHeaders, Collections.emptyList(), txs, minimumGasPrice, extraData);
        return blockFactory.newBlock(newHeader, txs, Collections.emptyList(), false);
    }

    private BlockHeader createHeader(
            List<BlockHeader> mainchainHeaders,
            List<BlockHeader> uncles,
            List<Transaction> txs,
            Coin minimumGasPrice,
            byte[] extraData) {
        final byte[] unclesListHash = HashUtil.keccak256(BlockHeader.getUnclesEncodedEx(uncles));

        BlockHeader newBlockParentHeader = mainchainHeaders.get(0);
        final long timestampSeconds = clock.calculateTimestampForChild(newBlockParentHeader);

        // Set gas limit before executing block
        BigInteger minGasLimit = BigInteger.valueOf(miningConfig.getGasLimit().getMininimum());
        BigInteger targetGasLimit = BigInteger.valueOf(miningConfig.getGasLimit().getTarget());
        BigInteger parentGasLimit = new BigInteger(1, newBlockParentHeader.getGasLimit());
        BigInteger gasUsed = BigInteger.valueOf(newBlockParentHeader.getGasUsed());
        boolean forceLimit = miningConfig.getGasLimit().isTargetForced();
        BigInteger gasLimit = gasLimitCalculator.calculateBlockGasLimit(parentGasLimit,
                                                                        gasUsed, minGasLimit, targetGasLimit, forceLimit);
        byte[] forkDetectionData = forkDetectionDataCalculator.calculateWithBlockHeaders(mainchainHeaders);

        long blockNumber = newBlockParentHeader.getNumber() + 1;

        // ummRoot can not be set to a value yet since the UMM contracts are not yet implemented
        byte[] ummRoot = activationConfig.isActive(ConsensusRule.RSKIPUMM, blockNumber) ? new byte[0] : null;

        final BlockHeader newHeader = blockFactory
                .getBlockHeaderBuilder()
                .setParentHash(newBlockParentHeader.getHash().getBytes())
                .setUnclesHash(unclesListHash)
                .setCoinbase(miningConfig.getCoinbaseAddress())
                .setTxTrieRoot(BlockHashesHelper.getTxTrieRoot(
                        txs, activationConfig.isActive(ConsensusRule.RSKIP126, blockNumber)
                    )
                )
                .setDifficulty(BlockDifficulty.ONE)
                .setNumber(blockNumber)
                .setGasLimit(gasLimit.toByteArray())
                .setGasUsed(0)
                .setTimestamp(timestampSeconds)
                .setExtraData(extraData)
                .setMergedMiningForkDetectionData(forkDetectionData)
                .setMinimumGasPrice(minimumGasPrice)
                .setUncleCount(uncles.size())
                .setUmmRoot(ummRoot)
                .build();

        newHeader.setDifficulty(difficultyCalculator.calcDifficulty(newHeader, newBlockParentHeader));
        return newHeader;
    }
}

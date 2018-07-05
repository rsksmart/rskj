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

import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.config.RemascConfig;
import co.rsk.config.RskSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.SelectionRule;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.db.BlockStore;
import org.ethereum.vm.LogInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implements the actual Remasc distribution logic
 * @author Oscar Guindzberg
 */
public class Remasc {
    private static final Logger logger = LoggerFactory.getLogger(Remasc.class);

    private final RskSystemProperties config;
    private final Repository repository;
    private final BlockStore blockStore;
    private final RemascConfig remascConstants;
    private final Transaction executionTx;
    private final Block executionBlock;
    private final List<LogInfo> logs;

    private final RemascStorageProvider provider;
    private final RemascFeesPayer feesPayer;

    public Remasc(RskSystemProperties config, Repository repository, BlockStore blockStore, RemascConfig remascConstants, Transaction executionTx, RskAddress contractAddress, Block executionBlock, List<LogInfo> logs) {
        this.config = config;
        this.repository = repository;
        this.blockStore = blockStore;
        this.remascConstants = remascConstants;
        this.executionTx = executionTx;
        this.executionBlock = executionBlock;
        this.logs = logs;

        this.provider = new RemascStorageProvider(repository, contractAddress);
        this.feesPayer = new RemascFeesPayer(repository, contractAddress);
    }

    public void save() {
        provider.save();
    }

    /**
     * Returns the internal contract state.
     * @return the internal contract state.
     */
    public RemascState getStateForDebugging() {
        return new RemascState(this.provider.getRewardBalance(), this.provider.getBurnedBalance(), this.provider.getSiblings(), this.provider.getBrokenSelectionRule());
    }


    /**
     * Implements the actual Remasc distribution logic
     */
    void processMinersFees() throws IOException, BlockStoreException {
        if (!(executionTx instanceof RemascTransaction)) {
            //Detect
            // 1) tx to remasc that is not the latest tx in a block
            // 2) invocation to remasc from another contract (ie call opcode)
            throw new RemascInvalidInvocationException("Invoked Remasc outside last tx of the block");
        }

        // This is not necessary but maintained for consensus reasons before we do a network upgrade
        this.addNewSiblings();

        long blockNbr = executionBlock.getNumber();
        long processingBlockNumber = blockNbr - remascConstants.getMaturity();
        if (processingBlockNumber < 1 ) {
            logger.debug("First block has not reached maturity yet, current block is {}", blockNbr);
            return;
        }

        int uncleGenerationLimit = config.getBlockchainConfig().getCommonConstants().getUncleGenerationLimit();
        List<Block> descendantsBlocks = new ArrayList<>(uncleGenerationLimit);

        // this search can be optimized if have certainty that the execution block is not in a fork
        // larger than depth
        Block currentBlock = blockStore.getBlockByHashAndDepth(
                executionBlock.getParentHash().getBytes(),
                remascConstants.getMaturity() - 1 - uncleGenerationLimit
        );
        descendantsBlocks.add(currentBlock);

        for (int i = 0; i < uncleGenerationLimit - 1; i++) {
            currentBlock = blockStore.getBlockByHash(currentBlock.getParentHash().getBytes());
            descendantsBlocks.add(currentBlock);
        }

        // descendants are reversed because the original order to pay siblings is defined in the way
        // blocks are ordered in the blockchain (the same as were stored in remasc contract)
        Collections.reverse(descendantsBlocks);

        Block processingBlock = blockStore.getBlockByHash(currentBlock.getParentHash().getBytes());
        BlockHeader processingBlockHeader = processingBlock.getHeader();

        // Adds current block fees to accumulated rewardBalance
        Coin processingBlockReward = processingBlockHeader.getPaidFees();
        Coin rewardBalance = provider.getRewardBalance();
        rewardBalance = rewardBalance.add(processingBlockReward);
        provider.setRewardBalance(rewardBalance);

        if (processingBlockNumber - remascConstants.getSyntheticSpan() < 0 ) {
            logger.debug("First block has not reached maturity+syntheticSpan yet, current block is {}", executionBlock.getNumber());
            return;
        }

        // Takes from rewardBalance this block's height reward.
        Coin syntheticReward = rewardBalance.divide(BigInteger.valueOf(remascConstants.getSyntheticSpan()));
        rewardBalance = rewardBalance.subtract(syntheticReward);
        provider.setRewardBalance(rewardBalance);

        // Pay RSK labs cut
        Coin payToRskLabs = syntheticReward.divide(BigInteger.valueOf(remascConstants.getRskLabsDivisor()));
        feesPayer.payMiningFees(processingBlockHeader.getHash().getBytes(), payToRskLabs, remascConstants.getRskLabsAddress(), logs);
        syntheticReward = syntheticReward.subtract(payToRskLabs);

        RemascFederationProvider federationProvider = new RemascFederationProvider(config, repository, processingBlock);

        Coin payToFederation = syntheticReward.divide(BigInteger.valueOf(remascConstants.getFederationDivisor()));

        byte[] processingBlockHash = processingBlockHeader.getHash().getBytes();
        int nfederators = federationProvider.getFederationSize();
        Coin[] payAndRemainderToFederator = payToFederation.divideAndRemainder(BigInteger.valueOf(nfederators));
        Coin payToFederator = payAndRemainderToFederator[0];
        Coin restToLastFederator = payAndRemainderToFederator[1];
        Coin paidToFederation = Coin.ZERO;

        for (int k = 0; k < nfederators; k++) {
            RskAddress federatorAddress = federationProvider.getFederatorAddress(k);

            if (k == nfederators - 1 && restToLastFederator.compareTo(Coin.ZERO) > 0) {
                feesPayer.payMiningFees(processingBlockHash, payToFederator.add(restToLastFederator), federatorAddress, logs);
            } else {
                feesPayer.payMiningFees(processingBlockHash, payToFederator, federatorAddress, logs);
            }

            paidToFederation = paidToFederation.add(payToFederator);
        }

        syntheticReward = syntheticReward.subtract(payToFederation);

        List<Sibling> siblings = getSiblingsToReward(descendantsBlocks, processingBlockNumber);
        if (!siblings.isEmpty()) {
            // Block has siblings, reward distribution is more complex
            boolean previousBrokenSelectionRule = provider.getBrokenSelectionRule();
            this.payWithSiblings(processingBlockHeader, syntheticReward, siblings, previousBrokenSelectionRule);
            boolean brokenSelectionRule = SelectionRule.isBrokenSelectionRule(processingBlockHeader, siblings);
            provider.setBrokenSelectionRule(brokenSelectionRule);
        } else {
            if (provider.getBrokenSelectionRule()) {
                // broken selection rule, apply punishment, ie burn part of the reward.
                Coin punishment = syntheticReward.divide(BigInteger.valueOf(remascConstants.getPunishmentDivisor()));
                syntheticReward = syntheticReward.subtract(punishment);
                provider.setBurnedBalance(provider.getBurnedBalance().add(punishment));
            }
            feesPayer.payMiningFees(processingBlockHeader.getHash().getBytes(), syntheticReward, processingBlockHeader.getCoinbase(), logs);
            provider.setBrokenSelectionRule(Boolean.FALSE);
        }

        // This is not necessary but maintained for consensus reasons before we do a network upgrade
        this.removeUsedSiblings(processingBlockHeader);
    }


    /**
     * Remove siblings just processed if any
     */
    private void removeUsedSiblings(BlockHeader processingBlockHeader) {
        provider.getSiblings().remove(processingBlockHeader.getNumber());
    }

    /**
     * Saves uncles of the current block into the siblings map to use in the future for fee distribution
     */
    private void addNewSiblings() {
        // Add uncles of the execution block to the siblings map
        List<BlockHeader> uncles = executionBlock.getUncleList();
        if (uncles == null) {
            return;
        }

        for (BlockHeader uncleHeader : uncles) {
            List<Sibling> siblings = provider.getSiblings().get(uncleHeader.getNumber());
            if (siblings == null) {
                siblings = new ArrayList<>();
            }

            siblings.add(new Sibling(uncleHeader, executionBlock.getHeader().getCoinbase(), executionBlock.getNumber()));
            provider.getSiblings().put(uncleHeader.getNumber(), siblings);
        }
    }

    /**
     * Descendants included on the same chain as the processing block could include siblings
     * that should be rewarded when fees on this block are paid
     * @param descendants blocks in the same blockchain that may include rewarded siblings
     * @param blockNumber number of the block is looked for siblings
     * @return
     */
    private List<Sibling> getSiblingsToReward(List<Block> descendants, long blockNumber) {
        return descendants.stream()
                .flatMap(block -> block.getUncleList().stream()
                        .filter(header -> header.getNumber() == blockNumber)
                        .map(header -> new Sibling(header, block.getCoinbase(), block.getNumber()))
                )
                .collect(Collectors.toList());
    }

    /**
     * Pay the mainchain block miner, its siblings miners and the publisher miners
     */
    private void payWithSiblings(BlockHeader processingBlockHeader, Coin fullBlockReward, List<Sibling> siblings, boolean previousBrokenSelectionRule) {
        SiblingPaymentCalculator paymentCalculator = new SiblingPaymentCalculator(fullBlockReward, previousBrokenSelectionRule, siblings.size(), this.remascConstants);

        byte[] processingBlockHeaderHash = processingBlockHeader.getHash().getBytes();
        this.payPublishersWhoIncludedSiblings(processingBlockHeaderHash, siblings, paymentCalculator.getIndividualPublisherReward());
        provider.addToBurnBalance(paymentCalculator.getPublishersSurplus());

        provider.addToBurnBalance(paymentCalculator.getMinersSurplus());

        this.payIncludedSiblings(processingBlockHeaderHash, siblings, paymentCalculator.getIndividualMinerReward());
        if (previousBrokenSelectionRule) {
            provider.addToBurnBalance(paymentCalculator.getPunishment().multiply(BigInteger.valueOf(siblings.size() + 1L)));
        }

        // Pay to main chain block miner
        feesPayer.payMiningFees(processingBlockHeaderHash, paymentCalculator.getIndividualMinerReward(), processingBlockHeader.getCoinbase(), logs);
    }

    private void payPublishersWhoIncludedSiblings(byte[] blockHash, List<Sibling> siblings, Coin minerReward) {
        for (Sibling sibling : siblings) {
            feesPayer.payMiningFees(blockHash, minerReward, sibling.getIncludedBlockCoinbase(), logs);
        }
    }

    private void payIncludedSiblings(byte[] blockHash, List<Sibling> siblings, Coin topReward) {
        long perLateBlockPunishmentDivisor = remascConstants.getLateUncleInclusionPunishmentDivisor();
        for (Sibling sibling : siblings) {
            long processingBlockNumber = executionBlock.getNumber() - remascConstants.getMaturity();
            long numberOfBlocksLate = sibling.getIncludedHeight() - processingBlockNumber - 1L;
            Coin lateInclusionPunishment = topReward.multiply(BigInteger.valueOf(numberOfBlocksLate)).divide(BigInteger.valueOf(perLateBlockPunishmentDivisor));
            feesPayer.payMiningFees(blockHash, topReward.subtract(lateInclusionPunishment), sibling.getCoinbase(), logs);
            provider.addToBurnBalance(lateInclusionPunishment);
        }
    }

}


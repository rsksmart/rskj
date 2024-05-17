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

import co.rsk.peg.constants.BridgeConstants;
import co.rsk.config.RemascConfig;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.SelectionRule;
import co.rsk.peg.federation.FederationStorageProvider;
import co.rsk.peg.federation.FederationStorageProviderImpl;
import co.rsk.peg.federation.FederationSupport;
import co.rsk.peg.federation.FederationSupportImpl;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.storage.BridgeStorageAccessorImpl;
import co.rsk.peg.storage.StorageAccessor;
import co.rsk.rpc.modules.trace.ProgramSubtrace;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.db.BlockStore;
import org.ethereum.vm.LogInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implements the actual Remasc distribution logic
 * @author Oscar Guindzberg
 */
public class Remasc {
    private static final Logger logger = LoggerFactory.getLogger(Remasc.class);

    private final Constants constants;
    private final ActivationConfig.ForBlock activations;
    private final Repository repository;
    private final BlockStore blockStore;
    private final RemascConfig remascConstants;
    private final Transaction executionTx;
    private final Block executionBlock;
    private final List<LogInfo> logs;

    private final RemascStorageProvider provider;
    private final RemascFeesPayer feesPayer;

    public Remasc(
            Constants constants,
            ActivationConfig activationConfig,
            Repository repository,
            BlockStore blockStore,
            RemascConfig remascConstants,
            Transaction executionTx,
            RskAddress contractAddress,
            Block executionBlock,
            List<LogInfo> logs) {
        this.repository = repository;
        this.blockStore = blockStore;
        this.remascConstants = remascConstants;
        this.executionTx = executionTx;
        this.executionBlock = executionBlock;
        this.logs = logs;

        this.provider = new RemascStorageProvider(repository, contractAddress);
        this.feesPayer = new RemascFeesPayer(repository, contractAddress);
        this.constants = constants;
        this.activations = activationConfig.forBlock(executionBlock.getNumber());
    }

    public void save() {
        provider.save();
    }

    public List<ProgramSubtrace> getSubtraces() {
        return this.feesPayer.getSubtraces();
    }

    /**
     * Returns the internal contract state.
     * @return the internal contract state.
     */
    public RemascState getStateForDebugging() {
        return new RemascState(this.provider.getRewardBalance(), this.provider.getBurnedBalance(), this.provider.getBrokenSelectionRule());
    }


    /**
     * Implements the actual Remasc distribution logic
     */
    void processMinersFees() {
        if (!(executionTx instanceof RemascTransaction)) {
            //Detect
            // 1) tx to remasc that is not the latest tx in a block
            // 2) invocation to remasc from another contract (ie call opcode)
            throw new RemascInvalidInvocationException("Invoked Remasc outside last tx of the block");
        }

        long executionBlockNumber = executionBlock.getNumber();

        long candidateBlockNumberToReward = executionBlockNumber - remascConstants.getMaturity();
        if (candidateBlockNumberToReward < 1 ) {
            logger.debug("First block has not reached maturity yet, current block is {}", executionBlockNumber);
            return;
        }

        int uncleGenerationLimit = constants.getUncleGenerationLimit();
        Deque<Map<Long, List<Sibling>>> descendantsBlocks = new LinkedList<>();

        // this search is now optimized if have certainty that the execution block is not in a fork
        // larger than depth. The optimized algorithm already covers this case
        Block currentBlock = blockStore.getBlockAtDepthStartingAt(
                remascConstants.getMaturity() - 1 - uncleGenerationLimit,
                executionBlock.getParentHash().getBytes()
        );

        descendantsBlocks.push(blockStore.getSiblingsFromBlockByHash(currentBlock.getHash()));

        // descendants are stored in reverse order because the original order to pay siblings is defined in the way
        // blocks are ordered in the blockchain (the same as were stored in remasc contract)
        for (int i = 0; i < uncleGenerationLimit - 1; i++) {
            currentBlock = blockStore.getBlockByHash(currentBlock.getParentHash().getBytes());
            descendantsBlocks.push(blockStore.getSiblingsFromBlockByHash(currentBlock.getHash()));
        }

        Block processingBlock = blockStore.getBlockByHash(currentBlock.getParentHash().getBytes());
        BlockHeader processingBlockHeader = processingBlock.getHeader();

        // Adds current block fees to accumulated rewardBalance
        Coin processingBlockReward = processingBlockHeader.getPaidFees();
        Coin rewardBalance = provider.getRewardBalance();
        rewardBalance = rewardBalance.add(processingBlockReward);
        provider.setRewardBalance(rewardBalance);

        if (candidateBlockNumberToReward - remascConstants.getSyntheticSpan() < 0 ) {
            logger.debug("First block has not reached maturity+syntheticSpan yet, current block is {}", executionBlock.getNumber());
            return;
        }

        List<Sibling> siblings = getSiblingsToReward(descendantsBlocks, candidateBlockNumberToReward);
        boolean previousBrokenSelectionRule = provider.getBrokenSelectionRule();
        boolean brokenSelectionRule = SelectionRule.isBrokenSelectionRule(processingBlockHeader, siblings);
        provider.setBrokenSelectionRule(!siblings.isEmpty() && brokenSelectionRule);

        // Takes from rewardBalance this block's height reward.
        Coin syntheticReward = rewardBalance.divide(BigInteger.valueOf(remascConstants.getSyntheticSpan()));

        if (shouldRewardBeAboveMinimumPayableGas()) {
            BigInteger minimumPayableGas = constants.getMinimumPayableGas();
            Coin minPayableFees = executionBlock.getMinimumGasPrice().multiply(minimumPayableGas);
            if (syntheticReward.compareTo(minPayableFees) < 0) {
                logger.debug("Synthetic Reward: {} is lower than minPayableFees: {} at block: {}",
                             syntheticReward, minPayableFees, executionBlock.getPrintableHash());
                return;
            }
        }

        rewardBalance = rewardBalance.subtract(syntheticReward);
        provider.setRewardBalance(rewardBalance);

        // Pay RSK labs cut
        RskAddress rskLabsAddress = getRskLabsAddress();
        Coin payToRskLabs = syntheticReward.divide(BigInteger.valueOf(remascConstants.getRskLabsDivisor()));
        feesPayer.payMiningFees(processingBlockHeader.getHash().getBytes(), payToRskLabs, rskLabsAddress, logs);
        syntheticReward = syntheticReward.subtract(payToRskLabs);
        Coin payToFederation = payToFederation(constants, processingBlock, syntheticReward);
        syntheticReward = syntheticReward.subtract(payToFederation);

        if (!siblings.isEmpty()) {
            // Block has siblings, reward distribution is more complex
            this.payWithSiblings(processingBlockHeader, syntheticReward, siblings, previousBrokenSelectionRule);
        } else {
            if (previousBrokenSelectionRule) {
                // broken selection rule, apply punishment, ie burn part of the reward.
                Coin punishment = syntheticReward.divide(BigInteger.valueOf(remascConstants.getPunishmentDivisor()));
                syntheticReward = syntheticReward.subtract(punishment);
                provider.setBurnedBalance(provider.getBurnedBalance().add(punishment));
            }
            feesPayer.payMiningFees(processingBlockHeader.getHash().getBytes(), syntheticReward, processingBlockHeader.getCoinbase(), logs);
        }
    }

    private boolean shouldRewardBeAboveMinimumPayableGas() {
        return activations.isActive(ConsensusRule.RSKIP85);
    }

    RskAddress getRskLabsAddress() {
        boolean isRskip218Enabled = activations.isActive(ConsensusRule.RSKIP218);
        return isRskip218Enabled ? remascConstants.getRskLabsAddressRskip218() : remascConstants.getRskLabsAddress();
    }

    private Coin payToFederation(Constants constants, Block processingBlock, Coin syntheticReward) {
        BridgeConstants bridgeConstants = constants.getBridgeConstants();
        StorageAccessor bridgeStorageAccessor = new BridgeStorageAccessorImpl(repository);

        FederationSupport federationSupport = newFederationSupportInstance(bridgeStorageAccessor, bridgeConstants, processingBlock, activations);

        RemascFederationProvider federationProvider = new RemascFederationProvider(activations, federationSupport);
        Coin federationReward = syntheticReward.divide(BigInteger.valueOf(remascConstants.getFederationDivisor()));

        Coin payToFederation = provider.getFederationBalance().add(federationReward);
        byte[] processingBlockHash = processingBlock.getHeader().getHash().getBytes();
        int nfederators = federationProvider.getFederationSize();
        Coin[] payAndRemainderToFederator = payToFederation.divideAndRemainder(BigInteger.valueOf(nfederators));
        Coin payToFederator = payAndRemainderToFederator[0];
        Coin restToLastFederator = payAndRemainderToFederator[1];

        if (shouldRewardBeAboveMinimumPayableGas()) {
            BigInteger minimumFederatorPayableGas = constants.getFederatorMinimumPayableGas();
            Coin minPayableFederatorFees = executionBlock.getMinimumGasPrice().multiply(minimumFederatorPayableGas);
            if (payToFederator.compareTo(minPayableFederatorFees) < 0) {
                provider.setFederationBalance(payToFederation);
                return federationReward;
            } else { // balance goes to zero because all federation balance will be distributed
                provider.setFederationBalance(Coin.ZERO);
            }
        }

        for (int k = 0; k < nfederators; k++) {
            RskAddress federatorAddress = federationProvider.getFederatorAddress(k);

            if (k == nfederators - 1 && restToLastFederator.compareTo(Coin.ZERO) > 0) {
                feesPayer.payMiningFees(processingBlockHash, payToFederator.add(restToLastFederator), federatorAddress, logs);
            } else {
                feesPayer.payMiningFees(processingBlockHash, payToFederator, federatorAddress, logs);
            }

        }

        return federationReward;
    }

    private FederationSupport newFederationSupportInstance(StorageAccessor bridgeStorageAccessor, BridgeConstants bridgeConstants, Block rskExecutionBlock, ActivationConfig.ForBlock activations) {
        FederationConstants federationConstants = bridgeConstants.getFederationConstants();
        FederationStorageProvider federationStorageProvider = new FederationStorageProviderImpl(bridgeStorageAccessor);
        return new FederationSupportImpl(federationConstants, federationStorageProvider, rskExecutionBlock, activations);
    }

    /**
     * Descendants included on the same chain as the processing block could include siblings
     * that should be rewarded when fees on this block are paid
     * @param descendants blocks in the same blockchain that may include rewarded siblings
     * @param blockNumber number of the block is looked for siblings
     * @return
     */
    private List<Sibling> getSiblingsToReward(Deque<Map<Long, List<Sibling>>> descendants, long blockNumber) {
        return descendants.stream()
                .flatMap(map -> map.getOrDefault(blockNumber, Collections.emptyList()).stream())
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


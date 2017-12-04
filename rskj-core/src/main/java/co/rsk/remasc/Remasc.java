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
import co.rsk.core.bc.SelectionRule;
import org.apache.commons.collections4.CollectionUtils;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.db.BlockStore;
import org.ethereum.db.RepositoryTrack;

import org.ethereum.util.BIUtil;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.LogInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Implements the actual Remasc distribution logic
 * @author Oscar Guindzberg
 */
public class Remasc {
    private static final Logger logger = LoggerFactory.getLogger(Remasc.class);

    private RemascConfig remascConstants;
    private RemascStorageProvider provider;

    private final Transaction executionTx;
    private Repository repository;


    private Block executionBlock;
    private BlockStore blockStore;

    private RemascFeesPayer feesPayer;

    private List<LogInfo> logs;

    Remasc(Transaction executionTx, Repository repository, String contractAddress, Block executionBlock, BlockStore blockStore, RemascConfig remascConstants, List<LogInfo> logs) {
        this.executionTx = executionTx;
        this.repository = repository;
        this.executionBlock = executionBlock;
        this.blockStore = blockStore;
        this.remascConstants = remascConstants;
        this.provider = new RemascStorageProvider(repository, contractAddress);
        this.logs = logs;
        this.feesPayer = new RemascFeesPayer(repository, contractAddress);
        this.repository = repository;
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
        this.addNewSiblings();

        long blockNbr = executionBlock.getNumber();

        long processingBlockNumber = blockNbr - remascConstants.getMaturity();
        if (processingBlockNumber < 1 ) {
            logger.debug("First block has not reached maturity yet, current block is {}", blockNbr);
            return;
        }
        BlockHeader processingBlockHeader = blockStore.getBlockByHashAndDepth(executionBlock.getParentHash(), remascConstants.getMaturity() - 1).getHeader();
        // Adds current block fees to accumulated rewardBalance
        BigInteger processingBlockReward = BigInteger.valueOf(processingBlockHeader.getPaidFees());
        BigInteger rewardBalance = provider.getRewardBalance();
        rewardBalance = rewardBalance.add(processingBlockReward);
        provider.setRewardBalance(rewardBalance);

        if (processingBlockNumber - remascConstants.getSyntheticSpan() < 0 ) {
            logger.debug("First block has not reached maturity+syntheticSpan yet, current block is {}", executionBlock.getNumber());
            return;
        }

        // Takes from rewardBalance this block's height reward.
        BigInteger fullBlockReward = rewardBalance.divide(BigInteger.valueOf(remascConstants.getSyntheticSpan()));
        rewardBalance = rewardBalance.subtract(fullBlockReward);
        provider.setRewardBalance(rewardBalance);

        // Pay RSK labs cut
        BigInteger payToRskLabs = fullBlockReward.divide(BigInteger.valueOf(remascConstants.getRskLabsDivisor()));
        feesPayer.payMiningFees(processingBlockHeader.getHash(), payToRskLabs, remascConstants.getRskLabsAddress(), logs);
        fullBlockReward = fullBlockReward.subtract(payToRskLabs);

        // TODO to improve
        // this type choreography is only needed because the RepositoryTrack support the
        // get snapshot to method
        Repository processingRepository = ((RepositoryTrack)repository).getOriginRepository().getSnapshotTo(processingBlockHeader.getStateRoot());
        // TODO to improve
        // and we need a RepositoryTrack to feed RemascFederationProvider
        // because it supports the update of bytes (notably, RepositoryImpl don't)
        // the update of bytes is needed, because BridgeSupport creation could alter
        // the storage when getChainHead is null (specially in production)
        processingRepository = processingRepository.startTracking();
        RemascFederationProvider federationProvider = new RemascFederationProvider(processingRepository);

        BigInteger payToFederation = fullBlockReward.divide(BigInteger.valueOf(remascConstants.getFederationDivisor()));

        byte[] processingBlockHash = processingBlockHeader.getHash();
        int nfederators = federationProvider.getFederationSize();
        BigInteger payToFederator = payToFederation.divide(BigInteger.valueOf(nfederators));
        BigInteger restToLastFederator = payToFederation.subtract(payToFederator.multiply(BigInteger.valueOf(nfederators)));
        BigInteger paidToFederation = BigInteger.ZERO;

        for (int k = 0; k < nfederators; k++) {
            byte[] federatorAddress = federationProvider.getFederatorAddress(k);

            if (k == nfederators - 1 && restToLastFederator.compareTo(BigInteger.ZERO) > 0)
                feesPayer.payMiningFees(processingBlockHash, payToFederator.add(restToLastFederator), federatorAddress, logs);
            else
                feesPayer.payMiningFees(processingBlockHash, payToFederator, federatorAddress, logs);

            paidToFederation = paidToFederation.add(payToFederator);
        }

        fullBlockReward = fullBlockReward.subtract(payToFederation);

        List<Sibling> siblings = provider.getSiblings().get(processingBlockNumber);

        if (CollectionUtils.isNotEmpty(siblings)) {
            // Block has siblings, reward distribution is more complex
            boolean previousBrokenSelectionRule = provider.getBrokenSelectionRule();
            this.payWithSiblings(processingBlockHeader, fullBlockReward, siblings, previousBrokenSelectionRule);
            boolean brokenSelectionRule = SelectionRule.isBrokenSelectionRule(processingBlockHeader, siblings);
            provider.setBrokenSelectionRule(brokenSelectionRule);
        } else {
            if (provider.getBrokenSelectionRule()) {
                // broken selection rule, apply punishment, ie burn part of the reward.
                BigInteger punishment = fullBlockReward.divide(BigInteger.valueOf(remascConstants.getPunishmentDivisor()));
                fullBlockReward = fullBlockReward.subtract(punishment);
                provider.setBurnedBalance(provider.getBurnedBalance().add(punishment));
            }
            feesPayer.payMiningFees(processingBlockHeader.getHash(), fullBlockReward, processingBlockHeader.getCoinbase(), logs);
            provider.setBrokenSelectionRule(Boolean.FALSE);
        }

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
        if (CollectionUtils.isNotEmpty(uncles)) {
            for (BlockHeader uncleHeader : uncles) {
                List<Sibling> siblings = provider.getSiblings().get(uncleHeader.getNumber());
                if (siblings == null)
                    siblings = new ArrayList<>();
                siblings.add(new Sibling(uncleHeader, executionBlock.getHeader().getCoinbase(), executionBlock.getNumber()));
                provider.getSiblings().put(uncleHeader.getNumber(), siblings);
            }
        }
    }

    /**
     * Pay the mainchain block miner, its siblings miners and the publisher miners
     */
    private void payWithSiblings(BlockHeader processingBlockHeader, BigInteger fullBlockReward, List<Sibling> siblings, boolean previousBrokenSelectionRule) {
        SiblingPaymentCalculator paymentCalculator = new SiblingPaymentCalculator(fullBlockReward, previousBrokenSelectionRule, siblings.size(), this.remascConstants);

        byte[] processingBlockHeaderHash = processingBlockHeader.getHash();
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

    private void payPublishersWhoIncludedSiblings(byte[] blockHash, List<Sibling> siblings, BigInteger minerReward) {
        for (Sibling sibling : siblings) {
            feesPayer.payMiningFees(blockHash, minerReward, sibling.getIncludedBlockCoinbase(), logs);
        }
    }

    private void payIncludedSiblings(byte[] blockHash, List<Sibling> siblings, BigInteger topReward) {
        long perLateBlockPunishmentDivisor = remascConstants.getLateUncleInclusionPunishmentDivisor();
        for (Sibling sibling : siblings) {
            long processingBlockNumber = executionBlock.getNumber() - remascConstants.getMaturity();
            long numberOfBlocksLate = sibling.getIncludedHeight() - processingBlockNumber - 1L;
            BigInteger lateInclusionPunishment = topReward.multiply(BigInteger.valueOf(numberOfBlocksLate)).divide(BigInteger.valueOf(perLateBlockPunishmentDivisor));
            feesPayer.payMiningFees(blockHash, topReward.subtract(lateInclusionPunishment), sibling.getCoinbase(), logs);
            provider.addToBurnBalance(lateInclusionPunishment);
        }
    }

    private void transfer(byte[] toAddr, BigInteger value) {
        BIUtil.transfer(repository, Hex.decode(PrecompiledContracts.REMASC_ADDR), toAddr, value);
    }
}


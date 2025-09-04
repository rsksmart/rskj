/*
 * This file is part of RskJ
 * Copyright (C) 2025 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

package co.rsk.validators;

import co.rsk.core.SuperDifficultyCalculator;
import co.rsk.core.bc.FamilyUtils;
import co.rsk.core.bc.SuperBlockFields;
import co.rsk.core.bc.SuperBridgeEventResolver;
import co.rsk.core.types.bytes.Bytes;
import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang3.tuple.Pair;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

public class SuperBlockRule implements BlockHeaderValidationRule, BlockValidationRule {
    private static final Logger logger = LoggerFactory.getLogger(SuperBlockRule.class);

    private final ActivationConfig activationConfig;
    private final BlockStore blockStore;
    private final ReceiptStore receiptStore;
    private final SuperDifficultyCalculator superDifficultyCalculator;

    public SuperBlockRule(ActivationConfig activationConfig, BlockStore blockStore, ReceiptStore receiptStore, SuperDifficultyCalculator superDifficultyCalculator) {
        this.activationConfig = activationConfig;
        this.blockStore = blockStore;
        this.receiptStore = receiptStore;
        this.superDifficultyCalculator = superDifficultyCalculator;
    }

    @Override
    public boolean isValid(BlockHeader header) {
        if (isRSKIP481Active(header.getNumber())) {
            return header.getSuperChainDataHash() != null;
        }

        return header.getSuperChainDataHash() == null;
    }

    @Override
    public boolean isValid(Block block) {
        BlockHeader header = block.getHeader();
        if (!isValid(header)) {
            return false;
        }

        SuperBlockFields superBlockFields = block.getSuperBlockFields();
        Optional<Boolean> isSuperOpt = block.isSuper();
        if (!isRSKIP481Active(block.getNumber())) {
            return superBlockFields == null && isSuperOpt.isPresent() && !isSuperOpt.get();
        } else if (isSuperOpt.isEmpty()) {
            return superBlockFields == null || areSuperBlockFieldsValid(block, superBlockFields);
        } else if (isSuperOpt.get()) {
            return superBlockFields != null && areSuperBlockFieldsValid(block, superBlockFields);
        } else {
            return superBlockFields == null;
        }
    }

    @VisibleForTesting
    protected boolean areSuperBlockFieldsValid(Block block, SuperBlockFields superBlockFields) {
        Pair<Block, List<Block>> superParentAndAncestors = FamilyUtils.findSuperParentAndAncestors(blockStore, block.getHeader());

        Block superParent = superParentAndAncestors.getLeft();
        List<Block> ancestors = superParentAndAncestors.getRight();

        if (superParent == null) {
            if (superBlockFields.getParentHash() != null || superBlockFields.getBlockNumber() != 0) {
                return false;
            }
        } else {
            if (superBlockFields.getParentHash() == null || !Bytes.equalByteSlices(superBlockFields.getParentHash(), Bytes.of(superParent.getHash().getBytes()))) {
                return false;
            }
            if (superBlockFields.getBlockNumber() != superParent.getSuperBlockFields().getBlockNumber() + 1) {
                return false;
            }

            final var calcSuperDifficulty = superDifficultyCalculator.calcSuperDifficulty(block, superParent);
            final var superDifficulty = superBlockFields.getSuperDifficulty();

            if (!superDifficulty.equals(calcSuperDifficulty)) {
                logger.error("#{}: superDifficulty != calcSuperDifficulty", block.getNumber());
                return false;
            }
        }

        byte[] bridgeEvent = SuperBridgeEventResolver.resolveSuperBridgeEvent(superParent);
        return SuperBridgeEventResolver.equalEvents(superBlockFields.getSuperBridgeEvent(), bridgeEvent);
    }

    private boolean isRSKIP481Active(long blockNumber) {
        return activationConfig.isActive(ConsensusRule.RSKIP481, blockNumber);
    }
}

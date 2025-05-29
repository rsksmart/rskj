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

import co.rsk.core.bc.BlockUtils;
import co.rsk.core.bc.FamilyUtils;
import co.rsk.core.bc.SuperBlockFields;
import co.rsk.core.bc.SuperBridgeEvent;
import co.rsk.core.types.bytes.Bytes;
import co.rsk.crypto.Keccak256;
import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang3.tuple.Pair;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SuperBlockRule implements BlockHeaderValidationRule, BlockValidationRule {

    private final ActivationConfig activationConfig;
    private final BlockStore blockStore;
    private final ReceiptStore receiptStore;

    public SuperBlockRule(ActivationConfig activationConfig, BlockStore blockStore, ReceiptStore receiptStore) {
        this.activationConfig = activationConfig;
        this.blockStore = blockStore;
        this.receiptStore = receiptStore;
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

    private boolean isSuperUncle(Keccak256 superParentHash, Map<Keccak256, Block> ancestorMap, BlockHeader uncleHeader) {
        if (!uncleHeader.isSuper().orElse(false)) {
            return false;
        }

        Keccak256 parentHash = uncleHeader.getParentHash();
        while (parentHash != null) {
            if (parentHash.equals(superParentHash)) {
                return true;
            }
            Block parent = ancestorMap.get(parentHash);
            if (parent == null) {
                return false;
            }

            parentHash = parent.getParentHash();
        }
        return false;
    }

    @VisibleForTesting
    protected boolean areSuperBlockFieldsValid(Block block, SuperBlockFields superBlockFields) {
        Pair<Block, List<Block>> superParentAndAncestors = FamilyUtils.findSuperParentAndAncestors(blockStore, block.getHeader());

        Block superParent = superParentAndAncestors.getLeft();
        List<Block> ancestors = superParentAndAncestors.getRight();

        List<BlockHeader> uncleList = superBlockFields.getUncleList();
        if (superParent == null) {
            if (superBlockFields.getParentHash() != null || superBlockFields.getBlockNumber() != 0) {
                return false;
            }
            if (!uncleList.isEmpty()) {
                return false;
            }
        } else {
            if (superBlockFields.getParentHash() == null || !Bytes.equalByteSlices(superBlockFields.getParentHash(), Bytes.of(superParent.getHash().getBytes()))) {
                return false;
            }
            if (superBlockFields.getBlockNumber() != superParent.getSuperBlockFields().getBlockNumber() + 1) {
                return false;
            }
            Map<Keccak256, Block> ancestorMap = ancestors.stream().collect(Collectors.toMap(Block::getHash, Function.identity()));
            for (BlockHeader blockHeader : uncleList) {
                if (!isSuperUncle(superParent.getHash(), ancestorMap, blockHeader)) {
                    return false;
                }
            }
        }

        SuperBridgeEvent bridgeEvent = SuperBridgeEvent.findEvent(
                Stream.concat(
                        BlockUtils.makeReceiptsStream(receiptStore, Collections.singletonList(block), SuperBridgeEvent.FILTER),
                        BlockUtils.makeReceiptsStream(receiptStore, ancestors, SuperBridgeEvent.FILTER)
                )
        );
        return SuperBridgeEvent.equalEvents(superBlockFields.getBridgeEvent(), bridgeEvent);
    }

    private boolean isRSKIP481Active(long blockNumber) {
        return activationConfig.isActive(ConsensusRule.RSKIP481, blockNumber);
    }
}

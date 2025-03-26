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

import co.rsk.core.bc.FamilyUtils;
import co.rsk.core.bc.BlockBundle;
import co.rsk.core.bc.SuperBlockFields;
import co.rsk.core.types.bytes.Bytes;
import co.rsk.crypto.Keccak256;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.db.BlockStore;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SuperBlockRule implements BlockHeaderValidationRule, BlockValidationRule {

    private final ActivationConfig activationConfig;
    private final BlockStore blockStore;

    public SuperBlockRule(ActivationConfig activationConfig, BlockStore blockStore) {
        this.activationConfig = activationConfig;
        this.blockStore = blockStore;
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
            return superBlockFields == null || areSuperBlockFieldsValid(header, superBlockFields);
        } else if (isSuperOpt.get()) {
            return superBlockFields != null && areSuperBlockFieldsValid(header, superBlockFields);
        } else {
            return superBlockFields == null;
        }
    }

    private boolean isSuperUncle(Keccak256 superParentHash, Map<Keccak256, BlockHeader> ancestors, BlockHeader uncleHeader) {
        if (!uncleHeader.isSuper().orElse(false)) {
            return false;
        }

        Keccak256 parentHash = uncleHeader.getParentHash();
        while (parentHash != null) {
            if (parentHash.equals(superParentHash)) {
                return true;
            }
            BlockHeader parent = ancestors.get(parentHash);
            if (parent == null) {
                return false;
            }

            parentHash = parent.getParentHash();
        }
        return false;
    }

    private boolean areSuperBlockFieldsValid(BlockHeader header, SuperBlockFields superBlockFields) {
        BlockBundle<Map<Keccak256, BlockHeader>> superParentAndAncestors = FamilyUtils.findSuperParentAndAncestors(blockStore, header);

        Block superParent = superParentAndAncestors.getBlock();
        Map<Keccak256, BlockHeader> ancestors = superParentAndAncestors.getBundle();

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
            for (BlockHeader blockHeader : uncleList) {
                if (!isSuperUncle(superParent.getHash(), ancestors, blockHeader)) {
                    return false;
                }
            }
        }

        // TODO: validate super bridge event

        return true;
    }

    private boolean isRSKIP481Active(long blockNumber) {
        return activationConfig.isActive(ConsensusRule.RSKIP481, blockNumber);
    }
}

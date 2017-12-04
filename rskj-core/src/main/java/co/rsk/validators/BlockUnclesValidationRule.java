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

package co.rsk.validators;

import co.rsk.core.bc.FamilyUtils;
import co.rsk.panic.PanicProcessor;
import org.apache.commons.collections4.CollectionUtils;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ByteArrayWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validate the uncles in a block.
 * It validates that the uncle root hash correspond with the uncle list
 * It calculates the already used uncles in the block ancestors
 * It calculates the ancestors of the block
 * It validates that the uncle list is not too large
 * It validates that each uncle
 * - is not an ancestor
 * - is not a used uncle
 * - has a common ancestor with the block
 *
 * @return true if the uncles in block are valid, false if not
 */

public class BlockUnclesValidationRule implements BlockValidationRule {

    private static final Logger logger = LoggerFactory.getLogger("blockvalidator");
    private static final PanicProcessor panicProcessor = new PanicProcessor();
    public static final String INVALIDUNCLE = "invaliduncle";

    private BlockStore blockStore;
    private int uncleListLimit;
    private int uncleGenerationLimit;
    private BlockValidationRule validations;
    private BlockParentDependantValidationRule parentValidations;
    private BlockUnclesHashValidationRule blockValidationRule;

    public BlockUnclesValidationRule(BlockStore blockStore, int uncleListLimit,
                                     int uncleGenerationLimit, BlockValidationRule validations,
                                     BlockParentDependantValidationRule parentValidations) {
        this.blockStore = blockStore;
        this.uncleListLimit = uncleListLimit;
        this.uncleGenerationLimit = uncleGenerationLimit;
        this.validations = validations;
        this.parentValidations = parentValidations;
        this.blockValidationRule = new BlockUnclesHashValidationRule();
    }

    @Override
    public boolean isValid(Block block) {

        if (!blockValidationRule.isValid(block))
            return false;

        List<BlockHeader> uncles = block.getUncleList();
        if (CollectionUtils.isNotEmpty(uncles) &&
                !validateUncleList(block.getNumber(), uncles,
                        FamilyUtils.getAncestors(blockStore, block, uncleGenerationLimit),
                        FamilyUtils.getUsedUncles(blockStore, block, uncleGenerationLimit))) {
            logger.warn("Uncles list validation failed");
            return false;
        }

        return true;
    }

    /**
     * Validate an uncle list.
     * It validates that the uncle list is not too large
     * It validates that each uncle
     * - is not an ancestor
     * - is not a used uncle
     * - has a common ancestor with the block
     *
     * @param blockNumber the number of the block containing the uncles
     * @param uncles      the uncle list to validate
     * @param ancestors   the list of direct ancestors of the block containing the uncles
     * @param used        used uncles
     * @return true if the uncles in the list are valid, false if not
     */
    public boolean validateUncleList(long blockNumber, List<BlockHeader> uncles, Set<ByteArrayWrapper> ancestors, Set<ByteArrayWrapper> used) {
        if (uncles.size() > uncleListLimit) {
            logger.error("Uncle list to big: block.getUncleList().size() > UNCLE_LIST_LIMIT");
            panicProcessor.panic(INVALIDUNCLE, "Uncle list to big: block.getUncleList().size() > UNCLE_LIST_LIMIT");
            return false;
        }

        Set<ByteArrayWrapper> hashes = new HashSet<>();

        for (BlockHeader uncle : uncles) {
            byte[] uhash = uncle.getHash();

            Block blockForUncleHeader = new Block(uncle);

            if (!this.validations.isValid(blockForUncleHeader)
                    || !validateParentNumber(uncle, blockNumber)) {
                return false;
            }

            ByteArrayWrapper uncleHash = new ByteArrayWrapper(uhash);

            /* Just checking that the uncle is not added twice */
            if (hashes.contains(uncleHash)) {
                return false;
            }
            hashes.add(uncleHash);

            if(!validateUnclesAncestors(ancestors, uncleHash) || !validateIfUncleWasNeverUsed(used, uncleHash)
                    || !validateUncleParent(ancestors, blockForUncleHeader)) {
                return false;
            }
        }
        return true;
    }

    private boolean validateParentNumber(BlockHeader uncle, long blockNumber) {
        boolean isSiblingOrDescendant = uncle.getNumber() >= blockNumber;

        if (isSiblingOrDescendant) {
            logger.error("Uncle is sibling or descendant");
            panicProcessor.panic(INVALIDUNCLE, "Uncle is sibling or descendant");
            return false;
        }

        // if uncle's parent's number is not less than currentBlock - UNCLE_GEN_LIMIT, mark invalid
        boolean isValid = (uncle.getNumber() - 1 >= (blockNumber - uncleGenerationLimit));

        if (!isValid) {
            logger.error("Uncle too old: generationGap must be under UNCLE_GENERATION_LIMIT");
            panicProcessor.panic(INVALIDUNCLE, "Uncle too old: generationGap must be under UNCLE_GENERATION_LIMIT");
            return false;
        }

        return true;
    }

    private boolean validateUnclesAncestors(Set<ByteArrayWrapper> ancestors, ByteArrayWrapper uncleHash) {
        if (ancestors != null && ancestors.contains(uncleHash)) {
            String uHashStr = uncleHash.toString();
            logger.error("Uncle is direct ancestor: {}", uHashStr);
            panicProcessor.panic(INVALIDUNCLE, String.format("Uncle is direct ancestor: %s", uHashStr));
            return false;
        }
        return true;
    }

    private boolean validateIfUncleWasNeverUsed(Set<ByteArrayWrapper> used, ByteArrayWrapper uncleHash) {
        String uhashString = uncleHash.toString();
        if (used != null && used.contains(uncleHash)) {
            logger.error("Uncle is not unique: {}", uhashString);
            panicProcessor.panic(INVALIDUNCLE, String.format("Uncle is not unique: %s", uhashString));
            return false;
        }
        return true;
    }

    private boolean validateUncleParent(Set<ByteArrayWrapper> ancestors, Block uncle) {
        String uhashString = Hex.toHexString(uncle.getHash());
        Block parent = blockStore.getBlockByHash(uncle.getParentHash());

        if (ancestors != null && (parent == null || !ancestors.contains(new ByteArrayWrapper(parent.getHash())))) {
            logger.error("Uncle has no common parent: {}", uhashString);
            panicProcessor.panic(INVALIDUNCLE, String.format("Uncle has no common parent: %s", uhashString));
            return false;
        }

        return this.parentValidations.isValid(uncle, parent);
    }
}

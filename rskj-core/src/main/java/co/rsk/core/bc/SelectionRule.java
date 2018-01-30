package co.rsk.core.bc;

import co.rsk.core.commons.Keccak256;
import co.rsk.remasc.Sibling;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;

import java.math.BigInteger;
import java.util.List;

public class SelectionRule {

    private static final BigInteger PAID_FEES_MULTIPLIER_CRITERIA = BigInteger.valueOf(2);

    public static boolean shouldWeAddThisBlock(
            BigInteger blockDifficulty,
            BigInteger currentDifficulty,
            Block block,
            Block currentBlock) {

        int compareDifficulties = blockDifficulty.compareTo(currentDifficulty);

        if (compareDifficulties > 0) {
            return true;
        }

        if (compareDifficulties < 0) {
            return false;
        }

        BigInteger pfm = currentBlock.getHeader().getPaidFees().multiply(PAID_FEES_MULTIPLIER_CRITERIA);
        // fees over PAID_FEES_MULTIPLIER_CRITERIA times higher
        if (block.getHeader().getPaidFees().compareTo(pfm) > 0) {
            return true;
        }

        BigInteger blockFeesCriteria = block.getHeader().getPaidFees().multiply(PAID_FEES_MULTIPLIER_CRITERIA);

        // As a last resort, choose the block with the lower hash. We ask that
        // the fees are at least bigger than the half of current block.
        return currentBlock.getHeader().getPaidFees().compareTo(blockFeesCriteria) < 0 &&
                isThisBlockHashSmaller(block.getHash(), currentBlock.getHash());
    }
    
    public static boolean isBrokenSelectionRule(
            BlockHeader processingBlockHeader, List<Sibling> siblings) {
        int maxUncleCount = 0;
        for (Sibling sibling : siblings) {
            maxUncleCount = Math.max(maxUncleCount, sibling.getUncleCount());
            BigInteger pfm = processingBlockHeader.getPaidFees().multiply(PAID_FEES_MULTIPLIER_CRITERIA);
            if (sibling.getPaidFees().compareTo(pfm) > 0) {
                return true;
            }
            BigInteger blockFeesCriteria = sibling.getPaidFees().multiply(PAID_FEES_MULTIPLIER_CRITERIA);
            if (processingBlockHeader.getPaidFees().compareTo(blockFeesCriteria) < 0 &&
                    isThisBlockHashSmaller(sibling.getHash(), processingBlockHeader.getHash())) {
                return true;
            }
        }
        return maxUncleCount > processingBlockHeader.getUncleCount();
    }

    public static boolean isThisBlockHashSmaller(Keccak256 thisBlockHash, Keccak256 compareBlockHash) {
        return thisBlockHash.compareTo(compareBlockHash) < 0;
    }
}

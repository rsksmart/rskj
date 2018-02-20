package co.rsk.core.bc;

import co.rsk.core.BlockDifficulty;
import co.rsk.core.Coin;
import co.rsk.remasc.Sibling;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.util.FastByteComparisons;

import java.math.BigInteger;
import java.util.List;

public class SelectionRule {

    private static final int BYTE_ARRAY_OFFSET = 0;
    private static final int BYTE_ARRAY_LENGTH = 32;

    private static final BigInteger PAID_FEES_MULTIPLIER_CRITERIA = BigInteger.valueOf(2);

    public static boolean shouldWeAddThisBlock(
            BlockDifficulty blockDifficulty,
            BlockDifficulty currentDifficulty,
            Block block,
            Block currentBlock) {

        int compareDifficulties = blockDifficulty.compareTo(currentDifficulty);

        if (compareDifficulties > 0) {
            return true;
        }

        if (compareDifficulties < 0) {
            return false;
        }

        Coin pfm = currentBlock.getHeader().getPaidFees().multiply(PAID_FEES_MULTIPLIER_CRITERIA);
        // fees over PAID_FEES_MULTIPLIER_CRITERIA times higher
        if (block.getHeader().getPaidFees().compareTo(pfm) > 0) {
            return true;
        }

        Coin blockFeesCriteria = block.getHeader().getPaidFees().multiply(PAID_FEES_MULTIPLIER_CRITERIA);

        // As a last resort, choose the block with the lower hash. We ask that
        // the fees are at least bigger than the half of current block.
        return currentBlock.getHeader().getPaidFees().compareTo(blockFeesCriteria) < 0 &&
                isThisBlockHashSmaller(block.getHash().getBytes(), currentBlock.getHash().getBytes());
    }
    
    public static boolean isBrokenSelectionRule(
            BlockHeader processingBlockHeader, List<Sibling> siblings) {
        int maxUncleCount = 0;
        for (Sibling sibling : siblings) {
            maxUncleCount = Math.max(maxUncleCount, sibling.getUncleCount());
            Coin pfm = processingBlockHeader.getPaidFees().multiply(PAID_FEES_MULTIPLIER_CRITERIA);
            if (sibling.getPaidFees().compareTo(pfm) > 0) {
                return true;
            }
            Coin blockFeesCriteria = sibling.getPaidFees().multiply(PAID_FEES_MULTIPLIER_CRITERIA);
            if (processingBlockHeader.getPaidFees().compareTo(blockFeesCriteria) < 0 &&
                    isThisBlockHashSmaller(sibling.getHash(), processingBlockHeader.getHash().getBytes())) {
                return true;
            }
        }
        return maxUncleCount > processingBlockHeader.getUncleCount();
    }

    public static boolean isThisBlockHashSmaller(byte[] thisBlockHash, byte[] compareBlockHash) {
        return FastByteComparisons.compareTo(
                thisBlockHash, BYTE_ARRAY_OFFSET, BYTE_ARRAY_LENGTH,
                compareBlockHash, BYTE_ARRAY_OFFSET, BYTE_ARRAY_LENGTH) < 0;
    }
}

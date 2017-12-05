package co.rsk.core.bc;

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

        // as a last resort, choose the block with the lower hash
        return isThisBlockHashSmaller(block.getHash(), currentBlock.getHash());
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
            if (isThisBlockHashSmaller(sibling.getHash(), processingBlockHeader.getHash())) {
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

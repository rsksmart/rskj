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

    private static final int PAID_FEES_MULTIPLIER_CRITERIA = 2;

    public static boolean shouldWeAddThisBlock(BigInteger blockDifficulty, BigInteger currentDifficulty,
        Block block, Block currentBlock) {
        int compareDifficulties = blockDifficulty.compareTo(currentDifficulty);

        if (compareDifficulties > 0) {
            return true;
        }

        if (compareDifficulties == 0) {
            if (block.getHeader().getPaidFees() >
                    PAID_FEES_MULTIPLIER_CRITERIA * currentBlock.getHeader().getPaidFees()
                    || FastByteComparisons.compareTo(
                            block.getHash(), BYTE_ARRAY_OFFSET, BYTE_ARRAY_LENGTH,
                            currentBlock.getHash(), BYTE_ARRAY_OFFSET, BYTE_ARRAY_LENGTH) < 0) {
                return true;
            }
        }
        return false;
    }
    
    public static boolean isBrokenSelectionRule(
            BlockHeader processingBlockHeader, List<Sibling> siblings) {
        int maxUncleCount = 0;
        for (Sibling sibling : siblings) {
            maxUncleCount = Math.max(maxUncleCount, sibling.getUncleCount());
            if (sibling.getPaidFees() > PAID_FEES_MULTIPLIER_CRITERIA
                    * processingBlockHeader.getPaidFees()) {
                return true;
            }
            if (FastByteComparisons.compareTo(sibling.getHash(), BYTE_ARRAY_OFFSET, BYTE_ARRAY_LENGTH,
                    processingBlockHeader.getHash(), BYTE_ARRAY_OFFSET, BYTE_ARRAY_LENGTH) < 0) {
                return true;
            }
        }
        return maxUncleCount > processingBlockHeader.getUncleCount()
    }
}

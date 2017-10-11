package co.rsk.core.bc;

import co.rsk.remasc.Sibling;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.util.FastByteComparisons;

import java.math.BigInteger;
import java.util.List;

public class SelectionRule {

    public static boolean shouldWeAddThisBlock(BigInteger blockDifficulty, BigInteger currentDifficulty,
        Block block, Block currentBlock) {
        int compareDifficulties = blockDifficulty.compareTo(currentDifficulty);

        if (compareDifficulties > 0) {
            return true;
        }

        if (compareDifficulties == 0) {
            if (block.getHeader().getPaidFees() > 2*currentBlock.getHeader().getPaidFees()
                    || FastByteComparisons.compareTo(block.getHash(), 0, 32,
                    currentBlock.getHash(), 0, 32) < 0) {
                return true;
            }
        }
        return false;
    }
    public static boolean isBrokenSelectionRule(
            BlockHeader processingBlockHeader, List<Sibling> siblings) {
        int maxUncleCount = 0;
        boolean paidFeesRule = false;
        boolean hashRule = false;
        for (Sibling sibling : siblings) {
            maxUncleCount = maxUncleCount<sibling.getUncleCount()?
                    sibling.getUncleCount():maxUncleCount;
            if (!paidFeesRule && sibling.getPaidFees() > 2
                    * processingBlockHeader.getPaidFees()) {
                paidFeesRule = true;
            }
            if (!hashRule && FastByteComparisons.compareTo(sibling.getHash(), 0, 32,
                    processingBlockHeader.getHash(), 0, 32) < 0) {
                hashRule = true;
            }
        }
        return maxUncleCount > processingBlockHeader.getUncleCount() ||
                paidFeesRule || hashRule;
    }
}

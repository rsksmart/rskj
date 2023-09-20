package co.rsk.bridge.bitcoin;

import co.rsk.bitcoinj.core.BtcBlock;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bridge.utils.MerkleTreeUtils;
import org.ethereum.util.Utils;

import java.util.Collections;
import java.util.List;

/**
 * Represents a branch of a merkle tree. Can be used
 * to validate that a certain transaction belongs
 * to a certain block.
 *
 * IMPORTANT: this class can only be used to validate
 * an existing merkle branch against a <block, transaction> pair.
 * It cannot be used to generate a merkle branch from a complete
 * block and a single transaction, since there's no use case
 * for it.
 *
 * @author Ariel Mendelzon
 */
public class MerkleBranch {
    private final List<Sha256Hash> hashes;
    private final int path;

    public MerkleBranch(List<Sha256Hash> hashes, int path) {
        this.hashes = Collections.unmodifiableList(hashes);
        this.path = path;

        //We validate that the number of hashes is uint8 as described in https://github.com/bitcoin/bips/blob/master/bip-0037.mediawiki
        if (hashes.size() > 32) {
            throw new IllegalArgumentException("The number of hashes can't be bigger than 255");
        }
        // We validate here that there are no more bits in the
        // path than those needed to reduce the branch to the
        // merkle root. That is, that the number of significant
        // bits is lower or equal to the number of hashes
        if (Utils.significantBitCount(path) > hashes.size()) {
            throw new IllegalArgumentException("The number of significant bits must be lower or equal to the number of hashes");
        }
    }

    public List<Sha256Hash> getHashes() {
        return hashes;
    }

    public int getPath() {
        return path;
    }
    
    /**
     * Returns true if and only if this
     * merkle branch successfully proves
     * that tx hash is included in block.
     *
     * @param txHash The transaction hash
     * @param block The BTC block
     * @return Whether this branch proves inclusion of tx in block.
     */
    public boolean proves(Sha256Hash txHash, BtcBlock block) {
        return block.getMerkleRoot().equals(reduceFrom(txHash));
    }

    /**
     * Given a transaction hash, this method traverses the path, calculating
     * the intermediate hashes and ultimately arriving at
     * the merkle root.
     *
     * @param txHash The transaction hash
     * @return The merkle root obtained from the traversal
     */
    public Sha256Hash reduceFrom(Sha256Hash txHash) {
        Sha256Hash current = txHash;
        int index = 0;
        while (index < hashes.size()) {
            boolean currentRight = ((path >> index) & 1) == 1;
            if (currentRight) {
                current = MerkleTreeUtils.combineLeftRight(hashes.get(index), current);
            } else {
                current = MerkleTreeUtils.combineLeftRight(current, hashes.get(index));
            }
            index++;
        }
        return current;
    }
}

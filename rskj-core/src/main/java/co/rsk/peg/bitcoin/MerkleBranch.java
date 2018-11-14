package co.rsk.peg.bitcoin;

import co.rsk.bitcoinj.core.BtcBlock;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.peg.exception.InvalidMerkleBranchException;
import org.ethereum.util.Utils;

import java.util.Collections;
import java.util.List;

import static co.rsk.bitcoinj.core.Utils.reverseBytes;

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
    private List<Sha256Hash> hashes;
    private int path;

    public MerkleBranch(List<Sha256Hash> hashes, int path) {
        this.hashes = Collections.unmodifiableList(hashes);
        this.path = path;

        // We validate here that there are no more bits in the
        // path than those needed to reduce the branch to the
        // merkle root. That is, that the number of significant
        // bits is lower of equal to the number of hashes
        if (Utils.significantBitCount(path) > hashes.size()) {
            throw new InvalidMerkleBranchException("The number of significant bits must be lower or equal to the number of hashes");
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
        byte index = 0;
        while (index < hashes.size()) {
            boolean currentRight = ((path >> index) & 1) == 1;
            if (currentRight) {
                current = combineLeftRight(hashes.get(index), current);
            } else {
                current = combineLeftRight(current, hashes.get(index));
            }
            index++;
        }

        return current;
    }

    /**
     * Combines two hashes (representing nodes in a merkle tree) to produce a single hash
     * that would be the parent of these two nodes.
     *
     * @param left The left hand side node bytes
     * @param right The right hand side node bytes
     * @return
     */
    private static Sha256Hash combineLeftRight(Sha256Hash left, Sha256Hash right) {
        return Sha256Hash.wrapReversed(Sha256Hash.hashTwice(
                reverseBytes(left.getBytes()), 0, 32,
                reverseBytes(right.getBytes()), 0, 32));
    }
}

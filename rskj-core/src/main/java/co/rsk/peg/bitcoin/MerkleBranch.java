package co.rsk.peg.bitcoin;

import co.rsk.bitcoinj.core.BtcBlock;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Sha256Hash;

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
    private List<byte[]> hashes;
    private int branch;

    // TODO: Need a method to build one of these
    // TODO: from an actual bitcoin merkle branch
    // TODO: message.
    // TODO: It is very important that this method
    // TODO: performs good validations on the input
    // TODO: to prevent corrupted input attacks.

    public MerkleBranch(List<byte[]> hashes, int branch) {
        this.hashes = Collections.unmodifiableList(hashes);
        this.branch = branch;
    }

    public List<byte[]> getHashes() {
        return hashes;
    }

    public int getBranch() {
        return branch;
    }

    /**
     * Returns true if and only if this
     * merkle branch successfully proves
     * that tx is included in block.
     *
     * @param tx The BTC transaction
     * @param block The BTC block
     * @return Whether this branch proves inclusion of tx in block.
     */
    public boolean proves(BtcTransaction tx, BtcBlock block) {
        return block.getMerkleRoot().equals(reduceFrom(tx));
    }

    /**
     * Given a BTC transaction, traverses the path, calculating
     * the intermediate hashes and ultimately arriving at
     * the merkle root.
     *
     * @param tx The BTC transaction
     * @return The merkle root obtained from the traversal
     */
    public Sha256Hash reduceFrom(BtcTransaction tx) {
        Sha256Hash current = tx.getHash();
        byte index = 0;
        while (index < hashes.size()) {
            boolean currentRight = ((branch >> index) & 1) == 1;
            if (currentRight) {
                current = combineLeftRight(hashes.get(index), current.getBytes());
            } else {
                current = combineLeftRight(current.getBytes(), hashes.get(index));
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
    private static Sha256Hash combineLeftRight(byte[] left, byte[] right) {
        return Sha256Hash.wrapReversed(Sha256Hash.hashTwice(
                reverseBytes(left), 0, 32,
                reverseBytes(right), 0, 32));
    }
}

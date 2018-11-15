package co.rsk.peg.utils;

import co.rsk.bitcoinj.core.Sha256Hash;

import static co.rsk.bitcoinj.core.Utils.reverseBytes;

public class MerkleTreeUtils {
    /**
     * Combines two hashes (representing nodes in a merkle tree) to produce a single hash
     * that would be the parent of these two nodes.
     *
     * @param left The left hand side node bytes
     * @param right The right hand side node bytes
     * @return
     */
    public static Sha256Hash combineLeftRight(Sha256Hash left, Sha256Hash right) {
        return Sha256Hash.wrapReversed(
                Sha256Hash.hashTwice(
                    reverseBytes(left.getBytes()), 0, 32,
                    reverseBytes(right.getBytes()), 0, 32
                )
        );
    }
}

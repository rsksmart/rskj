package co.rsk.bridge.utils;

import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.Utils;
import co.rsk.bitcoinj.core.VerificationException;

import static co.rsk.bitcoinj.core.Utils.reverseBytes;

public class MerkleTreeUtils {
    // coinbase to check is part of a valid 64 byte tx
    private static long COINBASE_CHECKED = Utils.readUint32(new byte[]{(byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF}, 0);

    private MerkleTreeUtils() {}

    /**
     * Combines two hashes (representing nodes in a merkle tree) to produce a single hash
     * that would be the parent of these two nodes.
     *
     * @param left The left hand side node bytes
     * @param right The right hand side node bytes
     * @return
     */
    public static Sha256Hash combineLeftRight(Sha256Hash left, Sha256Hash right) {
        byte[] leftBytes = left.getBytes();
        byte[] rightBytes = right.getBytes();
        checkNotAValid64ByteTransaction(leftBytes, rightBytes);
        return Sha256Hash.wrapReversed(
                Sha256Hash.hashTwice(
                    reverseBytes(leftBytes), 0, 32,
                    reverseBytes(rightBytes), 0, 32
                )
        );
    }


    /**
     * Checks supplied bytes DO NOT represent a valid bitcoin transaction.
     * Fixes attack described on https://bitslog.wordpress.com/2018/06/09/leaf-node-weakness-in-bitcoin-merkle-tree-design/
     * @throws VerificationException if bytes DO represent a valid bitcoin transaction.
     */
    private static void checkNotAValid64ByteTransaction(byte[] left, byte[] right) {
        byte[] leftAndRight = new byte[left.length + right.length];
        System.arraycopy(left, 0, leftAndRight, 0, 32);
        System.arraycopy(right, 0, leftAndRight, 32, 32);

        int _offset = 0;

        // Skip version
        _offset += 4;

        // Check inputs count
        byte inputCount = leftAndRight[_offset];
        _offset += 1;
        if (inputCount != 1) {
            // Only 1 input fits in 64 bytes
            return;
        }

        // Skip input 0 outpoint hash
        _offset += 32;

        // Check output 0 index
        long output0Index = Utils.readUint32(leftAndRight, _offset);
        _offset += 4;
        if (output0Index > 1000000 && output0Index != COINBASE_CHECKED){
            // this value is capped by max btc tx size
            // and should check also is not a valid coinbase expressed in a byte array
            return;
        }

        // Check input 0 script length
        byte input0ScriptLength = leftAndRight[_offset];
        _offset += 1;
        if (input0ScriptLength < 0 || input0ScriptLength > 4) {
            // Script length should be 0 to 4 to create a valid 64 byte tx
            return;
        }

        // Skip input 0 script
        _offset += input0ScriptLength;

        // Skip input 0 sequence
        _offset += 4;

        // Check output count
        byte outputCount = leftAndRight[_offset];
        _offset += 1;
        if (outputCount != 1) {
            // Only 1 output fits in 64 bytes
            return;
        }

        // check output 0 value
        long output0Value = Utils.readInt64(leftAndRight, _offset);
        _offset += 8;
        long maxNumberOfSatoshis = 21000000l * 100000000l;
        if (output0Value < 1 || output0Value > maxNumberOfSatoshis) {
            //  0 < value < 21 millions (expressed in satoshis) validation failed
            return;
        }

        // check output 0 script length
        byte output0ScriptLength = leftAndRight[_offset];
        if (output0ScriptLength < 0 || output0ScriptLength > 4) {
            // Script length should be 0 to 4 to create a valid 64 byte tx
            return;
        }

        // check input 0 script length + output 0 script length
        if ((input0ScriptLength + output0ScriptLength) != 4) {
            // Script length should be 0 to 4
            return;
        }

        throw new VerificationException("Supplied nodes form a valid btc transaction");
    }
}

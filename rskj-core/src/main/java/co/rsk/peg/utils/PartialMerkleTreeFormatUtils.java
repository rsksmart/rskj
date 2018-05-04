package co.rsk.peg.utils;

import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.VarInt;

public class PartialMerkleTreeFormatUtils {

    private static final int BLOCK_TRANSACTION_COUNT_LENGTH = 4;

    public static VarInt getHashesCount(byte[] pmtSerialized) {
        return new VarInt(pmtSerialized, BLOCK_TRANSACTION_COUNT_LENGTH);
    }

    public static VarInt getFlagBitsCount(byte[] pmtSerialized) {
        VarInt hashesCount = getHashesCount(pmtSerialized);
        return new VarInt(
                pmtSerialized,
                Math.addExact(
                    BLOCK_TRANSACTION_COUNT_LENGTH + hashesCount.getOriginalSizeInBytes(),
                    Math.multiplyExact(Math.toIntExact(hashesCount.value), Sha256Hash.LENGTH)
                )
        );
    }

    public static boolean hasExpectedSize(byte[] pmtSerialized) {
        try {
            VarInt hashesCount = getHashesCount(pmtSerialized);
            VarInt flagBitsCount = getFlagBitsCount(pmtSerialized);
            int declaredSize = Math.addExact(Math.addExact(BLOCK_TRANSACTION_COUNT_LENGTH
                    + hashesCount.getOriginalSizeInBytes()
                    + flagBitsCount.getOriginalSizeInBytes(),
                    Math.toIntExact(flagBitsCount.value)),
                    Math.multiplyExact(Math.toIntExact(hashesCount.value), Sha256Hash.LENGTH)
            );
            return pmtSerialized.length == declaredSize;
        } catch (RuntimeException e) {
            return false;
        }
    }
}

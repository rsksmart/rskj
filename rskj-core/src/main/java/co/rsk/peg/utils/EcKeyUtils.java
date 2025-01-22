package co.rsk.peg.utils;

import co.rsk.bitcoinj.core.BtcECKey;
import org.ethereum.util.RLP;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class EcKeyUtils {

    private EcKeyUtils() {}

    public static List<BtcECKey> getCompressedPubKeysList(List<BtcECKey> pubKeys) {
        List<BtcECKey> compressedPubKeysList = new ArrayList<>();

        for (BtcECKey key: pubKeys) {
            compressedPubKeysList.add(
                BtcECKey.fromPublicOnly(
                    key.getPubKeyPoint().getEncoded(true)
                )
            );
        }

        return compressedPubKeysList;
    }

    public static byte[] flatKeysAsByteArray(List<BtcECKey> keys) {
        return flatKeys(keys, BtcECKey::getPubKey);
    }

    public static byte[] flatKeysAsRlpCollection(List<BtcECKey> keys) {
        return flatKeys(keys, (k -> RLP.encodeElement(k.getPubKey())));
    }

    private static byte[] flatKeys(List<BtcECKey> keys, Function<BtcECKey, byte[]> parser) {
        List<byte[]> pubKeys = keys.stream()
            .map(parser)
            .toList();
        int pubKeysLength = pubKeys.stream().mapToInt(key -> key.length).sum();

        byte[] flatPubKeys = new byte[pubKeysLength];
        int copyPos = 0;
        for (byte[] key : pubKeys) {
            System.arraycopy(key, 0, flatPubKeys, copyPos, key.length);
            copyPos += key.length;
        }

        return flatPubKeys;
    }
}

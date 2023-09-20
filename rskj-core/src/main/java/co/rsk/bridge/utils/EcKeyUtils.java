package co.rsk.bridge.utils;

import co.rsk.bitcoinj.core.BtcECKey;
import java.util.ArrayList;
import java.util.List;

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
}

package co.rsk.peg.utils;

import co.rsk.bitcoinj.core.BtcECKey;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class EcKeyUtilsTest {
    private final List<BtcECKey> compressedPubKeysList = Arrays.stream(new String[]{
        "03b9fc46657cf72a1afa007ecf431de1cd27ff5cc8829fa625b66ca47b967e6b24",
        "029cecea902067992d52c38b28bf0bb2345bda9b21eca76b16a17c477a64e43301",
        "03284178e5fbcc63c54c3b38e3ef88adf2da6c526313650041b0ef955763634ebd",
    }).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

    private final List<BtcECKey> uncompressedPubKeysList = Arrays.stream(new String[]{
        "04b9fc46657cf72a1afa007ecf431de1cd27ff5cc8829fa625b66ca47b9"
            + "67e6b243635dfd897d936044b05344860cd5494283aad8508d52a784eb6a1f4527e2c9f",
        "049cecea902067992d52c38b28bf0bb2345bda9b21eca76b16a17c477a64e43301b069"
            + "dfae714467c15649fbdb61c70e367fb43f326dc807691923cd16698af99e",
        "04284178e5fbcc63c54c3b38e3ef88adf2da6c526313650041b0ef955763634ebd4076b8bb"
            + "c11b4a3f559c8041b03a903d7d7efacc4dd3796a27df324c7aa3bc5d",
    }).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

    @Test
    void getCompressedPubKeysList_compressed_public_keys_list() {
        Assertions.assertEquals(
            compressedPubKeysList,
            EcKeyUtils.getCompressedPubKeysList(compressedPubKeysList)
        );
    }

    @Test
    void getCompressedPubKeysList_uncompressed_public_keys_list() {
        Assertions.assertEquals(
            compressedPubKeysList,
            EcKeyUtils.getCompressedPubKeysList(uncompressedPubKeysList)
        );
    }
}

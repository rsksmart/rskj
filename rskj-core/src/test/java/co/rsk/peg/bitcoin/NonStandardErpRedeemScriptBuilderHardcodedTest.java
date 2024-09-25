package co.rsk.peg.bitcoin;

import static org.junit.jupiter.api.Assertions.*;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.script.Script;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class NonStandardErpRedeemScriptBuilderHardcodedTest {

    private static final BridgeConstants bridgeMainnetConstants = BridgeMainNetConstants.getInstance();

    private static final List<BtcECKey> defaultKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
        new String[]{"fa01", "fa02", "fa03", "fa04", "fa05", "fa06", "fa07", "fa08", "fa09"}, true
    );
    private static final int defaultThreshold = defaultKeys.size() / 2 + 1;

    private static final List<BtcECKey> erpKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
        new String[]{"fb01", "fb02", "fb03", "fb04"}, true
    );
    private static final int erpThreshold = erpKeys.size() / 2 + 1;

    private static long CSV_VALUE = bridgeMainnetConstants.getFederationConstants().getErpFedActivationDelay();

    private static final byte[] LEGACY_ERP_TESTNET_REDEEM_SCRIPT_BYTES = Hex.decode("6453210208f40073a9e43b3e9103acec79767a6de9b0409749884e989960fee578012fce210225e892391625854128c5c4ea4340de0c2a70570f33db53426fc9c746597a03f42102afc230c2d355b1a577682b07bc2646041b5d0177af0f98395a46018da699b6da210344a3c38cd59afcba3edcebe143e025574594b001700dec41e59409bdbd0f2a0921039a060badbeb24bee49eb2063f616c0f0f0765d4ca646b20a88ce828f259fcdb955670300cd50b27552210216c23b2ea8e4f11c3f9e22711addb1d16a93964796913830856b568cc3ea21d3210275562901dd8faae20de0a4166362a4f82188db77dbed4ca887422ea1ec185f1421034db69f2112f4fb1bb6141bf6e2bd6631f0484d0bd95b16767902c9fe219d4a6f5368ae");

    @Test
    void of_whenValidValues_shouldReturnHardcodeTestnetRedeemScript() {
        // act
        Script redeemScript = NonStandardErpRedeemScriptBuilderHardcoded.builder().of(
            defaultKeys, defaultThreshold, erpKeys, erpThreshold, CSV_VALUE
        );

        // assert
        assertArrayEquals(
            LEGACY_ERP_TESTNET_REDEEM_SCRIPT_BYTES,
            redeemScript.getProgram()
        );
    }

    @ParameterizedTest()
    @MethodSource("invalidInputsArgsProvider")
    void of_invalidInputs_throwsException(
        List<BtcECKey> defaultKeys,
        Integer defaultThreshold,
        List<BtcECKey> erpKeys,
        Integer erpThreshold,
        Long csvValue
    ) {
        // act
        Script redeemScript = NonStandardErpRedeemScriptBuilderHardcoded.builder().of(
            defaultKeys, defaultThreshold, erpKeys, erpThreshold, csvValue
        );

        // assert
        assertArrayEquals(
            LEGACY_ERP_TESTNET_REDEEM_SCRIPT_BYTES,
            redeemScript.getProgram()
        );
    }

    private static Stream<Arguments> invalidInputsArgsProvider() {
        long surpassingMaxCsvValue = ErpRedeemScriptBuilderUtils.MAX_CSV_VALUE + 1;
        return Stream.of(
            Arguments.of(null, 0, erpKeys, erpThreshold, CSV_VALUE),
            // empty default keys
            Arguments.of(Collections.emptyList(), 0, erpKeys, erpThreshold, CSV_VALUE),
            Arguments.of(defaultKeys, -1, erpKeys, erpThreshold, CSV_VALUE),
            // threshold greater than default keys size
            Arguments.of(defaultKeys, defaultKeys.size()+1, erpKeys, erpThreshold, CSV_VALUE),
            Arguments.of(defaultKeys, defaultThreshold, null, erpThreshold, CSV_VALUE),
            // empty erp keys
            Arguments.of(defaultKeys, defaultThreshold, Collections.emptyList(), erpThreshold, CSV_VALUE),
            Arguments.of(defaultKeys, defaultThreshold, erpKeys, -1, CSV_VALUE),
            // erp threshold greater than erp keys size
            Arguments.of(defaultKeys, defaultThreshold, erpKeys, erpKeys.size() + 1, CSV_VALUE),
            Arguments.of(defaultKeys, defaultThreshold, erpKeys, erpThreshold, -1L),
            Arguments.of(defaultKeys, defaultThreshold, erpKeys, erpThreshold, 0L),
            Arguments.of(defaultKeys, defaultThreshold, erpKeys, erpThreshold, surpassingMaxCsvValue)
        );
    }
}

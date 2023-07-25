package co.rsk.peg;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Utils;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptOpCodes;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeMainNetConstants;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.config.BridgeTestNetConstants;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class P2shErpFederationTest {

    @ParameterizedTest
    @MethodSource("getRedeemScriptArgsProvider")
    void getRedeemScript(BridgeConstants bridgeConstants) {
        List<BtcECKey> standardKeys = bridgeConstants.getGenesisFederation().getBtcPublicKeys();
        List<BtcECKey> emergencyKeys = bridgeConstants.getErpFedPubKeysList();
        long activationDelay = bridgeConstants.getErpFedActivationDelay();

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);

        Federation p2shErpFederation = new P2shErpFederation(
            FederationTestUtils.getFederationMembersWithBtcKeys(standardKeys),
            ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
            0L,
            bridgeConstants.getBtcParams(),
            emergencyKeys,
            activationDelay,
            activations
        );

        validateP2shErpRedeemScript(
            p2shErpFederation.getRedeemScript(),
            standardKeys,
            emergencyKeys,
            activationDelay
        );
    }

    @Test
    void getStandardRedeemscript() {
        List<FederationMember> members = FederationMember.getFederationMembersFromKeys(
            Arrays.asList(new BtcECKey(), new BtcECKey(), new BtcECKey())
        );
        Instant creationTime = Instant.now();
        int creationBlock = 0;
        NetworkParameters btcParams = BridgeRegTestConstants.getInstance().getBtcParams();

        ActivationConfig.ForBlock activations = ActivationConfigsForTest.all().forBlock(0);

        // Create a legacy powpeg and then a p2sh valid one. Both of them should produce the same standard redeem script
        Federation legacyFed = new Federation(
                members,
                creationTime,
                creationBlock,
                btcParams
        );

        P2shErpFederation p2shFed = new P2shErpFederation(
                members,
                creationTime,
                creationBlock,
                btcParams,
                Arrays.asList(new BtcECKey(), new BtcECKey()),
                10_000,
                activations
        );

        assertEquals(legacyFed.getRedeemScript(), p2shFed.getStandardRedeemScript());
        Assertions.assertNotEquals(p2shFed.getRedeemScript(), p2shFed.getStandardRedeemScript());
    }

    @Test
    void getPowPegAddress_testnet() {
        BridgeConstants bridgeTestNetConstants = BridgeTestNetConstants.getInstance();

        List<BtcECKey> powpegKeys = Arrays.stream(new String[]{
            "020ace50bab1230f8002a0bfe619482af74b338cc9e4c956add228df47e6adae1c",
            "0275d473555de2733c47125f9702b0f870df1d817379f5587f09b6c40ed2c6c949",
            "025093f439fb8006fd29ab56605ffec9cdc840d16d2361004e1337a2f86d8bd2db",
            "026b472f7d59d201ff1f540f111b6eb329e071c30a9d23e3d2bcd128fe73dc254c",
            "03250c11be0561b1d7ae168b1f59e39cbc1fd1ba3cf4d2140c1a365b2723a2bf93",
            "0357f7ed4c118e581f49cd3b4d9dd1edb4295f4def49d6dcf2faaaaac87a1a0a42",
            "03ae72827d25030818c4947a800187b1fbcc33ae751e248ae60094cc989fb880f6",
            "03e05bf6002b62651378b1954820539c36ca405cbb778c225395dd9ebff6780299",
            "03b58a5da144f5abab2e03e414ad044b732300de52fa25c672a7f7b35888771906"
        }).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());
        Address expectedAddress = Address.fromBase58(
            bridgeTestNetConstants.getBtcParams(),
            "2N7Y1BW8pMLMTQ1fg4kSAftSrwMwpb4S9B7"
        );

        Federation p2shErpFederation = new P2shErpFederation(
            FederationTestUtils.getFederationMembersWithBtcKeys(powpegKeys),
            Instant.now(),
            0L,
            bridgeTestNetConstants.getBtcParams(),
            bridgeTestNetConstants.getErpFedPubKeysList(),
            bridgeTestNetConstants.getErpFedActivationDelay(),
            mock(ActivationConfig.ForBlock.class)
        );

        assertEquals(expectedAddress, p2shErpFederation.getAddress());
    }

    @Test
    void getPowPegAddress_mainnet() {
        BridgeConstants bridgeMainNetConstants = BridgeMainNetConstants.getInstance();

        List<BtcECKey> powpegKeys = Arrays.stream(new String[]{
            "020ace50bab1230f8002a0bfe619482af74b338cc9e4c956add228df47e6adae1c",
            "0275d473555de2733c47125f9702b0f870df1d817379f5587f09b6c40ed2c6c949",
            "025093f439fb8006fd29ab56605ffec9cdc840d16d2361004e1337a2f86d8bd2db",
            "026b472f7d59d201ff1f540f111b6eb329e071c30a9d23e3d2bcd128fe73dc254c",
            "03250c11be0561b1d7ae168b1f59e39cbc1fd1ba3cf4d2140c1a365b2723a2bf93",
            "0357f7ed4c118e581f49cd3b4d9dd1edb4295f4def49d6dcf2faaaaac87a1a0a42",
            "03ae72827d25030818c4947a800187b1fbcc33ae751e248ae60094cc989fb880f6",
            "03e05bf6002b62651378b1954820539c36ca405cbb778c225395dd9ebff6780299",
            "03b58a5da144f5abab2e03e414ad044b732300de52fa25c672a7f7b35888771906"
        }).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());
        Address expectedAddress = Address.fromBase58(
            bridgeMainNetConstants.getBtcParams(),
            "35iEoWHfDfEXRQ5ZWM5F6eMsY2Uxrc64YK"
        );

        Federation p2shErpFederation = new P2shErpFederation(
            FederationTestUtils.getFederationMembersWithBtcKeys(powpegKeys),
            Instant.now(),
            0L,
            bridgeMainNetConstants.getBtcParams(),
            bridgeMainNetConstants.getErpFedPubKeysList(),
            bridgeMainNetConstants.getErpFedActivationDelay(),
            mock(ActivationConfig.ForBlock.class)
        );

        assertEquals(expectedAddress, p2shErpFederation.getAddress());
    }

    @ParameterizedTest()
    @MethodSource("spendFromP2shErpFedArgsProvider")
    void spendFromP2shErpFed(
        NetworkParameters networkParameters,
        long activationDelay,
        boolean signWithEmergencyMultisig) {

        List<BtcECKey> standardKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fed1", "fed2", "fed3", "fed4", "fed5", "fed6", "fed7", "fed8", "fed9", "fed10"},
            true
        );

        List<BtcECKey> emergencyKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"erp1", "erp2", "erp3", "erp4"},
            true
        );

        P2shErpFederation p2shErpFed = new P2shErpFederation(
            FederationMember.getFederationMembersFromKeys(standardKeys),
            ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
            0L,
            networkParameters,
            emergencyKeys,
            activationDelay,
            mock(ActivationConfig.ForBlock.class)
        );

        Coin value = Coin.valueOf(1_000_000);
        Coin fee = Coin.valueOf(10_000);
        BtcTransaction fundTx = new BtcTransaction(networkParameters);
        fundTx.addOutput(value, p2shErpFed.getAddress());

        Address destinationAddress = BitcoinTestUtils.createP2PKHAddress(
            networkParameters,
            "destination"
        );

        assertDoesNotThrow(() -> FederationTestUtils.spendFromErpFed(
            networkParameters,
            p2shErpFed,
            signWithEmergencyMultisig ? emergencyKeys : standardKeys,
            fundTx.getHash(),
            0,
            destinationAddress,
            value.minus(fee),
            signWithEmergencyMultisig
        ));
    }

    private void validateP2shErpRedeemScript(
        Script erpRedeemScript,
        List<BtcECKey> defaultMultisigKeys,
        List<BtcECKey> emergencyMultisigKeys,
        Long csvValue) {

        // Keys are sorted when added to the redeem script, so we need them sorted in order to validate
        defaultMultisigKeys.sort(BtcECKey.PUBKEY_COMPARATOR);
        emergencyMultisigKeys.sort(BtcECKey.PUBKEY_COMPARATOR);

        byte[] serializedCsvValue = Utils.signedLongToByteArrayLE(csvValue);

        byte[] script = erpRedeemScript.getProgram();
        Assertions.assertTrue(script.length > 0);

        int index = 0;

        // First byte should equal OP_NOTIF
        assertEquals(ScriptOpCodes.OP_NOTIF, script[index++]);

        // Next byte should equal M, from an M/N multisig
        int m = defaultMultisigKeys.size() / 2 + 1;
        assertEquals(ScriptOpCodes.getOpCode(String.valueOf(m)), script[index++]);

        // Assert public keys
        for (BtcECKey key: defaultMultisigKeys) {
            byte[] pubkey = key.getPubKey();
            assertEquals(pubkey.length, script[index++]);
            for (byte b : pubkey) {
                assertEquals(b, script[index++]);
            }
        }

        // Next byte should equal N, from an M/N multisig
        int n = defaultMultisigKeys.size();
        assertEquals(ScriptOpCodes.getOpCode(String.valueOf(n)), script[index++]);

        // Next byte should equal OP_CHECKMULTISIG
        assertEquals(Integer.valueOf(ScriptOpCodes.OP_CHECKMULTISIG).byteValue(), script[index++]);

        // Next byte should equal OP_ELSE
        assertEquals(ScriptOpCodes.OP_ELSE, script[index++]);

        // Next byte should equal csv value length
        assertEquals(serializedCsvValue.length, script[index++]);

        // Next bytes should equal the csv value in bytes
        for (int i = 0; i < serializedCsvValue.length; i++) {
            assertEquals(serializedCsvValue[i], script[index++]);
        }

        assertEquals(Integer.valueOf(ScriptOpCodes.OP_CHECKSEQUENCEVERIFY).byteValue(), script[index++]);
        assertEquals(ScriptOpCodes.OP_DROP, script[index++]);

        // Next byte should equal M, from an M/N multisig
        m = emergencyMultisigKeys.size() / 2 + 1;
        assertEquals(ScriptOpCodes.getOpCode(String.valueOf(m)), script[index++]);

        for (BtcECKey key: emergencyMultisigKeys) {
            byte[] pubkey = key.getPubKey();
            assertEquals(Integer.valueOf(pubkey.length).byteValue(), script[index++]);
            for (byte b : pubkey) {
                assertEquals(b, script[index++]);
            }
        }

        // Next byte should equal N, from an M/N multisig
        n = emergencyMultisigKeys.size();
        assertEquals(ScriptOpCodes.getOpCode(String.valueOf(n)), script[index++]);

        // Next byte should equal OP_CHECKMULTISIG
        assertEquals(Integer.valueOf(ScriptOpCodes.OP_CHECKMULTISIG).byteValue(), script[index++]);

        assertEquals(ScriptOpCodes.OP_ENDIF, script[index++]);
    }

    private static Stream<Arguments> spendFromP2shErpFedArgsProvider() {
        BridgeConstants bridgeMainNetConstants = BridgeMainNetConstants.getInstance();
        BridgeConstants bridgeTestNetConstants = BridgeTestNetConstants.getInstance();

        return Stream.of(
            Arguments.of(
                bridgeMainNetConstants.getBtcParams(),
                bridgeMainNetConstants.getErpFedActivationDelay(),
                false
            ),
            Arguments.of(
                bridgeTestNetConstants.getBtcParams(),
                bridgeTestNetConstants.getErpFedActivationDelay(),
                false
            ),
            Arguments.of(
                bridgeMainNetConstants.getBtcParams(),
                bridgeMainNetConstants.getErpFedActivationDelay(),
                true
            ),
            Arguments.of(
                bridgeTestNetConstants.getBtcParams(),
                bridgeTestNetConstants.getErpFedActivationDelay(),
                true
            )
        );
    }

    private static Stream<Arguments> getRedeemScriptArgsProvider() {
        return Stream.of(
            Arguments.of(BridgeMainNetConstants.getInstance()),
            Arguments.of(BridgeTestNetConstants.getInstance())
        );
    }
}

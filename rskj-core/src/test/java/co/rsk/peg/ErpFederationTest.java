package co.rsk.peg;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Utils;
import co.rsk.bitcoinj.core.VerificationException;
import co.rsk.bitcoinj.script.ErpFederationRedeemScriptParser;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptOpCodes;
import co.rsk.peg.resources.TestConstants;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ErpFederationTest {
    private ErpFederation federation;
    private ActivationConfig.ForBlock activations;

    // Default federation multisig keys
    private static final List<BtcECKey> DEFAULT_FED_KEYS = Arrays.stream(new String[]{
        "03b9fc46657cf72a1afa007ecf431de1cd27ff5cc8829fa625b66ca47b967e6b24",
        "029cecea902067992d52c38b28bf0bb2345bda9b21eca76b16a17c477a64e43301",
        "03284178e5fbcc63c54c3b38e3ef88adf2da6c526313650041b0ef955763634ebd",
        "03776b1fd8f86da3c1db3d69699e8250a15877d286734ea9a6da8e9d8ad25d16c1",
        "03ab0e2cd7ed158687fc13b88019990860cdb72b1f5777b58513312550ea1584bc"
    }).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).sorted(BtcECKey.PUBKEY_COMPARATOR).collect(Collectors.toList());

    // Emergency multisig keys
    private static final List<BtcECKey> ERP_KEYS = Arrays.stream(new String[]{
        "02ed3bace23c5e17652e174c835fb72bf53ee306b3406a26890221b4cef7500f88",
        "0385a7b790fc9d962493788317e4874a4ab07f1e9c78c773c47f2f6c96df756f05",
        "03cd5a3be41717d65683fe7a9de8ae5b4b8feced69f26a8b55eeefbcc2e74b75fb"
    }).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).sorted(BtcECKey.PUBKEY_COMPARATOR).collect(Collectors.toList());

    private static final long ACTIVATION_DELAY_VALUE = 5063;

    @Before
    public void setup() {
        activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(true);

        federation = createDefaultErpFederation();
    }

    @Test
    public void getErpPubKeys() {
        Assert.assertEquals(ERP_KEYS, federation.getErpPubKeys());
    }

    @Test
    public void getActivationDelay() {
        Assert.assertEquals(ACTIVATION_DELAY_VALUE, federation.getActivationDelay());
    }

    @Test
    public void getRedeemScript_before_RSKIP293() {
        Script redeemScript = federation.getRedeemScript();
        validateErpRedeemScript(
            redeemScript,
            ACTIVATION_DELAY_VALUE,
            false
        );
    }

    @Test
    public void getRedeemScript_after_RSKIP293() {
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(true);
        federation = createDefaultErpFederation();
        Script redeemScript = federation.getRedeemScript();

        validateErpRedeemScript(
            redeemScript,
            ACTIVATION_DELAY_VALUE,
            true
        );
    }

    @Test
    public void getRedeemScript_changes_after_RSKIP293() {
        Script preRskip293RedeemScript = federation.getRedeemScript();

        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(true);
        federation = createDefaultErpFederation();
        Script postRskip293RedeemScript = federation.getRedeemScript();

        Assert.assertNotEquals(preRskip293RedeemScript, postRskip293RedeemScript);
    }

    @Test
    public void getP2SHScript() {
        Script p2shs = federation.getP2SHScript();
        String expectedProgram = "a914867df89b0837b2fd86eb98751d14b6cef52d342487";

        Assert.assertEquals(expectedProgram, Hex.toHexString(p2shs.getProgram()));
        Assert.assertEquals(3, p2shs.getChunks().size());
        Assert.assertEquals(
            federation.getAddress(),
            p2shs.getToAddress(NetworkParameters.fromID(NetworkParameters.ID_REGTEST))
        );
    }

    @Test
    public void getAddress() {
        String fedAddress = federation.getAddress().toBase58();
        String expectedAddress = "2N5WMScfkbBVWMByrGw6GZDFF3m4tb3qqP8";

        Assert.assertEquals(expectedAddress, fedAddress);
    }

    @Test
    public void getErpPubKeys_compressed_public_keys() {
        Assert.assertEquals(ERP_KEYS, federation.getErpPubKeys());
    }

    @Test
    public void getErpPubKeys_uncompressed_public_keys() {
        // Public keys used for creating federation, but uncompressed format now
        List<BtcECKey> uncompressedErpKeys = ERP_KEYS
            .stream()
            .map(BtcECKey::decompress)
            .collect(Collectors.toList());

        // Recreate federation
        ErpFederation federationWithUncompressedKeys = new ErpFederation(
            FederationTestUtils.getFederationMembersWithBtcKeys(DEFAULT_FED_KEYS),
            ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST),
            uncompressedErpKeys,
            ACTIVATION_DELAY_VALUE,
            mock(ActivationConfig.ForBlock.class)
        );

        Assert.assertEquals(ERP_KEYS, federationWithUncompressedKeys.getErpPubKeys());
    }

    @Test(expected = VerificationException.class)
    public void createInvalidErpFederation_negativeCsvValue() {
        new ErpFederation(
            FederationTestUtils.getFederationMembersFromPks(100, 200, 300),
            ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST),
            ERP_KEYS,
            -100L,
            activations
        );
    }

    @Test(expected = VerificationException.class)
    public void createInvalidErpFederation_csvValueAboveMax() {
        new ErpFederation(
            FederationTestUtils.getFederationMembersFromPks(100, 200, 300),
            ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST),
            ERP_KEYS,
            ErpFederationRedeemScriptParser.MAX_CSV_VALUE + 1,
            activations
        );
    }

    @Test(expected = VerificationException.class)
    public void createInvalidErpFederation_csvValueBelowMin() {
        new ErpFederation(
            FederationTestUtils.getFederationMembersFromPks(100, 200, 300),
            ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST),
            ERP_KEYS,
            ErpFederationRedeemScriptParser.MIN_CSV_VALUE - 1,
            activations
        );
    }

    @Test
    public void getRedeemScript_before_RSKIP_284_testnet() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(false);

        Federation erpFederation = new ErpFederation(
            FederationTestUtils.getFederationMembersFromPks(100, 200, 300),
            ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_TESTNET),
            ERP_KEYS,
            ACTIVATION_DELAY_VALUE,
            activations
        );

        Assert.assertEquals(TestConstants.ERP_TESTNET_REDEEM_SCRIPT, erpFederation.getRedeemScript());
    }

    @Test
    public void getRedeemScript_before_RSKIP_284_mainnet() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(false);

        Federation erpFederation = new ErpFederation(
            FederationTestUtils.getFederationMembersWithBtcKeys(DEFAULT_FED_KEYS),
            ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_MAINNET),
            ERP_KEYS,
            ACTIVATION_DELAY_VALUE,
            activations
        );

        Assert.assertNotEquals(TestConstants.ERP_TESTNET_REDEEM_SCRIPT, erpFederation.getRedeemScript());
        validateErpRedeemScript(
            erpFederation.getRedeemScript(),
            ACTIVATION_DELAY_VALUE,
            false
        );
    }

    @Test
    public void getRedeemScript_after_RSKIP_284_testnet() {
        Federation erpFederation = new ErpFederation(
            FederationTestUtils.getFederationMembersWithBtcKeys(DEFAULT_FED_KEYS),
            ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_TESTNET),
            ERP_KEYS,
            ACTIVATION_DELAY_VALUE,
            activations
        );

        Assert.assertNotEquals(TestConstants.ERP_TESTNET_REDEEM_SCRIPT, erpFederation.getRedeemScript());
        validateErpRedeemScript(
            erpFederation.getRedeemScript(),
            ACTIVATION_DELAY_VALUE,
            false
        );
    }

    @Test
    public void getRedeemScript_after_RSKIP_284_mainnet() {
        Federation erpFederation = new ErpFederation(
            FederationTestUtils.getFederationMembersWithBtcKeys(DEFAULT_FED_KEYS),
            ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_MAINNET),
            ERP_KEYS,
            ACTIVATION_DELAY_VALUE,
            activations
        );

        Assert.assertNotEquals(TestConstants.ERP_TESTNET_REDEEM_SCRIPT, erpFederation.getRedeemScript());
        validateErpRedeemScript(
            erpFederation.getRedeemScript(),
            ACTIVATION_DELAY_VALUE,
            false
        );
    }

    @Test
    public void testEquals_basic() {
        Assert.assertEquals(federation, federation);

        Assert.assertNotEquals(null, federation);
        Assert.assertNotEquals(federation, new Object());
        Assert.assertNotEquals("something else", federation);
    }

    @Test
    public void testEquals_same() {
        Federation otherFederation = new ErpFederation(
            federation.getMembers(),
            federation.getCreationTime(),
            federation.getCreationBlockNumber(),
            federation.getBtcParams(),
            federation.getErpPubKeys(),
            federation.getActivationDelay(),
            activations
        );

        Assert.assertEquals(federation, otherFederation);
    }

    @Test
    public void testEquals_differentNumberOfMembers() {
        Federation otherFederation = new ErpFederation(
            FederationTestUtils.getFederationMembersFromPks(100, 200, 300, 400, 500, 600, 700),
            federation.getCreationTime(),
            federation.getCreationBlockNumber(),
            federation.getBtcParams(),
            federation.getErpPubKeys(),
            federation.getActivationDelay(),
            activations
        );

        Assert.assertNotEquals(federation, otherFederation);
    }

    @Test
    public void testEquals_differentCreationTime() {
        Federation otherFederation = new ErpFederation(
            federation.getMembers(),
            Instant.now(),
            federation.getCreationBlockNumber(),
            federation.getBtcParams(),
            federation.getErpPubKeys(),
            federation.getActivationDelay(),
            activations
        );

        Assert.assertNotEquals(federation, otherFederation);
    }

    @Test
    public void testEquals_differentCreationBlockNumber() {
        Federation otherFederation = new ErpFederation(
            federation.getMembers(),
            federation.getCreationTime(),
            federation.getCreationBlockNumber() + 1,
            federation.getBtcParams(),
            federation.getErpPubKeys(),
            federation.getActivationDelay(),
            activations
        );

        Assert.assertNotEquals(federation, otherFederation);
    }

    @Test
    public void testEquals_differentNetworkParameters() {
        Federation otherFederation = new ErpFederation(
            federation.getMembers(),
            federation.getCreationTime(),
            federation.getCreationBlockNumber(),
            NetworkParameters.fromID(NetworkParameters.ID_MAINNET),
            federation.getErpPubKeys(),
            federation.getActivationDelay(),
            activations
        );

        Assert.assertNotEquals(federation, otherFederation);
    }

    @Test
    public void testEquals_differentMembers() {
        Federation otherFederation = new ErpFederation(
            FederationTestUtils.getFederationMembersFromPks(101, 201, 301),
            federation.getCreationTime(),
            federation.getCreationBlockNumber(),
            federation.getBtcParams(),
            federation.getErpPubKeys(),
            federation.getActivationDelay(),
            activations
        );

        Assert.assertNotEquals(federation, otherFederation);
    }

    @Test
    public void testEquals_differentRedeemScript() {
        ActivationConfig.ForBlock activationsPre = mock(ActivationConfig.ForBlock.class);
        when(activationsPre.isActive(ConsensusRule.RSKIP284)).thenReturn(false);

        ActivationConfig.ForBlock activationsPost = mock(ActivationConfig.ForBlock.class);
        when(activationsPost.isActive(ConsensusRule.RSKIP284)).thenReturn(true);

        // Both federations created before RSKIP284 with the same data, should have the same redeem script
        Federation erpFederation = new ErpFederation(
            FederationTestUtils.getFederationMembersFromPks(100, 200, 300),
            ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_TESTNET),
            ERP_KEYS,
            ACTIVATION_DELAY_VALUE,
            activationsPre
        );

        Federation otherErpFederation = new ErpFederation(
            FederationTestUtils.getFederationMembersFromPks(100, 200, 300),
            ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_TESTNET),
            ERP_KEYS,
            ACTIVATION_DELAY_VALUE,
            activationsPre
        );

        Assert.assertEquals(erpFederation, otherErpFederation);

        // One federation created after RSKIP284 with the same data, should have different redeem script
        otherErpFederation = new ErpFederation(
            FederationTestUtils.getFederationMembersFromPks(100, 200, 300),
            ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_TESTNET),
            ERP_KEYS,
            ACTIVATION_DELAY_VALUE,
            activationsPost
        );

        Assert.assertNotEquals(erpFederation, otherErpFederation);

        // The other federation created after RSKIP284 with the same data, should have same redeem script
        erpFederation = new ErpFederation(
            FederationTestUtils.getFederationMembersFromPks(100, 200, 300),
            ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_TESTNET),
            ERP_KEYS,
            ACTIVATION_DELAY_VALUE,
            activationsPost
        );

        Assert.assertEquals(erpFederation, otherErpFederation);
    }

    private ErpFederation createDefaultErpFederation() {
        return new ErpFederation(
            FederationTestUtils.getFederationMembersWithBtcKeys(DEFAULT_FED_KEYS),
            ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST),
            ERP_KEYS,
            ACTIVATION_DELAY_VALUE,
            activations
        );
    }

    private void validateErpRedeemScript(
        Script erpRedeemScript,
        Long csvValue,
        boolean isRskip293Active) {

        byte[] serializedCsvValue = isRskip293Active ?
            Utils.unsignedLongToByteArrayLE(csvValue, ErpFederationRedeemScriptParser.CSV_SERIALIZED_LENGTH) :
            Utils.unsignedLongToByteArrayBE(csvValue, ErpFederationRedeemScriptParser.CSV_SERIALIZED_LENGTH);

        byte[] script = erpRedeemScript.getProgram();
        Assert.assertTrue(script.length > 0);

        int index = 0;

        // First byte should equal OP_NOTIF
        Assert.assertEquals(ScriptOpCodes.OP_NOTIF, script[index++]);

        // Next byte should equal M, from an M/N multisig
        int m = DEFAULT_FED_KEYS.size() / 2 + 1;
        Assert.assertEquals(ScriptOpCodes.getOpCode(String.valueOf(m)), script[index++]);

        // Assert public keys
        for (BtcECKey key: DEFAULT_FED_KEYS) {
            byte[] pubkey = key.getPubKey();
            Assert.assertEquals(pubkey.length, script[index++]);
            for (int pkIndex = 0; pkIndex < pubkey.length; pkIndex++) {
                Assert.assertEquals(pubkey[pkIndex], script[index++]);
            }
        }

        // Next byte should equal N, from an M/N multisig
        int n = DEFAULT_FED_KEYS.size();
        Assert.assertEquals(ScriptOpCodes.getOpCode(String.valueOf(n)), script[index++]);

        // Next byte should equal OP_ELSE
        Assert.assertEquals(ScriptOpCodes.OP_ELSE, script[index++]);

        // Next byte should equal csv value length
        Assert.assertEquals(ErpFederationRedeemScriptParser.CSV_SERIALIZED_LENGTH, script[index++]);

        // Next bytes should equal the csv value in bytes
        for (int i = 0; i < ErpFederationRedeemScriptParser.CSV_SERIALIZED_LENGTH; i++) {
            Assert.assertEquals(serializedCsvValue[i], script[index++]);
        }

        Assert.assertEquals(Integer.valueOf(ScriptOpCodes.OP_CHECKSEQUENCEVERIFY).byteValue(), script[index++]);
        Assert.assertEquals(ScriptOpCodes.OP_DROP, script[index++]);

        // Next byte should equal M, from an M/N multisig
        m = ERP_KEYS.size() / 2 + 1;
        Assert.assertEquals(ScriptOpCodes.getOpCode(String.valueOf(m)), script[index++]);

        for (BtcECKey key: ERP_KEYS) {
            byte[] pubkey = key.getPubKey();
            Assert.assertEquals(Integer.valueOf(pubkey.length).byteValue(), script[index++]);
            for (int pkIndex = 0; pkIndex < pubkey.length; pkIndex++) {
                Assert.assertEquals(pubkey[pkIndex], script[index++]);
            }
        }

        // Next byte should equal N, from an M/N multisig
        n = ERP_KEYS.size();
        Assert.assertEquals(ScriptOpCodes.getOpCode(String.valueOf(n)), script[index++]);

        Assert.assertEquals(ScriptOpCodes.OP_ENDIF, script[index++]);
        Assert.assertEquals(Integer.valueOf(ScriptOpCodes.OP_CHECKMULTISIG).byteValue(), script[index++]);
    }
}

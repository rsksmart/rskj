package co.rsk.peg.federation;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeMainNetConstants;
import co.rsk.config.BridgeTestNetConstants;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;

import static co.rsk.peg.federation.FederationFormatVersion.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FederationFactoryTest {
    private BridgeConstants bridgeConstants;
    private NetworkParameters networkParameters;
    private List<FederationMember> federationMembers;
    private List<BtcECKey> defaultKeys;
    private Instant creationTime;
    private long creationBlockNumber;
    private List<BtcECKey> emergencyKeys;
    private long activationDelayValue;
    private ActivationConfig.ForBlock activations;

    @BeforeEach
    void setUp() {
        BtcECKey federator0PublicKey = BtcECKey.fromPublicOnly(Hex.decode("03b53899c390573471ba30e5054f78376c5f797fda26dde7a760789f02908cbad2"));
        BtcECKey federator1PublicKey = BtcECKey.fromPublicOnly(Hex.decode("027319afb15481dbeb3c426bcc37f9a30e7f51ceff586936d85548d9395bcc2344"));
        BtcECKey federator2PublicKey = BtcECKey.fromPublicOnly(Hex.decode("0355a2e9bf100c00fc0a214afd1bf272647c7824eb9cb055480962f0c382596a70"));
        BtcECKey federator3PublicKey = BtcECKey.fromPublicOnly(Hex.decode("02566d5ded7c7db1aa7ee4ef6f76989fb42527fcfdcddcd447d6793b7d869e46f7"));
        BtcECKey federator4PublicKey = BtcECKey.fromPublicOnly(Hex.decode("0294c817150f78607566e961b3c71df53a22022a80acbb982f83c0c8baac040adc"));
        BtcECKey federator5PublicKey = BtcECKey.fromPublicOnly(Hex.decode("0372cd46831f3b6afd4c044d160b7667e8ebf659d6cb51a825a3104df6ee0638c6"));
        BtcECKey federator6PublicKey = BtcECKey.fromPublicOnly(Hex.decode("0340df69f28d69eef60845da7d81ff60a9060d4da35c767f017b0dd4e20448fb44"));
        BtcECKey federator7PublicKey = BtcECKey.fromPublicOnly(Hex.decode("02ac1901b6fba2c1dbd47d894d2bd76c8ba1d296d65f6ab47f1c6b22afb53e73eb"));
        BtcECKey federator8PublicKey = BtcECKey.fromPublicOnly(Hex.decode("031aabbeb9b27258f98c2bf21f36677ae7bae09eb2d8c958ef41a20a6e88626d26"));
        BtcECKey federator9PublicKey = BtcECKey.fromPublicOnly(Hex.decode("0245ef34f5ee218005c9c21227133e8568a4f3f11aeab919c66ff7b816ae1ffeea"));
        defaultKeys = Arrays.asList(
            federator0PublicKey, federator1PublicKey, federator2PublicKey,
            federator3PublicKey, federator4PublicKey, federator5PublicKey,
            federator6PublicKey, federator7PublicKey, federator8PublicKey,
            federator9PublicKey
        );
        federationMembers = FederationTestUtils.getFederationMembersWithKeys(defaultKeys);
        creationTime = ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant();
        creationBlockNumber = 0L;
        bridgeConstants = BridgeMainNetConstants.getInstance();
        networkParameters = bridgeConstants.getBtcParams();
    }

    @Test
    void buildStandardMultiSigFederation() {
        Federation federation = createStandardMultisigFederation();
        int version = federation.getFormatVersion();

        assertEquals(STANDARD_MULTISIG_FEDERATION.getFormatVersion(), version);
    }

    @Nested
    @Tag("erpFeds")
    class ErpFederationTests {
        @BeforeEach
        @Tag("erpFeds")
        void setUp() {
            emergencyKeys = bridgeConstants.getErpFedPubKeysList();
            activationDelayValue = bridgeConstants.getErpFedActivationDelay();
            activations = mock(ActivationConfig.ForBlock.class);
        }

        @Test
        void differentBuilderMethods_return_differentFederations(){
            Federation federationStandard = createStandardMultisigFederation();
            ErpFederation federationNonStandard = createNonStandardErpFederation();
            ErpFederation federationP2sh = createP2shErpFederation();

            assertNotEquals(federationStandard.getFormatVersion(), federationNonStandard.getFormatVersion());
            assertNotEquals(federationStandard.getFormatVersion(), federationP2sh.getFormatVersion());
            assertNotEquals(federationNonStandard.getFormatVersion(), federationP2sh.getFormatVersion());
        }

        @Test
        void differentNonStandardErpFederations_areNotEqualFeds_butHaveSameNonStandardFedFormat_testnet() {
            bridgeConstants = BridgeTestNetConstants.getInstance();
            networkParameters = bridgeConstants.getBtcParams();

            int version;

            // in testnet it should build non standard hardcoded fed
            when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(true);
            ErpFederation federationPostRSKIP201 = createNonStandardErpFederation();
            version = federationPostRSKIP201.getFormatVersion();

            assertEquals(NON_STANDARD_ERP_FEDERATION.getFormatVersion(), version);

            // build non standard fed with csv unsigned big endian
            when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(true);
            ErpFederation federationPostRSKIP284 = createNonStandardErpFederation();
            version = federationPostRSKIP284.getFormatVersion();

            assertEquals(NON_STANDARD_ERP_FEDERATION.getFormatVersion(), version);
            assertNotEquals(federationPostRSKIP201, federationPostRSKIP284);

            // build non standard fed
            when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(true);
            ErpFederation federationPostRSKIP293 = createNonStandardErpFederation();
            version = federationPostRSKIP293.getFormatVersion();

            assertEquals(NON_STANDARD_ERP_FEDERATION.getFormatVersion(), version);
            assertNotEquals(federationPostRSKIP201, federationPostRSKIP293);
            assertNotEquals(federationPostRSKIP284, federationPostRSKIP293);
        }

        @Test
        void differentNonStandardErpFederations_areNotEqualFeds_butHaveSameNonStandardFedFormat_mainnet() {
            int version;

            // in mainnet it should build non standard fed with csv unsigned big endian
            when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(true);
            ErpFederation federationPostRSKIP201 = createNonStandardErpFederation();
            version = federationPostRSKIP201.getFormatVersion();

            assertEquals(NON_STANDARD_ERP_FEDERATION.getFormatVersion(), version);

            // should build non standard fed with csv unsigned big endian
            when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(true);
            ErpFederation federationPostRSKIP284 = createNonStandardErpFederation();
            version = federationPostRSKIP284.getFormatVersion();

            assertEquals(NON_STANDARD_ERP_FEDERATION.getFormatVersion(), version);
            assertEquals(federationPostRSKIP201, federationPostRSKIP284);

            // build non standard fed
            when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(true);
            ErpFederation federationPostRSKIP293 = createNonStandardErpFederation();
            version = federationPostRSKIP293.getFormatVersion();

            assertEquals(NON_STANDARD_ERP_FEDERATION.getFormatVersion(), version);
            assertNotEquals(federationPostRSKIP201, federationPostRSKIP293);
            assertNotEquals(federationPostRSKIP284, federationPostRSKIP293);
        }

        @Test
        void p2shErpFederation_haveP2shFedFormat() {
            ErpFederation federation;
            int version;

            // mainnet
            federation = createP2shErpFederation();
            version = federation.getFormatVersion();
            assertEquals(P2SH_ERP_FEDERATION.getFormatVersion(), version);

            // testnet
            bridgeConstants = BridgeTestNetConstants.getInstance();
            networkParameters = bridgeConstants.getBtcParams();
            federation = createP2shErpFederation();
            version = federation.getFormatVersion();
            assertEquals(P2SH_ERP_FEDERATION.getFormatVersion(), version);
        }

    }

    private Federation createStandardMultisigFederation() {
        return FederationFactory.buildStandardMultiSigFederation(
            federationMembers,
            creationTime,
            creationBlockNumber,
            networkParameters
        );
    }

    private ErpFederation createNonStandardErpFederation() {
        return FederationFactory.buildNonStandardErpFederation(
            federationMembers,
            creationTime,
            creationBlockNumber,
            networkParameters,
            emergencyKeys,
            activationDelayValue,
            activations
        );
    }

    private ErpFederation createP2shErpFederation() {
        return FederationFactory.buildP2shErpFederation(
            federationMembers,
            creationTime,
            creationBlockNumber,
            networkParameters,
            emergencyKeys,
            activationDelayValue
        );
    }

}

package co.rsk.peg.federation;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.federation.constants.FederationMainNetConstants;
import co.rsk.peg.federation.constants.FederationTestNetConstants;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
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

class FederationFactoryTest {
    private static final BtcECKey FEDERATOR_0_PUBLIC_KEY = BtcECKey.fromPublicOnly(Hex.decode("03b53899c390573471ba30e5054f78376c5f797fda26dde7a760789f02908cbad2"));
    private static final BtcECKey FEDERATOR_1_PUBLIC_KEY = BtcECKey.fromPublicOnly(Hex.decode("027319afb15481dbeb3c426bcc37f9a30e7f51ceff586936d85548d9395bcc2344"));
    private static final BtcECKey FEDERATOR_2_PUBLIC_KEY = BtcECKey.fromPublicOnly(Hex.decode("0355a2e9bf100c00fc0a214afd1bf272647c7824eb9cb055480962f0c382596a70"));
    private static final BtcECKey FEDERATOR_3_PUBLIC_KEY = BtcECKey.fromPublicOnly(Hex.decode("02566d5ded7c7db1aa7ee4ef6f76989fb42527fcfdcddcd447d6793b7d869e46f7"));
    private static final BtcECKey FEDERATOR_4_PUBLIC_KEY = BtcECKey.fromPublicOnly(Hex.decode("0294c817150f78607566e961b3c71df53a22022a80acbb982f83c0c8baac040adc"));
    private static final BtcECKey FEDERATOR_5_PUBLIC_KEY = BtcECKey.fromPublicOnly(Hex.decode("0372cd46831f3b6afd4c044d160b7667e8ebf659d6cb51a825a3104df6ee0638c6"));
    private static final BtcECKey FEDERATOR_6_PUBLIC_KEY = BtcECKey.fromPublicOnly(Hex.decode("0340df69f28d69eef60845da7d81ff60a9060d4da35c767f017b0dd4e20448fb44"));
    private static final BtcECKey FEDERATOR_7_PUBLIC_KEY = BtcECKey.fromPublicOnly(Hex.decode("02ac1901b6fba2c1dbd47d894d2bd76c8ba1d296d65f6ab47f1c6b22afb53e73eb"));
    private static final BtcECKey FEDERATOR_8_PUBLIC_KEY = BtcECKey.fromPublicOnly(Hex.decode("031aabbeb9b27258f98c2bf21f36677ae7bae09eb2d8c958ef41a20a6e88626d26"));
    private static final BtcECKey FEDERATOR_9_PUBLIC_KEY = BtcECKey.fromPublicOnly(Hex.decode("0245ef34f5ee218005c9c21227133e8568a4f3f11aeab919c66ff7b816ae1ffeea"));
    private static final List<BtcECKey> DEFAULT_KEYS = Arrays.asList(
        FEDERATOR_0_PUBLIC_KEY, FEDERATOR_1_PUBLIC_KEY, FEDERATOR_2_PUBLIC_KEY,
        FEDERATOR_3_PUBLIC_KEY, FEDERATOR_4_PUBLIC_KEY, FEDERATOR_5_PUBLIC_KEY,
        FEDERATOR_6_PUBLIC_KEY, FEDERATOR_7_PUBLIC_KEY, FEDERATOR_8_PUBLIC_KEY,
        FEDERATOR_9_PUBLIC_KEY
    );
    private static final List<FederationMember> FEDERATION_MEMBERS = FederationTestUtils.getFederationMembersWithKeys(DEFAULT_KEYS);
    private static final Instant CREATION_TIME = ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant();
    private static final long CREATION_BLOCK_NUMBER = 0L;

    private FederationConstants federationConstants;
    private NetworkParameters networkParameters;
    private List<BtcECKey> emergencyKeys;
    private long activationDelayValue;
    private ActivationConfig.ForBlock activations;

    @BeforeEach
    void setUp() {
        federationConstants = FederationMainNetConstants.getInstance();
        networkParameters = federationConstants.getBtcParams();
        activations = ActivationConfigsForTest.all().forBlock(0L);
    }

    @Test
    void buildStandardMultiSigFederation() {
        Federation federation = createStandardMultisigFederation();
        int version = federation.getFormatVersion();

        assertEquals(STANDARD_MULTISIG_FEDERATION.getFormatVersion(), version);
    }

    private Federation createStandardMultisigFederation() {
        FederationArgs federationArgs = new FederationArgs(
            FEDERATION_MEMBERS,
            CREATION_TIME,
            CREATION_BLOCK_NUMBER,
            networkParameters
        );

        return FederationFactory.buildStandardMultiSigFederation(federationArgs);
    }

    @Nested
    @Tag("erpFeds")
    class ErpFederationTests {
        @BeforeEach
        void setUp() {
            emergencyKeys = federationConstants.getErpFedPubKeysList();
            activationDelayValue = federationConstants.getErpFedActivationDelay();
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
            federationConstants = FederationTestNetConstants.getInstance();
            networkParameters = federationConstants.getBtcParams();

            int version;

            // in testnet it should build non-standard hardcoded fed
            activations = ActivationConfigsForTest.iris300().forBlock(0L);
            ErpFederation federationPostRSKIP201 = createNonStandardErpFederation();
            version = federationPostRSKIP201.getFormatVersion();

            assertEquals(NON_STANDARD_ERP_FEDERATION.getFormatVersion(), version);

            // build non-standard fed with csv unsigned big endian
            List<ConsensusRule> exceptedRSKIP = List.of(ConsensusRule.RSKIP293);
            activations = ActivationConfigsForTest.hop400(exceptedRSKIP).forBlock(0L);
            ErpFederation federationPostRSKIP284 = createNonStandardErpFederation();
            version = federationPostRSKIP284.getFormatVersion();

            assertEquals(NON_STANDARD_ERP_FEDERATION.getFormatVersion(), version);
            assertNotEquals(federationPostRSKIP201, federationPostRSKIP284);

            // build non-standard fed
            activations = ActivationConfigsForTest.hop400().forBlock(0L);
            ErpFederation federationPostRSKIP293 = createNonStandardErpFederation();
            version = federationPostRSKIP293.getFormatVersion();

            assertEquals(NON_STANDARD_ERP_FEDERATION.getFormatVersion(), version);
            assertNotEquals(federationPostRSKIP201, federationPostRSKIP293);
            assertNotEquals(federationPostRSKIP284, federationPostRSKIP293);
        }

        @Test
        void differentNonStandardErpFederations_areNotEqualFeds_butHaveSameNonStandardFedFormat_mainnet() {
            int version;

            // in mainnet it should build non-standard fed with csv unsigned big endian
            activations = ActivationConfigsForTest.iris300().forBlock(0L);
            ErpFederation federationPostRSKIP201 = createNonStandardErpFederation();
            version = federationPostRSKIP201.getFormatVersion();

            assertEquals(NON_STANDARD_ERP_FEDERATION.getFormatVersion(), version);

            // should build non-standard fed with csv unsigned big endian
            List<ConsensusRule> exceptedRSKIP = List.of(ConsensusRule.RSKIP293);
            activations = ActivationConfigsForTest.hop400(exceptedRSKIP).forBlock(0L);
            ErpFederation federationPostRSKIP284 = createNonStandardErpFederation();
            version = federationPostRSKIP284.getFormatVersion();

            assertEquals(NON_STANDARD_ERP_FEDERATION.getFormatVersion(), version);
            assertEquals(federationPostRSKIP201, federationPostRSKIP284);

            // build non-standard fed
            activations = ActivationConfigsForTest.hop400().forBlock(0L);
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
            federationConstants = FederationTestNetConstants.getInstance();
            networkParameters = federationConstants.getBtcParams();
            federation = createP2shErpFederation();
            version = federation.getFormatVersion();
            assertEquals(P2SH_ERP_FEDERATION.getFormatVersion(), version);
        }

        private ErpFederation createNonStandardErpFederation() {
            FederationArgs federationArgs = new FederationArgs(
                FEDERATION_MEMBERS,
                CREATION_TIME,
                CREATION_BLOCK_NUMBER,
                networkParameters
            );

            return FederationFactory.buildNonStandardErpFederation(federationArgs, emergencyKeys, activationDelayValue, activations);
        }

        private ErpFederation createP2shErpFederation() {
            FederationArgs federationArgs = new FederationArgs(
                FEDERATION_MEMBERS,
                CREATION_TIME,
                CREATION_BLOCK_NUMBER,
                networkParameters
            );

            return FederationFactory.buildP2shErpFederation(federationArgs, emergencyKeys, activationDelayValue);
        }

        @Test
        void p2shP2wshErpFederation_mainnet_hasP2shP2wshFedFormat() {
            // act
            ErpFederation federation = createP2shP2wshErpFederation();

            // assert
            int expectedVersion = P2SH_P2WSH_ERP_FEDERATION.getFormatVersion();
            assertEquals(expectedVersion, federation.getFormatVersion());
        }

        @Test
        void p2shP2wshErpFederation_testnet_hasP2shP2wshFedFormat() {
            // arrange
            federationConstants = FederationTestNetConstants.getInstance();
            networkParameters = federationConstants.getBtcParams();

            // act
            ErpFederation federation = createP2shP2wshErpFederation();

            // assert
            int expectedVersion = P2SH_P2WSH_ERP_FEDERATION.getFormatVersion();
            assertEquals(expectedVersion, federation.getFormatVersion());
        }

        private ErpFederation createP2shP2wshErpFederation() {
            FederationArgs federationArgs = new FederationArgs(
                FEDERATION_MEMBERS,
                CREATION_TIME,
                CREATION_BLOCK_NUMBER,
                networkParameters
            );

            return FederationFactory.buildP2shP2wshErpFederation(federationArgs, emergencyKeys, activationDelayValue);
        }
    }
}

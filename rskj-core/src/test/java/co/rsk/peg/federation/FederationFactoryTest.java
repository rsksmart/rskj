package co.rsk.peg.federation;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.federation.constants.FederationMainNetConstants;
import co.rsk.peg.federation.constants.FederationTestNetConstants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;

import static co.rsk.peg.federation.FederationFormatVersion.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class FederationFactoryTest {
    private static final List<BtcECKey> DEFAULT_KEYS = BitcoinTestUtils.getBtcEcKeysFromSeeds(
        new String[] { "fed1", "fed2", "fed3", "fed4", "fed5", "fed6", "fed7", "fed8", "fed9" }, true
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
            Federation federationNonStandard = createNonStandardErpFederation();
            Federation federationP2sh = createP2shErpFederation();

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
            Federation federationPostRSKIP201 = createNonStandardErpFederation();
            version = federationPostRSKIP201.getFormatVersion();

            assertEquals(NON_STANDARD_ERP_FEDERATION.getFormatVersion(), version);

            // build non-standard fed with csv unsigned big endian
            List<ConsensusRule> exceptedRSKIP = List.of(ConsensusRule.RSKIP293);
            activations = ActivationConfigsForTest.hop400(exceptedRSKIP).forBlock(0L);
            Federation federationPostRSKIP284 = createNonStandardErpFederation();
            version = federationPostRSKIP284.getFormatVersion();

            assertEquals(NON_STANDARD_ERP_FEDERATION.getFormatVersion(), version);
            assertNotEquals(federationPostRSKIP201, federationPostRSKIP284);

            // build non-standard fed
            activations = ActivationConfigsForTest.hop400().forBlock(0L);
            Federation federationPostRSKIP293 = createNonStandardErpFederation();
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
            Federation federationPostRSKIP201 = createNonStandardErpFederation();
            version = federationPostRSKIP201.getFormatVersion();

            assertEquals(NON_STANDARD_ERP_FEDERATION.getFormatVersion(), version);

            // should build non-standard fed with csv unsigned big endian
            List<ConsensusRule> exceptedRSKIP = List.of(ConsensusRule.RSKIP293);
            activations = ActivationConfigsForTest.hop400(exceptedRSKIP).forBlock(0L);
            Federation federationPostRSKIP284 = createNonStandardErpFederation();
            version = federationPostRSKIP284.getFormatVersion();

            assertEquals(NON_STANDARD_ERP_FEDERATION.getFormatVersion(), version);
            assertEquals(federationPostRSKIP201, federationPostRSKIP284);

            // build non-standard fed
            activations = ActivationConfigsForTest.hop400().forBlock(0L);
            Federation federationPostRSKIP293 = createNonStandardErpFederation();
            version = federationPostRSKIP293.getFormatVersion();

            assertEquals(NON_STANDARD_ERP_FEDERATION.getFormatVersion(), version);
            assertNotEquals(federationPostRSKIP201, federationPostRSKIP293);
            assertNotEquals(federationPostRSKIP284, federationPostRSKIP293);
        }

        @Test
        void p2shErpFederation_haveP2shFedFormat() {
            Federation federation;
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

        private Federation createNonStandardErpFederation() {
            FederationArgs federationArgs = new FederationArgs(
                FEDERATION_MEMBERS,
                CREATION_TIME,
                CREATION_BLOCK_NUMBER,
                networkParameters
            );

            return FederationFactory.buildNonStandardErpFederation(federationArgs, emergencyKeys, activationDelayValue, activations);
        }

        private Federation createP2shErpFederation() {
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
            Federation federation = createP2shP2wshErpFederation();

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
            Federation federation = createP2shP2wshErpFederation();

            // assert
            int expectedVersion = P2SH_P2WSH_ERP_FEDERATION.getFormatVersion();
            assertEquals(expectedVersion, federation.getFormatVersion());
        }

        private Federation createP2shP2wshErpFederation() {
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

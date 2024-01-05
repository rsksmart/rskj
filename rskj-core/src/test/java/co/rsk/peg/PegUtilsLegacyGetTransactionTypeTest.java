package co.rsk.peg;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeMainNetConstants;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.federation.*;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static co.rsk.peg.PegTestUtils.createFederation;
import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP186;
import static org.mockito.Mockito.mock;

class PegUtilsLegacyGetTransactionTypeTest {
    private static final BridgeConstants bridgeMainnetConstants = BridgeMainNetConstants.getInstance();
    private static final NetworkParameters btcMainnetParams = bridgeMainnetConstants.getBtcParams();

    private static final Address oldFederationAddress = Address.fromBase58(
        bridgeMainnetConstants.getBtcParams(),
        bridgeMainnetConstants.getOldFederationAddress()
    );

    private static Context btcContext;

    @BeforeEach
    void setUp() {
        btcContext = new Context(btcMainnetParams);
        Context.propagate(btcContext);
    }

    @Test
    void test_sentFromP2SHErpFed() {
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.hop400().forBlock(0);

        BtcECKey federator0PublicKey = BtcECKey.fromPublicOnly(Hex.decode("03b53899c390573471ba30e5054f78376c5f797fda26dde7a760789f02908cbad2"));
        BtcECKey federator1PublicKey = BtcECKey.fromPublicOnly(Hex.decode("027319afb15481dbeb3c426bcc37f9a30e7f51ceff586936d85548d9395bcc2344"));
        BtcECKey federator2PublicKey = BtcECKey.fromPublicOnly(Hex.decode("0355a2e9bf100c00fc0a214afd1bf272647c7824eb9cb055480962f0c382596a70"));
        BtcECKey federator3PublicKey = BtcECKey.fromPublicOnly(Hex.decode("02566d5ded7c7db1aa7ee4ef6f76989fb42527fcfdcddcd447d6793b7d869e46f7"));
        BtcECKey federator4PublicKey = BtcECKey.fromPublicOnly(Hex.decode("0294c817150f78607566e961b3c71df53a22022a80acbb982f83c0c8baac040adc"));
        BtcECKey federator5PublicKey = BtcECKey.fromPublicOnly(Hex.decode("0372cd46831f3b6afd4c044d160b7667e8ebf659d6cb51a825a3104df6ee0638c6"));
        BtcECKey federator6PublicKey = BtcECKey.fromPublicOnly(Hex.decode("0340df69f28d69eef60845da7d81ff60a9060d4da35c767f017b0dd4e20448fb44"));
        BtcECKey federator7PublicKey = BtcECKey.fromPublicOnly(Hex.decode("02ac1901b6fba2c1dbd47d894d2bd76c8ba1d296d65f6ab47f1c6b22afb53e73eb"));
        BtcECKey federator8PublicKey = BtcECKey.fromPublicOnly(Hex.decode("031aabbeb9b27258f98c2bf21f36677ae7bae09eb2d8c958ef41a20a6e88626d26"));
        List<BtcECKey> standardKeys = Arrays.asList(
            federator0PublicKey, federator1PublicKey, federator2PublicKey,
            federator3PublicKey, federator4PublicKey, federator5PublicKey,
            federator6PublicKey, federator7PublicKey, federator8PublicKey
        );
        ErpFederationArgs activeFedArgs = new ErpFederationArgs(
            FederationTestUtils.getFederationMembersWithBtcKeys(standardKeys),
            bridgeMainnetConstants.getGenesisFederation().getCreationTime(),
            5L,
            bridgeMainnetConstants.getGenesisFederation().getBtcParams(),
            bridgeMainnetConstants.getErpFedPubKeysList(),
            bridgeMainnetConstants.getErpFedActivationDelay()
        );

        // Arrange
        ErpFederation activeFederation = FederationFactory.buildP2shErpFederation(
            activeFedArgs
        );

        List<BtcECKey> fedKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa01", "fa02", "fa03"}, true
        );

        ErpFederationArgs retiringFedArgs = new ErpFederationArgs(FederationTestUtils.getFederationMembersWithBtcKeys(fedKeys),
            Instant.ofEpochMilli(1000L),
            0L,
            btcMainnetParams,
            bridgeMainnetConstants.getErpFedPubKeysList(),
            bridgeMainnetConstants.getErpFedActivationDelay()
        );
        ErpFederation p2shRetiringFederation = FederationFactory.buildP2shErpFederation(
            retiringFedArgs
        );

        // Create a migrationTx from the p2sh erp fed
        Coin minimumPeginTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);

        BtcTransaction migrationTx = new BtcTransaction(btcMainnetParams);
        migrationTx.addInput(Sha256Hash.ZERO_HASH, 0, p2shRetiringFederation.getRedeemScript());
        migrationTx.addOutput(minimumPeginTxValue, activeFederation.getAddress());

        FederationTestUtils.addSignatures(p2shRetiringFederation, fedKeys, migrationTx);

        // Act
        PegTxType transactionType = PegUtilsLegacy.getTransactionType(
            migrationTx,
            activeFederation,
            p2shRetiringFederation,
            null,
            oldFederationAddress,
            activations,
            minimumPeginTxValue,
            new BridgeBtcWallet(btcContext, Arrays.asList(activeFederation, p2shRetiringFederation))
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGOUT_OR_MIGRATION, transactionType);
    }

    private static Stream<Arguments> test_sentFromOldFed_Args() {
        return Stream.of(
            Arguments.of(ActivationConfigsForTest.papyrus200().forBlock(0), PegTxType.PEGIN),
            Arguments.of(ActivationConfigsForTest.iris300().forBlock(0), PegTxType.PEGOUT_OR_MIGRATION)
        );
    }

    @ParameterizedTest
    @MethodSource("test_sentFromOldFed_Args")
    void test_sentFromOldFed(ActivationConfig.ForBlock activations, PegTxType expectedTxType) {
        BridgeConstants bridgeRegTestConstants = BridgeRegTestConstants.getInstance();
        NetworkParameters btcRegTestsParams = bridgeRegTestConstants.getBtcParams();
        Context.propagate(new Context(btcRegTestsParams));

        final List<BtcECKey> REGTEST_OLD_FEDERATION_PRIVATE_KEYS = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("47129ffed2c0273c75d21bb8ba020073bb9a1638df0e04853407461fdd9e8b83")),
            BtcECKey.fromPrivate(Hex.decode("9f72d27ba603cfab5a0201974a6783ca2476ec3d6b4e2625282c682e0e5f1c35")),
            BtcECKey.fromPrivate(Hex.decode("e1b17fcd0ef1942465eee61b20561b16750191143d365e71de08b33dd84a9788"))
        );

        // Arrange
        FederationArgs args = new FederationArgs(bridgeRegTestConstants.getGenesisFederation().getMembers(),
            bridgeRegTestConstants.getGenesisFederation().getCreationTime(),
            5L,
            bridgeRegTestConstants.getGenesisFederation().getBtcParams());
        Federation activeFederation = FederationFactory.buildStandardMultiSigFederation(
            args
        );

        Federation retiredFederation = createFederation(bridgeRegTestConstants, REGTEST_OLD_FEDERATION_PRIVATE_KEYS);

        // Create a migrationTx from the old fed address to the active fed
        BtcTransaction migrationTx = new BtcTransaction(btcRegTestsParams);
        migrationTx.addInput(BitcoinTestUtils.createHash(1), 0, retiredFederation.getRedeemScript());
        migrationTx.addOutput(Coin.COIN, activeFederation.getAddress());

        FederationTestUtils.addSignatures(retiredFederation, REGTEST_OLD_FEDERATION_PRIVATE_KEYS, migrationTx);

        // Act
        PegTxType transactionType = PegUtilsLegacy.getTransactionType(
            migrationTx,
            activeFederation,
            null,
            activations.isActive(RSKIP186)? retiredFederation.getP2SHScript(): null,
            oldFederationAddress,
            activations,
            bridgeRegTestConstants.getMinimumPeginTxValue(activations),
            new BridgeBtcWallet(btcContext, Collections.singletonList(activeFederation))
        );

        // Assert
        Assertions.assertEquals(bridgeRegTestConstants.getOldFederationAddress(), retiredFederation.getAddress().toString());
        Assertions.assertEquals(expectedTxType, transactionType);
    }

    @Test
    void test_sentFromP2SH_pegin() {
        // Arrange
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.hop400().forBlock(0);

        Federation activeFederation = bridgeMainnetConstants.getGenesisFederation();

        List<BtcECKey> unknownFedSigners = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"key1", "key2", "key3"}, true
        );
        Federation unknownFed = createFederation(bridgeMainnetConstants, unknownFedSigners);

        // Create a peginTx from the p2sh erp fed
        Coin minimumPeginTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);

        BtcTransaction peginTx = new BtcTransaction(btcMainnetParams);
        peginTx.addInput(
            BitcoinTestUtils.createHash(1),
            0,
            ScriptBuilder.createP2SHMultiSigInputScript(null, unknownFed.getRedeemScript())
        );
        peginTx.addOutput(minimumPeginTxValue, activeFederation.getAddress());

        FederationTestUtils.addSignatures(unknownFed, unknownFedSigners, peginTx);

        // Act
        PegTxType transactionType = PegUtilsLegacy.getTransactionType(
            peginTx,
            activeFederation,
            null,
            null,
            oldFederationAddress,
            activations,
            bridgeMainnetConstants.getMinimumPeginTxValue(activations),
            new BridgeBtcWallet(btcContext, Collections.singletonList(activeFederation))
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGIN, transactionType);
    }

    private static Stream<Arguments> pegin_ok_Args() {
        ActivationConfig.ForBlock papyrusActivations = ActivationConfigsForTest.papyrus200().forBlock(0);
        ActivationConfig.ForBlock iris300Activations = ActivationConfigsForTest.iris300().forBlock(0);

        List<BtcECKey> retiredFedSigningKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa07", "fa08", "fa09"}, true
        );

        Federation retiredFederation = createFederation(bridgeMainnetConstants, retiredFedSigningKeys);

        Script retiredFederationP2SHScript = retiredFederation.getP2SHScript();

        List<BtcECKey> retiringFedKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa01", "fa02", "fa03"}, true
        );

        Federation retiringFederation = createFederation(bridgeMainnetConstants, retiringFedKeys);


        return Stream.of(
            Arguments.of(
                papyrusActivations,
                null,
                null,
                PegTxType.PEGIN,
                bridgeMainnetConstants.getMinimumPeginTxValue(papyrusActivations)
            ),
            Arguments.of(
                papyrusActivations,
                retiringFederation,
                null,
                PegTxType.PEGIN,
                bridgeMainnetConstants.getMinimumPeginTxValue(papyrusActivations)
            ),
            Arguments.of(
                papyrusActivations,
                retiringFederation,
                retiredFederationP2SHScript,
                PegTxType.PEGIN,
                bridgeMainnetConstants.getMinimumPeginTxValue(papyrusActivations)
            ),

            Arguments.of(
                papyrusActivations,
                null,
                null,
                PegTxType.UNKNOWN,
                bridgeMainnetConstants.getMinimumPeginTxValue(papyrusActivations).minus(Coin.SATOSHI)
            ),
            Arguments.of(
                papyrusActivations,
                retiringFederation,
                null,
                PegTxType.UNKNOWN,
                bridgeMainnetConstants.getMinimumPeginTxValue(papyrusActivations).minus(Coin.SATOSHI)
            ),
            Arguments.of(
                papyrusActivations,
                retiringFederation,
                retiredFederationP2SHScript,
                PegTxType.UNKNOWN,
                bridgeMainnetConstants.getMinimumPeginTxValue(papyrusActivations).minus(Coin.SATOSHI)
            ),


            Arguments.of(
                iris300Activations,
                null,
                null,
                PegTxType.PEGIN,
                bridgeMainnetConstants.getMinimumPeginTxValue(iris300Activations)
            ),
            Arguments.of(
                iris300Activations,
                retiringFederation,
                null,
                PegTxType.PEGIN,
                bridgeMainnetConstants.getMinimumPeginTxValue(iris300Activations)
            ),
            Arguments.of(
                iris300Activations,
                retiringFederation,
                retiredFederationP2SHScript,
                PegTxType.PEGIN,
                bridgeMainnetConstants.getMinimumPeginTxValue(iris300Activations)
            ),

            Arguments.of(
                iris300Activations,
                null,
                null,
                PegTxType.UNKNOWN,
                bridgeMainnetConstants.getMinimumPeginTxValue(iris300Activations).minus(Coin.SATOSHI)
            ),
            Arguments.of(
                iris300Activations,
                retiringFederation,
                null,
                PegTxType.UNKNOWN,
                bridgeMainnetConstants.getMinimumPeginTxValue(iris300Activations).minus(Coin.SATOSHI)
            ),
            Arguments.of(
                iris300Activations,
                retiringFederation,
                retiredFederationP2SHScript,
                PegTxType.UNKNOWN,
                bridgeMainnetConstants.getMinimumPeginTxValue(iris300Activations).minus(Coin.SATOSHI)
            )
        );
    }

    @ParameterizedTest
    @MethodSource("pegin_ok_Args")
    void test_pegin(
        ActivationConfig.ForBlock activations,
        Federation retiringFederation,
        Script retiredFederationP2SHScript,
        PegTxType expectedTxType,
        Coin amountToSend
    ) {
        // Arrange

        List<BtcECKey> activeFedKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa01", "fa02", "fa03"}, true
        );

        List<BtcECKey> erpFedKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa04", "fa05", "fa06"}, true
        );

        ErpFederationArgs args = new ErpFederationArgs(
            FederationTestUtils.getFederationMembersWithBtcKeys(activeFedKeys),
            Instant.ofEpochMilli(1000L),
            0L,
            btcMainnetParams,
            erpFedKeys,
            100L
        );
        ErpFederation activeFederation = FederationFactory.buildP2shErpFederation(
            args
        );

        BtcTransaction peginTx = new BtcTransaction(btcMainnetParams);
        peginTx.addInput(BitcoinTestUtils.createHash(1), 0, new Script(new byte[]{}));
        peginTx.addOutput(amountToSend, activeFederation.getAddress());

        // Act
        PegTxType transactionType = PegUtilsLegacy.getTransactionType(
            peginTx,
            activeFederation,
            retiringFederation,
            retiredFederationP2SHScript,
            oldFederationAddress,
            activations,
            bridgeMainnetConstants.getMinimumPeginTxValue(activations),
            new BridgeBtcWallet(btcContext, Collections.singletonList(activeFederation))
        );

        // Assert
        Assertions.assertEquals(expectedTxType, transactionType);
    }

    @Test
    void test_pegout_tx() {
        // Arrange
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);

        List<BtcECKey> fedKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa01", "fa02", "fa03"}, true
        );

        FederationArgs args = new FederationArgs(FederationTestUtils.getFederationMembersWithBtcKeys(fedKeys),
            Instant.ofEpochMilli(1000L),
            0L,
            btcMainnetParams
        );
        Federation activeFederation = FederationFactory.buildStandardMultiSigFederation(
            args
        );

        // Create a pegoutBtcTx from active fed to a user btc address
        Address userAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "user");

        BtcTransaction pegoutBtcTx = new BtcTransaction(btcMainnetParams);
        pegoutBtcTx.addInput(BitcoinTestUtils.createHash(1), 0, activeFederation.getRedeemScript());
        pegoutBtcTx.addOutput(Coin.COIN, userAddress);

        FederationTestUtils.addSignatures(activeFederation, fedKeys, pegoutBtcTx);

        // Act
        PegTxType transactionType = PegUtilsLegacy.getTransactionType(
            pegoutBtcTx,
            activeFederation,
            null,
            null,
            oldFederationAddress,
            activations,
            bridgeMainnetConstants.getMinimumPeginTxValue(activations),
            new BridgeBtcWallet(btcContext, Collections.singletonList(activeFederation))
        );

        //Assert
        Assertions.assertEquals(PegTxType.PEGOUT_OR_MIGRATION, transactionType);
    }

    @Test
    void test_migration_tx() {
        // Arrange
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);

        Federation activeFederation = FederationTestUtils.getFederation(100, 200, 300);

        List<BtcECKey> retiringFedKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa01", "fa02", "fa03"}, true
        );

        FederationArgs args = new FederationArgs(
            FederationTestUtils.getFederationMembersWithBtcKeys(retiringFedKeys),
            Instant.ofEpochMilli(1000L),
            0L,
            btcMainnetParams
        );
        Federation retiringFederation = FederationFactory.buildStandardMultiSigFederation(
            args
        );

        BtcTransaction migrationTx = new BtcTransaction(btcMainnetParams);
        migrationTx.addInput(BitcoinTestUtils.createHash(1), 0, retiringFederation.getRedeemScript());
        migrationTx.addOutput(Coin.COIN, activeFederation.getAddress());

        FederationTestUtils.addSignatures(retiringFederation, retiringFedKeys, migrationTx);

        // Act
        PegTxType transactionType = PegUtilsLegacy.getTransactionType(
            migrationTx,
            activeFederation,
            retiringFederation,
            null,
            oldFederationAddress,
            activations,
            bridgeMainnetConstants.getMinimumPeginTxValue(activations),
            new BridgeBtcWallet(btcContext, Arrays.asList(activeFederation, retiringFederation))
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGOUT_OR_MIGRATION, transactionType);
    }

    @Test
    void test_migration_to_p2shFed_tx() {
        // Arrange
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);

        List<BtcECKey> retiringFedKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa01", "fa02", "fa03"}, true
        );

        FederationArgs args = new FederationArgs(FederationTestUtils.getFederationMembersWithBtcKeys(retiringFedKeys),
            Instant.ofEpochMilli(1000L),
            0L,
            btcMainnetParams
        );
        Federation retiringFederation = FederationFactory.buildStandardMultiSigFederation(
            args
        );

        List<BtcECKey> activeFedKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa04", "fa05", "fa06"}, true
        );

        Federation activeFederation = PegTestUtils.createP2shErpFederation(bridgeMainnetConstants, activeFedKeys);

        BtcTransaction migrationTx = new BtcTransaction(btcMainnetParams);
        migrationTx.addInput(BitcoinTestUtils.createHash(1), 0, retiringFederation.getRedeemScript());
        migrationTx.addOutput(Coin.COIN, activeFederation.getAddress());

        FederationTestUtils.addSignatures(retiringFederation, retiringFedKeys, migrationTx);

        // Act
        PegTxType transactionType = PegUtilsLegacy.getTransactionType(
            migrationTx,
            activeFederation,
            retiringFederation,
            null,
            oldFederationAddress,
            activations,
            bridgeMainnetConstants.getMinimumPeginTxValue(activations),
            new BridgeBtcWallet(btcContext, Arrays.asList(activeFederation, retiringFederation))
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGOUT_OR_MIGRATION, transactionType);
    }

    @Test
    void test_migration_from_and_to_p2shFed_tx() {
        // Arrange
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.hop400().forBlock(0);

        List<BtcECKey> retiringFedKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa01", "fa02", "fa03"}, true
        );

        Federation retiringFederation = PegTestUtils.createP2shErpFederation(bridgeMainnetConstants, retiringFedKeys);

        List<BtcECKey> activeFedKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa04", "fa05", "fa06"}, true
        );

        Federation activeFederation = PegTestUtils.createP2shErpFederation(bridgeMainnetConstants, activeFedKeys);

        BtcTransaction migrationTx = new BtcTransaction(btcMainnetParams);
        migrationTx.addInput(BitcoinTestUtils.createHash(1), 0, retiringFederation.getRedeemScript());
        migrationTx.addOutput(Coin.COIN, activeFederation.getAddress());

        FederationTestUtils.addSignatures(retiringFederation, retiringFedKeys, migrationTx);

        // Act
        PegTxType transactionType = PegUtilsLegacy.getTransactionType(
            migrationTx,
            activeFederation,
            retiringFederation,
            null,
            oldFederationAddress,
            activations,
            bridgeMainnetConstants.getMinimumPeginTxValue(activations),
            new BridgeBtcWallet(btcContext, Arrays.asList(activeFederation, retiringFederation))
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGOUT_OR_MIGRATION, transactionType);
    }

    @Test
    void test_unknown_tx() {
        // Arrange
        Federation activeFederation = bridgeMainnetConstants.getGenesisFederation();

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);

        Address unknownAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "unknown");

        BtcTransaction unknownPegTx = new BtcTransaction(btcMainnetParams);
        unknownPegTx.addInput(BitcoinTestUtils.createHash(1), 0, new Script(new byte[]{}));
        unknownPegTx.addOutput(Coin.COIN, unknownAddress);

        // Act
        PegTxType transactionType = PegUtilsLegacy.getTransactionType(
            unknownPegTx,
            activeFederation,
            null,
            null,
            oldFederationAddress,
            activations,
            bridgeMainnetConstants.getMinimumPeginTxValue(activations),
            new BridgeBtcWallet(btcContext, Collections.singletonList(activeFederation))
        );

        // Assert
        Assertions.assertEquals(PegTxType.UNKNOWN, transactionType);
    }
}

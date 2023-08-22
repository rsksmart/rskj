package co.rsk.peg;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.TransactionInput;
import co.rsk.bitcoinj.core.TransactionOutPoint;
import co.rsk.bitcoinj.script.Script;
import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeMainNetConstants;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.test.builders.BridgeSupportBuilder;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static co.rsk.peg.PegTestUtils.createBaseInputScriptThatSpendsFromTheFederation;
import static co.rsk.peg.PegTestUtils.createFederation;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BridgeSupportGetTransactionTypeTest {
    private final BridgeConstants bridgeConstants = BridgeMainNetConstants.getInstance();
    private final NetworkParameters btcParams = bridgeConstants.getBtcParams();

    private static final List<BtcECKey> REGTEST_OLD_FEDERATION_PRIVATE_KEYS = Arrays.asList(
        BtcECKey.fromPrivate(Hex.decode("47129ffed2c0273c75d21bb8ba020073bb9a1638df0e04853407461fdd9e8b83")),
        BtcECKey.fromPrivate(Hex.decode("9f72d27ba603cfab5a0201974a6783ca2476ec3d6b4e2625282c682e0e5f1c35")),
        BtcECKey.fromPrivate(Hex.decode("e1b17fcd0ef1942465eee61b20561b16750191143d365e71de08b33dd84a9788"))
    );

    private static Stream<Arguments> getTransactionType_sentFromP2SHErpFed_Args() {
        return Stream.of(
            Arguments.of(ActivationConfigsForTest.papyrus200().forBlock(0), PegTxType.PEGIN),
            Arguments.of(ActivationConfigsForTest.iris300().forBlock(0), PegTxType.PEGOUT_OR_MIGRATION)
        );
    }

    @ParameterizedTest
    @MethodSource("getTransactionType_sentFromP2SHErpFed_Args")
    void getTransactionType_sentFromP2SHErpFed(ActivationConfig.ForBlock activations, PegTxType expectedTxType) {
        // Arrange
        Federation activeFederation = new P2shErpFederation(
            bridgeConstants.getGenesisFederation().getMembers(),
            bridgeConstants.getGenesisFederation().getCreationTime(),
            5L,
            bridgeConstants.getGenesisFederation().getBtcParams(),
            bridgeConstants.getErpFedPubKeysList(),
            bridgeConstants.getErpFedActivationDelay(),
            activations
        );

        Block rskCurrentBlock = new BlockGenerator().createBlock(
            bridgeConstants.getFederationActivationAge(activations) + activeFederation.getCreationBlockNumber(),
            1
        );

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BridgeSupport bridgeSupport = new BridgeSupportBuilder()
            .withBridgeConstants(bridgeConstants)
            .withActivations(activations)
            .withProvider(provider)
            .withExecutionBlock(rskCurrentBlock)
            .build();

        when(provider.getNewFederation()).thenReturn(activeFederation);

        List<BtcECKey> fedKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02")),
            BtcECKey.fromPrivate(Hex.decode("fa03"))
        );
        fedKeys.sort(BtcECKey.PUBKEY_COMPARATOR);

        P2shErpFederation p2shRetiringFederation = new P2shErpFederation(
            FederationTestUtils.getFederationMembersWithBtcKeys(fedKeys),
            Instant.ofEpochMilli(1000L),
            0L,
            bridgeConstants.getBtcParams(),
            bridgeConstants.getErpFedPubKeysList(),
            bridgeConstants.getErpFedActivationDelay(),
            activations
        );
        when(provider.getOldFederation()).thenReturn(p2shRetiringFederation);

        // Create a migrationTx from the p2sh erp fed
        BtcTransaction migrationTx = new BtcTransaction(bridgeConstants.getBtcParams());
        migrationTx.addOutput(bridgeConstants.getMinimumPeginTxValue(activations), activeFederation.getAddress());
        migrationTx.addInput(Sha256Hash.ZERO_HASH, 0, p2shRetiringFederation.getRedeemScript());

        FederationTestUtils.addSignatures(p2shRetiringFederation, fedKeys, migrationTx);

        // Act
        PegTxType transactionType = bridgeSupport.getTransactionType(migrationTx);

        // Assert
        Assertions.assertEquals(expectedTxType, transactionType);
    }

    private static Stream<Arguments> getTransactionType_sentFromOldFed_Args() {
        return Stream.of(
            Arguments.of(ActivationConfigsForTest.papyrus200().forBlock(0), PegTxType.PEGIN),
            Arguments.of(ActivationConfigsForTest.iris300().forBlock(0), PegTxType.PEGOUT_OR_MIGRATION)
        );
    }

    @ParameterizedTest
    @MethodSource("getTransactionType_sentFromOldFed_Args")
    void getTransactionType_sentFromOldFed(ActivationConfig.ForBlock activations, PegTxType expectedTxType) {
        BridgeConstants bridgeConstants = BridgeRegTestConstants.getInstance();

        // Arrange
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        BridgeSupport bridgeSupport = new BridgeSupportBuilder()
            .withBridgeConstants(bridgeConstants)
            .withActivations(activations)
            .withProvider(provider)
            .build();

        Federation activeFederation = new Federation(
            bridgeConstants.getGenesisFederation().getMembers(),
            bridgeConstants.getGenesisFederation().getCreationTime(),
            5L,
            bridgeConstants.getGenesisFederation().getBtcParams()
        );
        when(provider.getNewFederation()).thenReturn(activeFederation);


        Federation retiredFederation = createFederation(bridgeConstants, REGTEST_OLD_FEDERATION_PRIVATE_KEYS);

        Optional<Script> lastRetiredFederationP2SHScript = Optional.empty();
        if (activations.isActive(ConsensusRule.RSKIP186)){
            lastRetiredFederationP2SHScript = Optional.of(retiredFederation.getP2SHScript());
        }
        when(provider.getLastRetiredFederationP2SHScript()).thenReturn(lastRetiredFederationP2SHScript);

        // Create a migrationTx from the old fed address to the active fed
        BtcTransaction migrationTx = new BtcTransaction(btcParams);
        migrationTx.addOutput(Coin.COIN, activeFederation.getAddress());

        TransactionInput migrationTxInput = new TransactionInput(
            btcParams,
            null,
            new byte[]{},
            new TransactionOutPoint(
                btcParams,
                0,
                Sha256Hash.ZERO_HASH
            )
        );
        migrationTx.addInput(migrationTxInput);
        Script fedInputScript = createBaseInputScriptThatSpendsFromTheFederation(retiredFederation);
        migrationTxInput.setScriptSig(fedInputScript);

        FederationTestUtils.addSignatures(retiredFederation, REGTEST_OLD_FEDERATION_PRIVATE_KEYS, migrationTx);

        // Act
        PegTxType transactionType = bridgeSupport.getTransactionType(migrationTx);

        // Assert
        Assertions.assertEquals(bridgeConstants.getOldFederationAddress(), retiredFederation.getAddress().toString());
        Assertions.assertEquals(expectedTxType, transactionType);
    }

    @Test
    void getTransactionType_sentFromP2SH_pegin() {
        // Arrange
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.hop400().forBlock(0);

        BridgeSupport bridgeSupport = new BridgeSupportBuilder()
            .withBridgeConstants(bridgeConstants)
            .withActivations(activations)
            .build();

        Federation activeFederation = bridgeConstants.getGenesisFederation();

        List<BtcECKey> p2shFedKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02")),
            BtcECKey.fromPrivate(Hex.decode("fa03"))
        );
        p2shFedKeys.sort(BtcECKey.PUBKEY_COMPARATOR);

        P2shErpFederation p2shErpFederation = new P2shErpFederation(
            FederationTestUtils.getFederationMembersWithBtcKeys(p2shFedKeys),
            Instant.ofEpochMilli(1000L),
            0L,
            bridgeConstants.getBtcParams(),
            bridgeConstants.getErpFedPubKeysList(),
            bridgeConstants.getErpFedActivationDelay(),
            activations
        );

        // Create a peginTx from the p2sh erp fed
        BtcTransaction peginTx = new BtcTransaction(bridgeConstants.getBtcParams());
        peginTx.addOutput(bridgeConstants.getMinimumPeginTxValue(activations), activeFederation.getAddress());
        peginTx.addInput(Sha256Hash.ZERO_HASH, 0, p2shErpFederation.getRedeemScript());

        FederationTestUtils.addSignatures(p2shErpFederation, p2shFedKeys, peginTx);

        // Act
        PegTxType transactionType = bridgeSupport.getTransactionType(peginTx);

        // Assert
        Assertions.assertEquals(PegTxType.PEGIN, transactionType);
    }

    private static Stream<Arguments> getTransactionType_pegin_Args() {
        BridgeConstants bridgeConstantsMainnet = BridgeMainNetConstants.getInstance();

        ActivationConfig.ForBlock papyrusActivations = ActivationConfigsForTest.papyrus200().forBlock(0);
        ActivationConfig.ForBlock iris300Activations = ActivationConfigsForTest.iris300().forBlock(0);

        return Stream.of(
            Arguments.of(
                papyrusActivations,
                PegTxType.PEGIN,
                bridgeConstantsMainnet.getMinimumPeginTxValue(papyrusActivations)
            ),
            Arguments.of(
                papyrusActivations,
                PegTxType.UNKNOWN,
                bridgeConstantsMainnet.getMinimumPeginTxValue(papyrusActivations).minus(Coin.SATOSHI)
            ),
            Arguments.of(
                iris300Activations,
                PegTxType.PEGIN,
                bridgeConstantsMainnet.getMinimumPeginTxValue(iris300Activations)
            ),
            Arguments.of(
                iris300Activations,
                PegTxType.UNKNOWN,
                bridgeConstantsMainnet.getMinimumPeginTxValue(iris300Activations).minus(Coin.SATOSHI)
            )
        );
    }

    @ParameterizedTest
    @MethodSource("getTransactionType_pegin_Args")
    void getTransactionType_pegin(
        ActivationConfig.ForBlock activations,
        PegTxType expectedTxType,
        Coin amountToSend
    ) {
        // Arrange
        BridgeSupport bridgeSupport = new BridgeSupportBuilder()
            .withBridgeConstants(bridgeConstants)
            .withActivations(activations)
            .build();
        BtcTransaction btcTx = new BtcTransaction(btcParams);

        btcTx.addOutput(amountToSend, bridgeConstants.getGenesisFederation().getAddress());
        btcTx.addInput(PegTestUtils.createHash(1), 0, new Script(new byte[]{}));

        // Act
        PegTxType transactionType = bridgeSupport.getTransactionType(btcTx);

        // Assert
        Assertions.assertEquals(expectedTxType, transactionType);
    }

    @Test
    void getTransactionType_pegout_tx() {
        // Arrange
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BridgeSupport bridgeSupport = new BridgeSupportBuilder()
            .withBridgeConstants(bridgeConstants)
            .withProvider(provider)
            .build();

        List<BtcECKey> fedKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02")),
            BtcECKey.fromPrivate(Hex.decode("fa03"))
        );
        fedKeys.sort(BtcECKey.PUBKEY_COMPARATOR);

        Federation activeFederation = new Federation(
            FederationTestUtils.getFederationMembersWithBtcKeys(fedKeys),
            Instant.ofEpochMilli(1000L),
            0L,
            bridgeConstants.getBtcParams()
        );
        when(provider.getNewFederation()).thenReturn(activeFederation);

        Address userAddress = new Address(
            btcParams,
            Hex.decode("4a22c3c4cbb31e4d03b15550636762bda0baf85a")
        );

        // Create a tx from the Fed to a random btc address
        BtcTransaction pegoutBtcTx = new BtcTransaction(btcParams);
        pegoutBtcTx.addOutput(Coin.COIN, userAddress);
        TransactionInput pegoutInput = new TransactionInput(
            btcParams,
            pegoutBtcTx,
            new byte[]{},
            new TransactionOutPoint(
                btcParams,
                0,
                Sha256Hash.ZERO_HASH
            )
        );
        pegoutBtcTx.addInput(pegoutInput);

        FederationTestUtils.addSignatures(activeFederation, fedKeys, pegoutBtcTx);

        // Act
        PegTxType resultTxType = bridgeSupport.getTransactionType(pegoutBtcTx);

        //Assert
        Assertions.assertEquals(PegTxType.PEGOUT_OR_MIGRATION, resultTxType);
    }

    @Test
    void getTransactionType_migration_tx() {
        // Arrange
        long federationActivationAge = bridgeConstants.getFederationActivationAge(mock(ActivationConfig.ForBlock.class)) + 5L;
        Block rskCurrentBlock = new BlockGenerator().createBlock(federationActivationAge, 1);
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BridgeSupport bridgeSupport = new BridgeSupportBuilder()
            .withBridgeConstants(bridgeConstants)
            .withProvider(provider)
            .withExecutionBlock(rskCurrentBlock)
            .build();

        Federation activeFederation = FederationTestUtils.getFederation(100, 200, 300);
        when(provider.getNewFederation()).thenReturn(activeFederation);

        List<BtcECKey> retiringFedKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02")),
            BtcECKey.fromPrivate(Hex.decode("fa03"))
        );
        retiringFedKeys.sort(BtcECKey.PUBKEY_COMPARATOR);

        Federation retiringFederation = new Federation(
            FederationTestUtils.getFederationMembersWithBtcKeys(retiringFedKeys),
            Instant.ofEpochMilli(1000L),
            0L,
            bridgeConstants.getBtcParams()
        );
        when(provider.getOldFederation()).thenReturn(retiringFederation);

        BtcTransaction migrationTx = new BtcTransaction(btcParams);
        migrationTx.addOutput(Coin.COIN, activeFederation.getAddress());
        TransactionInput migrationTxInput = new TransactionInput(
            btcParams,
            null,
            new byte[]{},
            new TransactionOutPoint(
                btcParams,
                0,
                Sha256Hash.ZERO_HASH
            )
        );
        migrationTx.addInput(migrationTxInput);

        FederationTestUtils.addSignatures(retiringFederation, retiringFedKeys, migrationTx);

        // Act
        PegTxType transactionType = bridgeSupport.getTransactionType(migrationTx);

        // Assert
        Assertions.assertEquals(PegTxType.PEGOUT_OR_MIGRATION, transactionType);
    }

    @Test
    void getTransactionType_unknown_tx() {
        // Arrange
        BridgeSupport bridgeSupport = new BridgeSupportBuilder()
            .withBridgeConstants(bridgeConstants)
            .build();

        BtcTransaction unknownPegTx = new BtcTransaction(btcParams);
        unknownPegTx.addOutput(Coin.COIN, BitcoinTestUtils.createP2PKHAddress(btcParams, "unknown"));
        TransactionInput transactionInput = new TransactionInput(
            btcParams,
            null,
            new byte[]{},
            new TransactionOutPoint(
                btcParams,
                0,
                Sha256Hash.ZERO_HASH
            )
        );
        unknownPegTx.addInput(transactionInput);

        // Act
        PegTxType transactionType = bridgeSupport.getTransactionType(unknownPegTx);

        // Assert
        Assertions.assertEquals(PegTxType.UNKNOWN, transactionType);
    }
}

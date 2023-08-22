package co.rsk.peg;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
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

import static co.rsk.peg.PegTestUtils.createFederation;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BridgeSupportGetTransactionTypeTest {
    private static final BridgeConstants bridgeMainnetConstants = BridgeMainNetConstants.getInstance();
    private static final NetworkParameters btcMainnetParams = bridgeMainnetConstants.getBtcParams();

    private static final List<BtcECKey> REGTEST_OLD_FEDERATION_PRIVATE_KEYS = Arrays.asList(
        BtcECKey.fromPrivate(Hex.decode("47129ffed2c0273c75d21bb8ba020073bb9a1638df0e04853407461fdd9e8b83")),
        BtcECKey.fromPrivate(Hex.decode("9f72d27ba603cfab5a0201974a6783ca2476ec3d6b4e2625282c682e0e5f1c35")),
        BtcECKey.fromPrivate(Hex.decode("e1b17fcd0ef1942465eee61b20561b16750191143d365e71de08b33dd84a9788"))
    );

    @Test
    void getTransactionType_sentFromP2SHErpFed() {
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.hop400().forBlock(0);

        // Arrange
        Federation activeFederation = new P2shErpFederation(
            bridgeMainnetConstants.getGenesisFederation().getMembers(),
            bridgeMainnetConstants.getGenesisFederation().getCreationTime(),
            5L,
            bridgeMainnetConstants.getGenesisFederation().getBtcParams(),
            bridgeMainnetConstants.getErpFedPubKeysList(),
            bridgeMainnetConstants.getErpFedActivationDelay(),
            activations
        );

        Block rskCurrentBlock = new BlockGenerator().createBlock(
            bridgeMainnetConstants.getFederationActivationAge(activations) + activeFederation.getCreationBlockNumber(),
            1
        );

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BridgeSupport bridgeSupport = new BridgeSupportBuilder()
            .withBridgeConstants(bridgeMainnetConstants)
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
            btcMainnetParams,
            bridgeMainnetConstants.getErpFedPubKeysList(),
            bridgeMainnetConstants.getErpFedActivationDelay(),
            activations
        );
        when(provider.getOldFederation()).thenReturn(p2shRetiringFederation);

        // Create a migrationTx from the p2sh erp fed
        Coin minimumPeginTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);

        BtcTransaction migrationTx = new BtcTransaction(btcMainnetParams);
        migrationTx.addInput(Sha256Hash.ZERO_HASH, 0, p2shRetiringFederation.getRedeemScript());
        migrationTx.addOutput(minimumPeginTxValue, activeFederation.getAddress());

        FederationTestUtils.addSignatures(p2shRetiringFederation, fedKeys, migrationTx);

        // Act
        PegTxType transactionType = bridgeSupport.getTransactionType(migrationTx);

        // Assert
        Assertions.assertEquals(PegTxType.PEGOUT_OR_MIGRATION, transactionType);
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
        BridgeConstants bridgeRegTestConstants = BridgeRegTestConstants.getInstance();
        NetworkParameters btcRegTestsParams = bridgeRegTestConstants.getBtcParams();

        // Arrange
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        BridgeSupport bridgeSupport = new BridgeSupportBuilder()
            .withBridgeConstants(bridgeRegTestConstants)
            .withActivations(activations)
            .withProvider(provider)
            .build();

        Federation activeFederation = new Federation(
            bridgeRegTestConstants.getGenesisFederation().getMembers(),
            bridgeRegTestConstants.getGenesisFederation().getCreationTime(),
            5L,
            bridgeRegTestConstants.getGenesisFederation().getBtcParams()
        );
        when(provider.getNewFederation()).thenReturn(activeFederation);


        Federation retiredFederation = createFederation(bridgeRegTestConstants, REGTEST_OLD_FEDERATION_PRIVATE_KEYS);

        Optional<Script> lastRetiredFederationP2SHScript = Optional.empty();
        if (activations.isActive(ConsensusRule.RSKIP186)){
            lastRetiredFederationP2SHScript = Optional.of(retiredFederation.getP2SHScript());
        }
        when(provider.getLastRetiredFederationP2SHScript()).thenReturn(lastRetiredFederationP2SHScript);

        // Create a migrationTx from the old fed address to the active fed
        BtcTransaction migrationTx = new BtcTransaction(btcRegTestsParams);
        migrationTx.addInput(PegTestUtils.createHash(1), 0, retiredFederation.getRedeemScript());
        migrationTx.addOutput(Coin.COIN, activeFederation.getAddress());

        FederationTestUtils.addSignatures(retiredFederation, REGTEST_OLD_FEDERATION_PRIVATE_KEYS, migrationTx);

        // Act
        PegTxType transactionType = bridgeSupport.getTransactionType(migrationTx);

        // Assert
        Assertions.assertEquals(bridgeRegTestConstants.getOldFederationAddress(), retiredFederation.getAddress().toString());
        Assertions.assertEquals(expectedTxType, transactionType);
    }

    @Test
    void getTransactionType_sentFromP2SH_pegin() {
        // Arrange
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.hop400().forBlock(0);

        BridgeSupport bridgeSupport = new BridgeSupportBuilder()
            .withBridgeConstants(bridgeMainnetConstants)
            .withActivations(activations)
            .build();

        Federation activeFederation = bridgeMainnetConstants.getGenesisFederation();

        List<BtcECKey> multiSigKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02")),
            BtcECKey.fromPrivate(Hex.decode("fa03"))
        );
        multiSigKeys.sort(BtcECKey.PUBKEY_COMPARATOR);
        Script redeemScript = ScriptBuilder.createRedeemScript((multiSigKeys.size() / 2) + 1, multiSigKeys);

        // Create a peginTx from the p2sh erp fed
        Coin minimumPeginTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);

        BtcTransaction peginTx = new BtcTransaction(btcMainnetParams);
        peginTx.addInput(Sha256Hash.ZERO_HASH, 0, redeemScript);
        peginTx.addOutput(minimumPeginTxValue, activeFederation.getAddress());

        // Act
        PegTxType transactionType = bridgeSupport.getTransactionType(peginTx);

        // Assert
        Assertions.assertEquals(PegTxType.PEGIN, transactionType);
    }

    private static Stream<Arguments> getTransactionType_pegin_Args() {
        ActivationConfig.ForBlock papyrusActivations = ActivationConfigsForTest.papyrus200().forBlock(0);
        ActivationConfig.ForBlock iris300Activations = ActivationConfigsForTest.iris300().forBlock(0);

        return Stream.of(
            Arguments.of(
                papyrusActivations,
                PegTxType.PEGIN,
                bridgeMainnetConstants.getMinimumPeginTxValue(papyrusActivations)
            ),
            Arguments.of(
                papyrusActivations,
                PegTxType.UNKNOWN,
                bridgeMainnetConstants.getMinimumPeginTxValue(papyrusActivations).minus(Coin.SATOSHI)
            ),
            Arguments.of(
                iris300Activations,
                PegTxType.PEGIN,
                bridgeMainnetConstants.getMinimumPeginTxValue(iris300Activations)
            ),
            Arguments.of(
                iris300Activations,
                PegTxType.UNKNOWN,
                bridgeMainnetConstants.getMinimumPeginTxValue(iris300Activations).minus(Coin.SATOSHI)
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
            .withBridgeConstants(bridgeMainnetConstants)
            .withActivations(activations)
            .build();

        BtcTransaction peginTx = new BtcTransaction(btcMainnetParams);
        peginTx.addInput(PegTestUtils.createHash(1), 0, new Script(new byte[]{}));
        peginTx.addOutput(amountToSend, bridgeMainnetConstants.getGenesisFederation().getAddress());

        // Act
        PegTxType transactionType = bridgeSupport.getTransactionType(peginTx);

        // Assert
        Assertions.assertEquals(expectedTxType, transactionType);
    }

    @Test
    void getTransactionType_pegout_tx() {
        // Arrange
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BridgeSupport bridgeSupport = new BridgeSupportBuilder()
            .withBridgeConstants(bridgeMainnetConstants)
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
            btcMainnetParams
        );
        when(provider.getNewFederation()).thenReturn(activeFederation);

        // Create a pegoutBtcTx from active fed to a user btc address
        Address userAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "user");

        BtcTransaction pegoutBtcTx = new BtcTransaction(btcMainnetParams);
        pegoutBtcTx.addInput(PegTestUtils.createHash(1), 0, activeFederation.getRedeemScript());
        pegoutBtcTx.addOutput(Coin.COIN, userAddress);

        FederationTestUtils.addSignatures(activeFederation, fedKeys, pegoutBtcTx);

        // Act
        PegTxType resultTxType = bridgeSupport.getTransactionType(pegoutBtcTx);

        //Assert
        Assertions.assertEquals(PegTxType.PEGOUT_OR_MIGRATION, resultTxType);
    }

    @Test
    void getTransactionType_migration_tx() {
        // Arrange
        long federationActivationAge = bridgeMainnetConstants
            .getFederationActivationAge(mock(ActivationConfig.ForBlock.class)) + 5L;
        Block rskCurrentBlock = new BlockGenerator().createBlock(federationActivationAge, 1);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        BridgeSupport bridgeSupport = new BridgeSupportBuilder()
            .withBridgeConstants(bridgeMainnetConstants)
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
            btcMainnetParams
        );
        when(provider.getOldFederation()).thenReturn(retiringFederation);

        BtcTransaction migrationTx = new BtcTransaction(btcMainnetParams);
        migrationTx.addInput(PegTestUtils.createHash(1), 0, retiringFederation.getRedeemScript());
        migrationTx.addOutput(Coin.COIN, activeFederation.getAddress());

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
            .withBridgeConstants(bridgeMainnetConstants)
            .build();

        Address unknownAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "unknown");

        BtcTransaction unknownPegTx = new BtcTransaction(btcMainnetParams);
        unknownPegTx.addInput(PegTestUtils.createHash(1), 0, new Script(new byte[]{}));
        unknownPegTx.addOutput(Coin.COIN, unknownAddress);

        // Act
        PegTxType transactionType = bridgeSupport.getTransactionType(unknownPegTx);

        // Assert
        Assertions.assertEquals(PegTxType.UNKNOWN, transactionType);
    }
}

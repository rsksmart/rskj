package co.rsk.peg;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.wallet.Wallet;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeMainNetConstants;
import co.rsk.config.BridgeTestNetConstants;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.federation.*;
import co.rsk.test.builders.BridgeSupportBuilder;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static co.rsk.peg.PegTestUtils.createFederation;
import static co.rsk.peg.PegTestUtils.createP2shErpFederation;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PegUtilsTest {
    private static final BridgeConstants bridgeMainnetConstants = BridgeMainNetConstants.getInstance();
    private static final NetworkParameters btcMainnetParams = bridgeMainnetConstants.getBtcParams();
    private static final Context context = new Context(bridgeMainnetConstants.getBtcParams());
    private static final ActivationConfig.ForBlock activations = ActivationConfigsForTest.arrowhead600().forBlock(0);

    private static final int FIRST_OUTPUT_INDEX = 0;
    private static final int FIRST_INPUT_INDEX = 0;

    private BridgeStorageProvider provider;
    private Address userAddress;

    private Federation retiredFed;
    private List<BtcECKey> retiringFedSigners;
    private Federation retiringFederation;
    private List<BtcECKey> activeFedSigners;
    private Federation activeFederation;


    @BeforeEach
    void init() {
        provider = mock(BridgeStorageProvider.class);
        userAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "userAddress");

        retiredFed = bridgeMainnetConstants.getGenesisFederation();

        retiringFedSigners = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa01", "fa02", "fa03"}, true
        );
        retiringFederation = createFederation(bridgeMainnetConstants, retiringFedSigners);

        activeFedSigners = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa01", "fa02", "fa03", "fa04", "fa05"}, true
        );
        activeFederation = createP2shErpFederation(bridgeMainnetConstants, activeFedSigners);
    }

    @Test
    void test_getTransactionType_before_tbd_600() {
        // Arrange
        ActivationConfig.ForBlock fingerrootActivations = ActivationConfigsForTest.fingerroot500().forBlock(0);
        Wallet liveFederationWallet = mock(Wallet.class);
        BtcTransaction btcTransaction = mock(BtcTransaction.class);

        // Act
        IllegalStateException exception = Assertions.assertThrows(IllegalStateException.class, () -> {
            PegUtils.getTransactionTypeUsingPegoutIndex(fingerrootActivations, provider, liveFederationWallet, btcTransaction);
        });

        // Assert
        String expectedMessage = "Can't call this method before RSKIP379 activation";
        Assertions.assertEquals(expectedMessage, exception.getMessage());
    }

    @Test
    void test_getTransactionType_tx_sending_funds_to_unknown_address() {
        // Arrange
        Wallet liveFederationWallet = new BridgeBtcWallet(context, Collections.singletonList(activeFederation));

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        btcTransaction.addOutput(Coin.COIN, userAddress);

        // Act
        PegTxType pegTxType = PegUtils.getTransactionTypeUsingPegoutIndex(
            activations,
            provider,
            liveFederationWallet,
            btcTransaction
        );

        // Assert
        Assertions.assertEquals(PegTxType.UNKNOWN, pegTxType);
    }

    @Test
    void test_getTransactionType_pegin_below_minimum_active_fed() {
        // Arrange
        Federation activeFederation = bridgeMainnetConstants.getGenesisFederation();
        Wallet liveFederationWallet = new BridgeBtcWallet(context, Collections.singletonList(activeFederation));

        Coin minimumPeginTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        btcTransaction.addOutput(minimumPeginTxValue.minus(Coin.SATOSHI), activeFederation.getAddress());

        // Act
        PegTxType pegTxType = PegUtils.getTransactionTypeUsingPegoutIndex(
            activations,
            provider,
            liveFederationWallet,
            btcTransaction
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGIN, pegTxType);
    }

    @Test
    void test_getTransactionType_pegin_active_fed() {
        // Arrange
        Wallet liveFederationWallet = new BridgeBtcWallet(context, Collections.singletonList(activeFederation));

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        btcTransaction.addOutput(Coin.COIN, activeFederation.getAddress());

        // Act
        PegTxType pegTxType = PegUtils.getTransactionTypeUsingPegoutIndex(
            activations,
            provider,
            liveFederationWallet,
            btcTransaction
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGIN, pegTxType);
    }

    @Test
    void test_getTransactionType_pegin_output_to_active_fed_and_other_addresses() {
        // Arrange
        Wallet liveFederationWallet = new BridgeBtcWallet(context, Collections.singletonList(activeFederation));

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        btcTransaction.addInput(BitcoinTestUtils.createHash(2), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        btcTransaction.addOutput(Coin.COIN, userAddress);
        btcTransaction.addOutput(Coin.COIN, activeFederation.getAddress());
        btcTransaction.addOutput(Coin.COIN, BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "unknown2"));

        // Act
        PegTxType pegTxType = PegUtils.getTransactionTypeUsingPegoutIndex(
            activations,
            provider,
            liveFederationWallet,
            btcTransaction
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGIN, pegTxType);
    }

    @Test
    void test_getTransactionType_pegin_multiple_outputs_to_active_fed() {
        // Arrange
        Wallet liveFederationWallet = new BridgeBtcWallet(context, Collections.singletonList(activeFederation));

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);

        for (int i = 0; i < 10; i++) {
            btcTransaction.addInput(BitcoinTestUtils.createHash(i), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
            btcTransaction.addOutput(Coin.COIN, activeFederation.getAddress());
        }

        // Act
        PegTxType pegTxType = PegUtils.getTransactionTypeUsingPegoutIndex(
            activations,
            provider,
            liveFederationWallet,
            btcTransaction
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGIN, pegTxType);
    }

    @Test
    void test_getTransactionType_pegin_output_to_retiring_fed_and_other_addresses() {
        // Arrange
        Federation retiringFed = bridgeMainnetConstants.getGenesisFederation();

        List<BtcECKey> signers = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa01", "fa02", "fa03"}, true
        );
        List<FederationMember> fedMembers = FederationTestUtils.getFederationMembersWithBtcKeys(signers);
        Instant creationTime = Instant.ofEpochMilli(1000L);
        List<BtcECKey> erpPubKeys = bridgeMainnetConstants.getErpFedPubKeysList();
        long activationDelay = bridgeMainnetConstants.getErpFedActivationDelay();
        ErpFederationArgs erpFederationArgs =
            new ErpFederationArgs(fedMembers, creationTime, 0L, btcMainnetParams, erpPubKeys, activationDelay);
        ErpFederation activeFed = FederationFactory.buildP2shErpFederation(erpFederationArgs);
        Wallet liveFederationWallet = new BridgeBtcWallet(context, Arrays.asList(retiringFed, activeFed));

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        btcTransaction.addOutput(Coin.COIN, userAddress);
        btcTransaction.addOutput(Coin.COIN, retiringFed.getAddress());
        btcTransaction.addOutput(Coin.COIN, BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "unknown2"));

        // Act
        PegTxType pegTxType = PegUtils.getTransactionTypeUsingPegoutIndex(
            activations,
            provider,
            liveFederationWallet,
            btcTransaction
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGIN, pegTxType);
    }

    @Test
    void test_getTransactionType_pegin_retiring_fed() {
        // Arrange
        Wallet liveFederationWallet = new BridgeBtcWallet(context, Arrays.asList(retiringFederation, activeFederation));

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        btcTransaction.addOutput(Coin.COIN, retiringFederation.getAddress());

        // Act
        PegTxType pegTxType = PegUtils.getTransactionTypeUsingPegoutIndex(
            activations,
            provider,
            liveFederationWallet,
            btcTransaction
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGIN, pegTxType);
    }

    @Test
    void test_getTransactionType_pegin_multiple_outputs_to_retiring_fed() {
        // Arrange
        Wallet liveFederationWallet = new BridgeBtcWallet(context, Arrays.asList(retiringFederation, activeFederation));

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);

        for (int i = 0; i < 10; i++) {
            btcTransaction.addInput(BitcoinTestUtils.createHash(i), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
            btcTransaction.addOutput(Coin.COIN, retiringFederation.getAddress());
        }

        // Act
        PegTxType pegTxType = PegUtils.getTransactionTypeUsingPegoutIndex(
            activations,
            provider,
            liveFederationWallet,
            btcTransaction
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGIN, pegTxType);
    }

    @Test
    void test_getTransactionType_pegin_outputs_to_active_and_retiring_fed() {
        // Arrange
        Wallet liveFederationWallet = new BridgeBtcWallet(context, Arrays.asList(retiringFederation, activeFederation));

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        btcTransaction.addInput(BitcoinTestUtils.createHash(2), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        btcTransaction.addOutput(Coin.COIN, activeFederation.getAddress());
        btcTransaction.addOutput(Coin.COIN, retiringFederation.getAddress());

        // Act
        PegTxType pegTxType = PegUtils.getTransactionTypeUsingPegoutIndex(
            activations,
            provider,
            liveFederationWallet,
            btcTransaction
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGIN, pegTxType);
    }

    @Test
    void test_getTransactionType_pegin_outputs_to_active_and_retiring_fed_and_other_address() {
        // Arrange
        Wallet liveFederationWallet = new BridgeBtcWallet(context, Arrays.asList(retiringFederation, activeFederation));

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        btcTransaction.addInput(BitcoinTestUtils.createHash(2), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        btcTransaction.addOutput(Coin.COIN, userAddress);
        btcTransaction.addOutput(Coin.COIN, activeFederation.getAddress());
        btcTransaction.addOutput(Coin.COIN, retiringFederation.getAddress());
        btcTransaction.addOutput(Coin.COIN, userAddress);

        // Act
        PegTxType pegTxType = PegUtils.getTransactionTypeUsingPegoutIndex(
            activations,
            provider,
            liveFederationWallet,
            btcTransaction
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGIN, pegTxType);
    }

    @Test
    void test_getTransactionType_pegout_no_change_output() {
        // Arrange
        Wallet liveFederationWallet = new BridgeBtcWallet(context, Collections.singletonList(activeFederation));

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        btcTransaction.addOutput(Coin.COIN, userAddress);

        Script p2SHScript = ScriptBuilder.createP2SHOutputScript(activeFederation.getRedeemScript());
        Script inputScript = p2SHScript.createEmptyInputScript(null, activeFederation.getRedeemScript());

        btcTransaction.getInput(FIRST_INPUT_INDEX).setScriptSig(inputScript);

        Sha256Hash firstInputSigHash = btcTransaction.hashForSignature(
            FIRST_INPUT_INDEX,
            activeFederation.getRedeemScript(),
            BtcTransaction.SigHash.ALL,
            false
        );

        when(provider.hasPegoutTxSigHash(firstInputSigHash)).thenReturn(true);

        // Act
        PegTxType pegTxType = PegUtils.getTransactionTypeUsingPegoutIndex(
            activations,
            provider,
            liveFederationWallet,
            btcTransaction
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGOUT_OR_MIGRATION, pegTxType);
    }

    @Test
    void test_getTransactionType_pegout_no_change_output_sighash_no_exists_in_provider() {
        Wallet liveFederationWallet = new BridgeBtcWallet(context, Collections.singletonList(activeFederation));

        // Arrange
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        btcTransaction.addOutput(Coin.COIN, userAddress);

        Script p2SHScript = ScriptBuilder.createP2SHOutputScript(activeFederation.getRedeemScript());
        Script inputScript = p2SHScript.createEmptyInputScript(null, activeFederation.getRedeemScript());

        btcTransaction.getInput(FIRST_INPUT_INDEX).setScriptSig(inputScript);

        // Act
        PegTxType pegTxType = PegUtils.getTransactionTypeUsingPegoutIndex(
            activations,
            provider,
            liveFederationWallet,
            btcTransaction
        );

        // Assert
        Assertions.assertEquals(PegTxType.UNKNOWN, pegTxType);
    }

    @Test
    void test_getTransactionType_standard_pegout() {
        // Arrange
        Wallet liveFederationWallet = new BridgeBtcWallet(context, Collections.singletonList(activeFederation));

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        btcTransaction.addOutput(Coin.COIN, userAddress);
        btcTransaction.addOutput(Coin.COIN, activeFederation.getAddress());

        Script p2SHScript = ScriptBuilder.createP2SHOutputScript(activeFederation.getRedeemScript());
        Script inputScript = p2SHScript.createEmptyInputScript(null, activeFederation.getRedeemScript());

        btcTransaction.getInput(FIRST_INPUT_INDEX).setScriptSig(inputScript);

        Sha256Hash firstInputSigHash = btcTransaction.hashForSignature(
            FIRST_INPUT_INDEX,
            activeFederation.getRedeemScript(),
            BtcTransaction.SigHash.ALL,
            false
        );

        when(provider.hasPegoutTxSigHash(firstInputSigHash)).thenReturn(true);

        // Act
        PegTxType pegTxType = PegUtils.getTransactionTypeUsingPegoutIndex(
            activations,
            provider,
            liveFederationWallet,
            btcTransaction
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGOUT_OR_MIGRATION, pegTxType);
    }

    @Test
    void test_getTransactionType_standard_pegout_sighash_no_exists_in_provider() {
        // Arrange
        Wallet liveFederationWallet = new BridgeBtcWallet(context, Collections.singletonList(activeFederation));

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        btcTransaction.addOutput(Coin.COIN, userAddress);
        btcTransaction.addOutput(Coin.COIN, activeFederation.getAddress());

        Script p2SHScript = ScriptBuilder.createP2SHOutputScript(activeFederation.getRedeemScript());
        Script inputScript = p2SHScript.createEmptyInputScript(null, activeFederation.getRedeemScript());

        btcTransaction.getInput(FIRST_INPUT_INDEX).setScriptSig(inputScript);

        // Act
        PegTxType pegTxType = PegUtils.getTransactionTypeUsingPegoutIndex(
            activations,
            provider,
            liveFederationWallet,
            btcTransaction
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGIN, pegTxType);
    }

    @Test
    void test_getTransactionType_migration() {
        // Arrange
        Wallet liveFederationWallet = new BridgeBtcWallet(context, Arrays.asList(retiringFederation, activeFederation));

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        Sha256Hash fundTxHash = BitcoinTestUtils.createHash(1);
        btcTransaction.addInput(fundTxHash, FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        btcTransaction.addInput(fundTxHash, 1, new Script(new byte[]{}));
        btcTransaction.addInput(fundTxHash, 2, new Script(new byte[]{}));

        btcTransaction.addOutput(Coin.COIN, activeFederation.getAddress());

        Script p2SHScript = ScriptBuilder.createP2SHOutputScript(retiringFederation.getRedeemScript());
        Script inputScript = p2SHScript.createEmptyInputScript(null, retiringFederation.getRedeemScript());
        btcTransaction.getInput(FIRST_INPUT_INDEX).setScriptSig(inputScript);

        Sha256Hash firstInputSigHash = btcTransaction.hashForSignature(
            FIRST_INPUT_INDEX,
            retiringFederation.getRedeemScript(),
            BtcTransaction.SigHash.ALL,
            false
        );
        when(provider.hasPegoutTxSigHash(firstInputSigHash)).thenReturn(true);

        // Act
        PegTxType pegTxType = PegUtils.getTransactionTypeUsingPegoutIndex(
            activations,
            provider,
            liveFederationWallet,
            btcTransaction
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGOUT_OR_MIGRATION, pegTxType);
    }

    @Test
    void test_getTransactionType_migration_sighash_no_exists_in_provider() {
        // Arrange
        Wallet liveFederationWallet = new BridgeBtcWallet(context, Arrays.asList(retiringFederation, activeFederation));

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        Sha256Hash fundTxHash = BitcoinTestUtils.createHash(1);
        btcTransaction.addInput(fundTxHash, FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        btcTransaction.addInput(fundTxHash, 1, new Script(new byte[]{}));
        btcTransaction.addInput(fundTxHash, 2, new Script(new byte[]{}));

        btcTransaction.addOutput(Coin.COIN, activeFederation.getAddress());

        Script p2SHScript = ScriptBuilder.createP2SHOutputScript(retiringFederation.getRedeemScript());
        Script inputScript = p2SHScript.createEmptyInputScript(null, retiringFederation.getRedeemScript());
        btcTransaction.getInput(FIRST_INPUT_INDEX).setScriptSig(inputScript);

        // Act
        PegTxType pegTxType = PegUtils.getTransactionTypeUsingPegoutIndex(
            activations,
            provider,
            liveFederationWallet,
            btcTransaction
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGIN, pegTxType);
    }

    @Test
    void test_getTransactionType_migration_from_retired_fed() {
        // Arrange
        Wallet liveFederationWallet = new BridgeBtcWallet(context, Collections.singletonList(activeFederation));

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, retiredFed.getP2SHScript());
        btcTransaction.addOutput(Coin.COIN, activeFederation.getAddress());

        Script p2SHScript = ScriptBuilder.createP2SHOutputScript(retiredFed.getRedeemScript());
        Script inputScript = p2SHScript.createEmptyInputScript(null, retiredFed.getRedeemScript());

        btcTransaction.getInput(FIRST_INPUT_INDEX).setScriptSig(inputScript);

        Sha256Hash firstInputSigHash = btcTransaction.hashForSignature(
            FIRST_INPUT_INDEX,
            retiredFed.getRedeemScript(),
            BtcTransaction.SigHash.ALL,
            false
        );

        when(provider.hasPegoutTxSigHash(firstInputSigHash)).thenReturn(true);

        // Act
        PegTxType pegTxType = PegUtils.getTransactionTypeUsingPegoutIndex(
            activations,
            provider,
            liveFederationWallet,
            btcTransaction
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGOUT_OR_MIGRATION, pegTxType);
    }

    @Test
    void test_getTransactionType_flyover() {
        // Arrange
        Wallet liveFederationWallet = new BridgeBtcWallet(context, Collections.singletonList(activeFederation));

        Address userRefundBtcAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "userRefundBtcAddress");
        Address lpBtcAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "lpBtcAddress");
        Keccak256 derivationArgumentsHash = PegTestUtils.createHash3(0);
        RskAddress lbcAddress = PegTestUtils.createRandomRskAddress();

        BridgeSupport bridgeSupport = new BridgeSupportBuilder().build();
        Keccak256 flyoverDerivationHash = bridgeSupport.getFlyoverDerivationHash(
            derivationArgumentsHash,
            userRefundBtcAddress,
            lpBtcAddress,
            lbcAddress
        );

        Address activeFederationAddress = PegTestUtils.getFlyoverAddressFromRedeemScript(
            bridgeMainnetConstants,
            activeFederation.getRedeemScript(),
            Sha256Hash.wrap(flyoverDerivationHash.getBytes())
        );

        BtcTransaction btcTransaction = new BtcTransaction(bridgeMainnetConstants.getBtcParams());
        btcTransaction.addOutput(Coin.COIN, activeFederationAddress);

        btcTransaction.addInput(
            Sha256Hash.ZERO_HASH,
            0, new Script(new byte[]{})
        );

        // Act
        PegTxType pegTxType = PegUtils.getTransactionTypeUsingPegoutIndex(
            activations,
            provider,
            liveFederationWallet,
            btcTransaction
        );

        // Assert
        Assertions.assertEquals(PegTxType.UNKNOWN, pegTxType);
    }

    @Test
    void test_getTransactionType_flyover_segwit() {
        // Arrange
        BridgeConstants bridgeTestNetConstants = BridgeTestNetConstants.getInstance();
        NetworkParameters btcTestNetParams = bridgeTestNetConstants.getBtcParams();
        Context context = new Context(bridgeTestNetConstants.getBtcParams());

        Federation retiringFed = bridgeTestNetConstants.getGenesisFederation();

        List<BtcECKey> signers = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa01", "fa02", "fa03"}, true
        );
        List<FederationMember> fedMembers = FederationTestUtils.getFederationMembersWithBtcKeys(signers);
        Instant creationTime = Instant.ofEpochMilli(1000L);
        List<BtcECKey> erpPubKeys = bridgeTestNetConstants.getErpFedPubKeysList();
        long activationDelay = bridgeTestNetConstants.getErpFedActivationDelay();

        ErpFederationArgs erpFederationArgs =
            new ErpFederationArgs(fedMembers, creationTime, 0L, btcTestNetParams, erpPubKeys, activationDelay);
        ErpFederation activeFed = FederationFactory.buildP2shErpFederation(erpFederationArgs);
        Wallet liveFederationWallet = new BridgeBtcWallet(context, Arrays.asList(retiringFed, activeFed));

        String segwitTxHex = "020000000001011f668117f2ca3314806ade1d99ae400f5413d7e9d4bfcbd11d52645e060e22fb0100000000fdffffff0300000000000000001b6a1952534b5401a27c6f697954357247e78f9900023cfe01a9d49c0412030000000000160014b413f59a7ee6e34321140e83ea661e0484a79bc2988708000000000017a9145e6cf80958803e9b3c81cd90422152520d2a505c870247304402203fce49b39f79581d93720f462b5f33f9174e66dc6efb635d4f41aacb33b08d0302201221aec5db31e269454fcc7a4df2936ccedd566ccf48828d4f97050954f196540121021831c5ba44b739521d635e521560525672087e4d5db053801f4aeb60e782f6d6d0f02400";
        BtcTransaction btcTransaction = new BtcTransaction(btcTestNetParams, Hex.decode(segwitTxHex));

        // Act
        PegTxType pegTxType = PegUtils.getTransactionTypeUsingPegoutIndex(
            activations,
            provider,
            liveFederationWallet,
            btcTransaction
        );

        // Assert
        Assertions.assertEquals(PegTxType.UNKNOWN, pegTxType);
    }

}

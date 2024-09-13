package co.rsk.peg;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.crypto.TransactionSignature;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.wallet.RedeemData;
import co.rsk.bitcoinj.wallet.Wallet;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.constants.BridgeTestNetConstants;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.federation.*;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.test.builders.BridgeSupportBuilder;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static co.rsk.peg.PegTestUtils.createFederation;
import static co.rsk.peg.federation.FederationTestUtils.createP2shErpFederation;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PegUtilsTest {
    private static final BridgeConstants bridgeMainnetConstants = BridgeMainNetConstants.getInstance();
    private static final NetworkParameters btcMainnetParams = bridgeMainnetConstants.getBtcParams();
    private static final FederationConstants federationMainNetConstants = bridgeMainnetConstants.getFederationConstants();
    private static final Context context = new Context(bridgeMainnetConstants.getBtcParams());
    private static final ActivationConfig.ForBlock activations = ActivationConfigsForTest.all().forBlock(0L);

    private static final int FIRST_OUTPUT_INDEX = 0;
    private static final int FIRST_INPUT_INDEX = 0;

    private BridgeStorageProvider provider;
    private Address userAddress;
    private Federation retiringFederation;
    private Federation activeFederation;

    @BeforeEach
    void init() {
        provider = mock(BridgeStorageProvider.class);
        userAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "userAddress");
        List<BtcECKey> retiringFedSigners = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa01", "fa02", "fa03"}, true
        );
        retiringFederation = createFederation(bridgeMainnetConstants, retiringFedSigners);

        List<BtcECKey> activeFedSigners = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa01", "fa02", "fa03", "fa04", "fa05"}, true
        );
        activeFederation = createP2shErpFederation(federationMainNetConstants, activeFedSigners);
    }

    @Test
    void test_getTransactionType_before_tbd_600() {
        // Arrange
        ActivationConfig.ForBlock fingerrootActivations = ActivationConfigsForTest.fingerroot500().forBlock(0);
        Wallet liveFederationWallet = mock(Wallet.class);
        BtcTransaction btcTransaction = mock(BtcTransaction.class);

        // Act
        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
            PegUtils.getTransactionTypeUsingPegoutIndex(fingerrootActivations, provider, liveFederationWallet, btcTransaction)
        );

        // Assert
        String expectedMessage = "Can't call this method before RSKIP379 activation";
        assertEquals(expectedMessage, exception.getMessage());
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
        assertEquals(PegTxType.UNKNOWN, pegTxType);
    }

    @Test
    void test_getTransactionType_pegin_below_minimum_active_fed() {
        // Arrange
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
        assertEquals(PegTxType.PEGIN, pegTxType);
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
        assertEquals(PegTxType.PEGIN, pegTxType);
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
        assertEquals(PegTxType.PEGIN, pegTxType);
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
        assertEquals(PegTxType.PEGIN, pegTxType);
    }

    @Test
    void test_getTransactionType_pegin_output_to_retiring_fed_and_other_addresses() {
        // Arrange
        List<BtcECKey> signers = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa01", "fa02", "fa03"}, true
        );
        List<FederationMember> fedMembers = FederationTestUtils.getFederationMembersWithBtcKeys(signers);
        Instant creationTime = Instant.ofEpochMilli(1000L);
        List<BtcECKey> erpPubKeys = federationMainNetConstants.getErpFedPubKeysList();
        long activationDelay = federationMainNetConstants.getErpFedActivationDelay();
        FederationArgs federationArgs = new FederationArgs(fedMembers, creationTime, 0L, btcMainnetParams);

        ErpFederation activeFed = FederationFactory.buildP2shErpFederation(federationArgs, erpPubKeys, activationDelay);
        Wallet liveFederationWallet = new BridgeBtcWallet(context, Arrays.asList(retiringFederation, activeFed));

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        btcTransaction.addOutput(Coin.COIN, userAddress);
        btcTransaction.addOutput(Coin.COIN, retiringFederation.getAddress());
        btcTransaction.addOutput(Coin.COIN, BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "unknown2"));

        // Act
        PegTxType pegTxType = PegUtils.getTransactionTypeUsingPegoutIndex(
            activations,
            provider,
            liveFederationWallet,
            btcTransaction
        );

        // Assert
        assertEquals(PegTxType.PEGIN, pegTxType);
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
        assertEquals(PegTxType.PEGIN, pegTxType);
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
        assertEquals(PegTxType.PEGIN, pegTxType);
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
        assertEquals(PegTxType.PEGIN, pegTxType);
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
        assertEquals(PegTxType.PEGIN, pegTxType);
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
        assertEquals(PegTxType.PEGOUT_OR_MIGRATION, pegTxType);
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
        assertEquals(PegTxType.UNKNOWN, pegTxType);
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
        assertEquals(PegTxType.PEGOUT_OR_MIGRATION, pegTxType);
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
        assertEquals(PegTxType.PEGIN, pegTxType);
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
        assertEquals(PegTxType.PEGOUT_OR_MIGRATION, pegTxType);
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
        assertEquals(PegTxType.PEGIN, pegTxType);
    }

    @Test
    void test_getTransactionType_migration_from_retired_fed() {
        // Arrange
        Wallet liveFederationWallet = new BridgeBtcWallet(context, Collections.singletonList(activeFederation));
        Federation genesisFederation = FederationTestUtils.getGenesisFederation(federationMainNetConstants);

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, genesisFederation.getP2SHScript());
        btcTransaction.addOutput(Coin.COIN, activeFederation.getAddress());

        Script p2SHScript = ScriptBuilder.createP2SHOutputScript(genesisFederation.getRedeemScript());
        Script inputScript = p2SHScript.createEmptyInputScript(null, genesisFederation.getRedeemScript());

        btcTransaction.getInput(FIRST_INPUT_INDEX).setScriptSig(inputScript);

        Sha256Hash firstInputSigHash = btcTransaction.hashForSignature(
            FIRST_INPUT_INDEX,
            genesisFederation.getRedeemScript(),
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
        assertEquals(PegTxType.PEGOUT_OR_MIGRATION, pegTxType);
    }

    @Test
    void test_getTransactionType_flyover() {
        // Arrange
        Wallet liveFederationWallet = new BridgeBtcWallet(context, Collections.singletonList(activeFederation));

        Address userRefundBtcAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "userRefundBtcAddress");
        Address lpBtcAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "lpBtcAddress");
        Keccak256 derivationArgumentsHash = PegTestUtils.createHash3(0);
        RskAddress lbcAddress = PegTestUtils.createRandomRskAddress();

        BridgeSupport bridgeSupport = BridgeSupportBuilder.builder().build();
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
        assertEquals(PegTxType.UNKNOWN, pegTxType);
    }

    @Test
    void test_getTransactionType_flyover_segwit() {
        // Arrange
        BridgeConstants bridgeTestNetConstants = BridgeTestNetConstants.getInstance();
        NetworkParameters btcTestNetParams = bridgeTestNetConstants.getBtcParams();

        List<BtcECKey> signers = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa01", "fa02", "fa03"}, true
        );
        List<FederationMember> fedMembers = FederationTestUtils.getFederationMembersWithBtcKeys(signers);
        Instant creationTime = Instant.ofEpochMilli(1000L);
        List<BtcECKey> erpPubKeys = federationMainNetConstants.getErpFedPubKeysList();
        long activationDelay = federationMainNetConstants.getErpFedActivationDelay();

        FederationArgs federationArgs =
            new FederationArgs(fedMembers, creationTime, 0L, btcTestNetParams);
        ErpFederation activeFed = FederationFactory.buildP2shErpFederation(federationArgs, erpPubKeys, activationDelay);
        Wallet liveFederationWallet = new BridgeBtcWallet(context, Arrays.asList(retiringFederation, activeFed));

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
        assertEquals(PegTxType.UNKNOWN, pegTxType);
    }


    @Test
    void calculatePegoutTxSize_ZeroInput_ZeroOutput() {
        Federation federation = P2shErpFederationBuilder.builder().build();

        assertThrows(
            IllegalArgumentException.class,
            () -> PegUtils.calculatePegoutTxSize(activations, federation, 0, 0)
        );
    }

    @Test
    void calculatePegoutTxSize_2Inputs_2Outputs() {
        Federation federation = P2shErpFederationBuilder.builder().build();

        int inputSize = 2;
        int outputSize = 2;
        int pegoutTxSize = PegUtils.calculatePegoutTxSize(activations, federation, inputSize, outputSize);

        // The difference between the calculated size and a real tx size should be smaller than 1% in any direction
        int origTxSize = 2076; // Data for 2 inputs, 2 outputs From https://www.blockchain.com/btc/tx/e92cab54ecf738a00083fd8990515247aa3404df4f76ec358d9fe87d95102ae4
        int difference = origTxSize - pegoutTxSize;
        double tolerance = origTxSize * .01;

        assertTrue(difference < tolerance && difference > -tolerance);
    }

    @Test
    void calculatePegoutTxSize_9Inputs_2Outputs() {
        Federation federation = P2shErpFederationBuilder.builder().build();

        int inputSize = 9;
        int outputSize = 2;
        int pegoutTxSize = PegUtils.calculatePegoutTxSize(activations, federation, inputSize, outputSize);

        // The difference between the calculated size and a real tx size should be smaller than 1% in any direction
        int origTxSize = 9069; // Data for 9 inputs, 2 outputs From https://www.blockchain.com/btc/tx/15adf52f7b4b7a7e563fca92aec7bbe8149b87fac6941285a181e6fcd799a1cd
        int difference = origTxSize - pegoutTxSize;
        double tolerance = origTxSize * .01;

        assertTrue(difference < tolerance && difference > -tolerance);
    }

    @Test
    void calculatePegoutTxSize_10Inputs_20Outputs() {
        Federation federation = P2shErpFederationBuilder.builder().build();

        // Create a pegout tx with 10 inputs and 20 outputs
        int inputSize = 10;
        int outputSize = 20;
        BtcTransaction pegoutTx = createPegOutTx(inputSize, outputSize, federation, keys);

        int pegoutTxSize = PegUtils.calculatePegoutTxSize(activations, federation, inputSize, outputSize);

        // The difference between the calculated size and a real tx size should be smaller than 2% in any direction
        int origTxSize = pegoutTx.bitcoinSerialize().length;
        int difference = origTxSize - pegoutTxSize;
        double tolerance = origTxSize * .02;

        assertTrue(difference < tolerance && difference > -tolerance);
    }

    @Test
    void calculatePegoutTxSize_50Inputs_200Outputs() {
        Federation federation = P2shErpFederationBuilder.builder().build();

        // Create a pegout tx with 50 inputs and 200 outputs
        int inputSize = 50;
        int outputSize = 200;
        BtcTransaction pegoutTx = createPegOutTx(inputSize, outputSize, federation, keys);

        int pegoutTxSize = PegUtils.calculatePegoutTxSize(activations, federation, inputSize, outputSize);

        // The difference between the calculated size and a real tx size should be smaller than 2% in any direction
        int origTxSize = pegoutTx.bitcoinSerialize().length;
        int difference = origTxSize - pegoutTxSize;
        double tolerance = origTxSize * .02;

        assertTrue(difference < tolerance && difference > -tolerance);
    }

    @Test
    void getRegularPegoutTxSize_has_proper_calculations() {
        Federation federation = P2shErpFederationBuilder.builder().build();

        // Create a pegout tx with two inputs and two outputs
        int inputs = 2;
        BtcTransaction pegoutTx = createPegOutTx(Collections.emptyList(), inputs, federation, false);

        for (int inputIndex = 0; inputIndex < inputs; inputIndex++) {
            Script inputScript = pegoutTx.getInput(inputIndex).getScriptSig();

            Sha256Hash sighash = pegoutTx.hashForSignature(inputIndex, federation.getRedeemScript(), BtcTransaction.SigHash.ALL, false);

            for (int keyIndex = 0; keyIndex < keys.size() - 1; keyIndex++) {
                BtcECKey key = keys.get(keyIndex);
                BtcECKey.ECDSASignature sig = key.sign(sighash);
                TransactionSignature txSig = new TransactionSignature(sig, BtcTransaction.SigHash.ALL, false);
                byte[] txSigEncoded = txSig.encodeToBitcoin();

                int sigIndex = inputScript.getSigInsertionIndex(sighash, key);
                inputScript = ScriptBuilder.updateScriptWithSignature(inputScript, txSigEncoded, sigIndex, 1, 1);
                pegoutTx.getInput(inputIndex).setScriptSig(inputScript);
            }
        }

        int pegoutTxSize = PegUtils.getRegularPegoutTxSize(activations, federation);

        // The difference between the calculated size and a real tx size should be smaller than 10% in any direction
        int difference = pegoutTx.bitcoinSerialize().length - pegoutTxSize;
        double tolerance = pegoutTxSize * .1;

        assertTrue(difference < tolerance && difference > -tolerance);
    }
}

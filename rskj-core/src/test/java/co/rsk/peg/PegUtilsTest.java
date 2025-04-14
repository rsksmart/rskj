package co.rsk.peg;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.wallet.Wallet;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.constants.BridgeTestNetConstants;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.federation.*;
import co.rsk.peg.federation.constants.FederationConstants;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
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
import static co.rsk.peg.PegUtils.*;
import static co.rsk.peg.federation.FederationTestUtils.createP2shErpFederation;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PegUtilsTest {
    private static final BridgeConstants bridgeMainnetConstants = BridgeMainNetConstants.getInstance();
    private static final NetworkParameters btcMainnetParams = bridgeMainnetConstants.getBtcParams();
    private static final FederationConstants federationMainNetConstants = bridgeMainnetConstants.getFederationConstants();
    private static final Context context = new Context(bridgeMainnetConstants.getBtcParams());
    private static final ActivationConfig.ForBlock activations = ActivationConfigsForTest.arrowhead600().forBlock(0);

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

    @ParameterizedTest
    @MethodSource("derivationHashAndRedeemScriptArgs")
    void getFlyoverRedeemScript_fromRealValues_shouldReturnSameRealRedeemScript(Keccak256 flyoverDerivationHash, Federation federation) {
        Script flyoverRedeemScript = new Script(Hex.decode("20fc2bb93810d3d2332fed0b291c03822100a813eceaa0665896e0c82a8d50043975645521020ace50bab1230f8002a0bfe619482af74b338cc9e4c956add228df47e6adae1c21025093f439fb8006fd29ab56605ffec9cdc840d16d2361004e1337a2f86d8bd2db210275d473555de2733c47125f9702b0f870df1d817379f5587f09b6c40ed2c6c9492102a95f095d0ce8cb3b9bf70cc837e3ebe1d107959b1fa3f9b2d8f33446f9c8cbdb2103250c11be0561b1d7ae168b1f59e39cbc1fd1ba3cf4d2140c1a365b2723a2bf9321034851379ec6b8a701bd3eef8a0e2b119abb4bdde7532a3d6bcbff291b0daf3f25210350179f143a632ce4e6ac9a755b82f7f4266cfebb116a42cadb104c2c2a3350f92103b04fbd87ef5e2c0946a684c8c93950301a45943bbe56d979602038698facf9032103b58a5da144f5abab2e03e414ad044b732300de52fa25c672a7f7b3588877190659ae670350cd00b275532102370a9838e4d15708ad14a104ee5606b36caaaaf739d833e67770ce9fd9b3ec80210257c293086c4d4fe8943deda5f890a37d11bebd140e220faa76258a41d077b4d42103c2660a46aa73078ee6016dee953488566426cf55fc8011edd0085634d75395f92103cd3e383ec6e12719a6c69515e5559bcbe037d0aa24c187e1e26ce932e22ad7b354ae68"));

        assertEquals(flyoverRedeemScript, getFlyoverFederationRedeemScript(flyoverDerivationHash, federation.getRedeemScript()));
    }

    @ParameterizedTest
    @MethodSource("derivationHashAndRedeemScriptArgs")
    void getFlyoverScriptPubKey_fromRealValues_shouldReturnSameRealOutputScript(Keccak256 flyoverDerivationHash, Federation federation) {
        Script scriptPubKey = getFlyoverFederationScriptPubKey(flyoverDerivationHash, federation); // OP_HASH160 outputScript OP_EQUAL
        byte[] outputScript = Hex.decode("18fc3b52a5b7d5277f41b9765719b45bfa427730");

        assertArrayEquals(outputScript, scriptPubKey.getPubKeyHash());
    }

    @ParameterizedTest
    @MethodSource("derivationHashAndRedeemScriptArgs")
    void getFlyoverAddress_fromRealValues_shouldReturnSameRealAddress(Keccak256 flyoverDerivationHash, Federation federation) {
        Address flyoverAddress = Address.fromBase58(btcMainnetParams, "33y8JWrSe4byp3DKmy2Mkyykz2dzP8Lmvn");

        assertEquals(flyoverAddress, getFlyoverFederationAddress(btcMainnetParams, flyoverDerivationHash, federation));
    }

    private static Stream<Arguments> derivationHashAndRedeemScriptArgs() {
        // reference from https://mempool.space/tx/ffaebdabce5b1cc1b2ab95657cf087a67ade6a29ecc9ca7d4e2089e346a3e1b3

        Keccak256 flyoverDerivationHash = new Keccak256("fc2bb93810d3d2332fed0b291c03822100a813eceaa0665896e0c82a8d500439");

        List<BtcECKey> membersKeys = Arrays.asList(
            BtcECKey.fromPublicOnly(Hex.decode("020ace50bab1230f8002a0bfe619482af74b338cc9e4c956add228df47e6adae1c")),
            BtcECKey.fromPublicOnly(Hex.decode("025093f439fb8006fd29ab56605ffec9cdc840d16d2361004e1337a2f86d8bd2db")),
            BtcECKey.fromPublicOnly(Hex.decode("0275d473555de2733c47125f9702b0f870df1d817379f5587f09b6c40ed2c6c949")),
            BtcECKey.fromPublicOnly(Hex.decode("02a95f095d0ce8cb3b9bf70cc837e3ebe1d107959b1fa3f9b2d8f33446f9c8cbdb")),
            BtcECKey.fromPublicOnly(Hex.decode("03250c11be0561b1d7ae168b1f59e39cbc1fd1ba3cf4d2140c1a365b2723a2bf93")),
            BtcECKey.fromPublicOnly(Hex.decode("034851379ec6b8a701bd3eef8a0e2b119abb4bdde7532a3d6bcbff291b0daf3f25")),
            BtcECKey.fromPublicOnly(Hex.decode("0350179f143a632ce4e6ac9a755b82f7f4266cfebb116a42cadb104c2c2a3350f9")),
            BtcECKey.fromPublicOnly(Hex.decode("03b04fbd87ef5e2c0946a684c8c93950301a45943bbe56d979602038698facf903")),
            BtcECKey.fromPublicOnly(Hex.decode("03b58a5da144f5abab2e03e414ad044b732300de52fa25c672a7f7b35888771906"))
        );
        Federation federation = P2shErpFederationBuilder.builder().withMembersBtcPublicKeys(membersKeys).build();

        return Stream.of(
            Arguments.of(flyoverDerivationHash, federation)
        );
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

        Keccak256 flyoverDerivationHash = PegUtils.getFlyoverDerivationHash(
            derivationArgumentsHash,
            userRefundBtcAddress,
            lpBtcAddress,
            lbcAddress,
            activations
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

}

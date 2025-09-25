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
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
    private static final ActivationConfig.ForBlock activations = ActivationConfigsForTest.all().forBlock(0);

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
    void getFlyoverDerivationHash_returnsExpectedDerivationHash() {
        // arrange
        Address userRefundBtcAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "userRefundBtcAddress");
        Address lpBtcAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "lpBtcAddress");
        Keccak256 derivationArgumentsHash = PegTestUtils.createHash3(5);
        RskAddress lbcAddress = new RskAddress("461750b4824b14c3d9b7702bc6fbb82469082b23");

        // act
        Keccak256 flyoverDerivationHash = PegUtils.getFlyoverDerivationHash(
            derivationArgumentsHash,
            userRefundBtcAddress,
            lpBtcAddress,
            lbcAddress,
            activations
        );

        // assert
        Keccak256 expectedDerivationHash = new Keccak256(Hex.decode("56d4f6bd69378ef607e091832903ddc2b5aac5008bd06987a26f14bb248c44d8"));
        assertEquals(expectedDerivationHash, flyoverDerivationHash);
    }

    @Test
    void getFlyoverValues_fromRealLegacyFedTx_shouldReturnSameRealValues() {
        // reference from https://mempool.space/tx/ffaebdabce5b1cc1b2ab95657cf087a67ade6a29ecc9ca7d4e2089e346a3e1b3
        // arrange
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

        Keccak256 flyoverDerivationHash = new Keccak256("fc2bb93810d3d2332fed0b291c03822100a813eceaa0665896e0c82a8d500439");

        // act
        Script flyoverFederationRedeemScript = getFlyoverFederationRedeemScript(flyoverDerivationHash, federation.getRedeemScript());
        Script flyoverFederationOutputScript = getFlyoverFederationOutputScript(flyoverFederationRedeemScript, federation.getFormatVersion());
        Script flyoverFederationScriptPubKey = getFlyoverFederationScriptPubKey(flyoverDerivationHash, federation); // OP_HASH160 outputScript OP_EQUAL
        Address flyoverFederationAddress = getFlyoverFederationAddress(btcMainnetParams, flyoverDerivationHash, federation);

        // address
        Script expectedFlyoverRedeemScript = new Script(Hex.decode("20fc2bb93810d3d2332fed0b291c03822100a813eceaa0665896e0c82a8d50043975645521020ace50bab1230f8002a0bfe619482af74b338cc9e4c956add228df47e6adae1c21025093f439fb8006fd29ab56605ffec9cdc840d16d2361004e1337a2f86d8bd2db210275d473555de2733c47125f9702b0f870df1d817379f5587f09b6c40ed2c6c9492102a95f095d0ce8cb3b9bf70cc837e3ebe1d107959b1fa3f9b2d8f33446f9c8cbdb2103250c11be0561b1d7ae168b1f59e39cbc1fd1ba3cf4d2140c1a365b2723a2bf9321034851379ec6b8a701bd3eef8a0e2b119abb4bdde7532a3d6bcbff291b0daf3f25210350179f143a632ce4e6ac9a755b82f7f4266cfebb116a42cadb104c2c2a3350f92103b04fbd87ef5e2c0946a684c8c93950301a45943bbe56d979602038698facf9032103b58a5da144f5abab2e03e414ad044b732300de52fa25c672a7f7b3588877190659ae670350cd00b275532102370a9838e4d15708ad14a104ee5606b36caaaaf739d833e67770ce9fd9b3ec80210257c293086c4d4fe8943deda5f890a37d11bebd140e220faa76258a41d077b4d42103c2660a46aa73078ee6016dee953488566426cf55fc8011edd0085634d75395f92103cd3e383ec6e12719a6c69515e5559bcbe037d0aa24c187e1e26ce932e22ad7b354ae68"));
        assertEquals(expectedFlyoverRedeemScript, flyoverFederationRedeemScript);

        byte[] expectedFlyoverFederationScriptPubKey = Hex.decode("18fc3b52a5b7d5277f41b9765719b45bfa427730");
        assertArrayEquals(expectedFlyoverFederationScriptPubKey, flyoverFederationOutputScript.getPubKeyHash());
        assertArrayEquals(expectedFlyoverFederationScriptPubKey, flyoverFederationScriptPubKey.getPubKeyHash());

        Address expectedFlyoverFederationAddress = Address.fromBase58(btcMainnetParams, "33y8JWrSe4byp3DKmy2Mkyykz2dzP8Lmvn");
        assertEquals(expectedFlyoverFederationAddress, flyoverFederationAddress);
    }

    @Test
    void getFlyoverValues_fromRealSegwitFedTx_shouldReturnSameValues() {
        // arrange
        // data from https://mempool.space/tx/a4d76b6211b078cbc1d2079002437fcf018cc85cd40dd6195bb0f6b42930b96b
        List<BtcECKey> btcMembersKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{
                "fed1", "fed2", "fed3", "fed4", "fed5", "fed6", "fed7", "fed8", "fed9", "fed10",
                "fed11", "fed12", "fed13", "fed14", "fed15", "fed16", "fed17", "fed18", "fed19", "fed20"
            },
            true
        );
        List<BtcECKey> erpPublicKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{
                "erp1", "erp2", "erp3", "erp4","erp5", "erp6", "erp7", "erp8","erp9", "erp10",
                "erp11", "erp12", "erp13", "erp14","erp15", "erp16", "erp17", "erp18","erp19", "erp20"
            },
            true
        );
        long activationDelay = 30;

        Federation federation = P2shP2wshErpFederationBuilder.builder()
            .withMembersBtcPublicKeys(btcMembersKeys)
            .withErpPublicKeys(erpPublicKeys)
            .withErpActivationDelay(activationDelay)
            .build();

        Keccak256 flyoverDerivationHash = new Keccak256("0100000000000000000000000000000000000000000000000000000000000000");

        // act
        Script flyoverRedeemScript = getFlyoverFederationRedeemScript(flyoverDerivationHash, federation.getRedeemScript());
        Script flyoverFederationOutputScript = getFlyoverFederationOutputScript(flyoverRedeemScript, federation.getFormatVersion());
        Script flyoverFederationScriptPubKey = getFlyoverFederationScriptPubKey(flyoverDerivationHash, federation); // OP_HASH160 outputScript OP_EQUAL
        Address flyoverFederationAddress = getFlyoverFederationAddress(btcMainnetParams, flyoverDerivationHash, federation);

        // assert
        Script expectedFlyoverRedeemScript = new Script(Hex.decode("20010000000000000000000000000000000000000000000000000000000000000075645b210211310637a4062844098b46278059298cc948e1ff314ca9ec75c82e0d0b8ad22c210238de69e208565fd82e4b76c4eff5d817a51679b8a90c41709e49660ba23501c521024b120731b26ec7165cddd214fc8e3f0c844a03dc0e533fb0cf9f89ad2f68a881210274564db76110474ac0d7e09080c182855b22a864cc201ed55217b23301f52f222102867f0e693a2553bf2bc13a5efa0b516b28e66317fbe8e484dd3f375bcb48ec592102881af2910c909f224557353dd28e3729363cf5c24232f26d25c92dac72a3dcdb21029c75b3257e0842c4be48e57e39bf2735c74a76c4b1c0b08d1cc66bf5b8748cc12102a46cbe93287cb51a398a157de2b428f21a94f46affdd916ce921bd10db6520332102d335ef4eeb74330c3a53f529f9741fa096412c7982ed681fcf69763894f34f892102d3f5fd6e107cf68b1be8dce0e16a0a8afb8dcef9a76c851d7eaf6d51c46a35752103163b86a62b4eeeb52f67cb16ce13a8622a066f2a063280749b956a97705dfc3d21033267e382e076cbaa199d49ea7362535f95b135de181caf66b391f541bf39ab0e210343e106d90183e2eef7d5cb7538a634439bf1301d731787c6736922ff19e750ed21034461d4263b907cfc5ebb468f19d6a133b567f3cc4855e8725faaf60c6e388bca21036e92e6555d2e70af4f5a4f888145356e60bb1a5bc00786a8e9f50152090b2f692103ab54da6b69407dcaaa85f6904687052c93f1f9dd0633f1321b3e624fcd30144b2103bd5b51b1c5d799da190285c8078a2712b8e5dc6f73c799751e6256bb89a4bd042103be060191c9632184f2a0ab2638eeed04399372f37fc7a3cff5291cfd6426cf352103e6def9ef0597336eb58d24f955b6b63756cf7b3885322f9d0cf5a2a12f7e459b2103ef03253b7b4f33d68c39141eb016df15fafbb1d0fa4a2e7f208c94ea154ab8c30114ae67011eb2755b21021a560245f78312588f600315d75d493420bed65873b63d0d4bb8ca1b9163a35b2102218e9dc07ac4190a1d7df94fc75953b36671129f12668a94f1f504fe47399ead210272ed6e14e70f6b4757d412729730837bc63b6313276be8308a5a96afd63af9942102872f69892a74d60f6185c2908414dcddb24951c035a1a8466c6c56f55043e7602102886d7d8e865f75dfda3ddf94619af87ad8aa71e8ef393e1e57593576b7d7af1621028e59462fb53ba31186a353b7ea77ebefda9097392e45b7ca7a216168230d05af21028f5a88b08d75765b36951254e68060759de5be7e559972c37c67fc8cedafeb262102c9ced4bbc468af9ace1645df2fd50182d5822cb4c68aae0e50ae1d45da260d2a2102deba35a96add157b6de58f48bb6e23bcb0a17037bed1beb8ba98de6b0a0d71d62102f2e00fefa5868e2c56405e188ec1d97557a7c77fb6a448352cc091c2ae9d50492102fb8c06c723d4e59792e36e6226087fcfac65c1d8a0d5c5726a64102a551528442103077c62a45ea1a679e54c9f7ad800d8e40eaf6012657c8dccd3b61d5e070d9a432103616959a72dd302043e9db2dbd7827944ecb2d555a8f72a48bb8f916ec5aac6ec210362f9c79cd0586704d6a9ea863573f3b123d90a31faaa5a1d9a69bf9631c78ae321036899d94ad9d3f24152dd4fa79b9cb8dddbd26d18297be4facb295f57c9de60bd210376e4cb35baa8c46b0dcffaf303785c5f7aadf457df30ac956234cc8114e2f47d2103a587256beec4e167aebc478e1d6502bb277a596ae9574ccb646da11fffbf36502103bb9da162c3f581ced93167f86d7e0e5962762a1188f5bd1f8b5d08fed46ef73d2103c34fcd05cef2733ea7337c37f50ae26245646aba124948c6ff8dcdf8212849982103f8ac768e683a07ac4063f72a6d856aedeae109f844abcfa34ac9519d715177460114ae68"));
        assertEquals(expectedFlyoverRedeemScript, flyoverRedeemScript);

        byte[] expectedFlyoverFederationScriptPubKey = Hex.decode("412e8c6fb9691820b826d423111e12a2590c363d");
        assertArrayEquals(expectedFlyoverFederationScriptPubKey, flyoverFederationOutputScript.getPubKeyHash());
        assertArrayEquals(expectedFlyoverFederationScriptPubKey, flyoverFederationScriptPubKey.getPubKeyHash());

        Address expectedFlyoverFederationAddress = Address.fromBase58(btcMainnetParams, "37dfcGJtPJjRUcPkPgqfstPB5umEea3rWZ");
        assertEquals(expectedFlyoverFederationAddress, flyoverFederationAddress);
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
    void allUTXOsToFedAreAboveMinimumPeginValue_peginWithOneOutputExactlyMinimum_shouldReturnTrue() {
        // Arrange
        Wallet liveFederationWallet = new BridgeBtcWallet(context, List.of(activeFederation));
        BtcTransaction peginBtcTx = new BtcTransaction(btcMainnetParams);
        BtcECKey userPubKey = BitcoinTestUtils.getBtcEcKeyFromSeed("user");

        peginBtcTx.addInput(
            BitcoinTestUtils.createHash(1),
            0,
            ScriptBuilder.createInputScript(null, userPubKey)
        );

        Coin minimumPeginTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);
        peginBtcTx.addOutput(minimumPeginTxValue, activeFederation.getAddress());

        // Act
        boolean allUTXOsAboveMinimum = PegUtils.allUTXOsToFedAreAboveMinimumPeginValue(
            peginBtcTx,
            liveFederationWallet,
            minimumPeginTxValue,
            activations
        );

        // Assert
        assertTrue(allUTXOsAboveMinimum);
    }

    @Test
    void allUTXOsToFedAreAboveMinimumPeginValue_peginWithOneOutputExactlyMinimumAndOneAboveMinimum_shouldReturnTrue() {
        // Arrange
        Wallet liveFederationWallet = new BridgeBtcWallet(context, List.of(activeFederation));
        Coin minimumPeginTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);

        BtcTransaction peginBtcTx = new BtcTransaction(btcMainnetParams);
        BtcECKey userPubKey = BitcoinTestUtils.getBtcEcKeyFromSeed("user");

        peginBtcTx.addInput(
            BitcoinTestUtils.createHash(1),
            0,
            ScriptBuilder.createInputScript(null, userPubKey)
        );

        Address federationAddress = activeFederation.getAddress();
        Coin valueAboveMinimum = minimumPeginTxValue.plus(Coin.SATOSHI);

        peginBtcTx.addOutput(minimumPeginTxValue, federationAddress);
        peginBtcTx.addOutput(valueAboveMinimum, federationAddress);

        // Act
        boolean allUTXOsAboveMinimum = PegUtils.allUTXOsToFedAreAboveMinimumPeginValue(
            peginBtcTx,
            liveFederationWallet,
            minimumPeginTxValue,
            activations
        );

        // Assert
        assertTrue(allUTXOsAboveMinimum);
    }

    @Test
    void allUTXOsToFedAreAboveMinimumPeginValue_peginWithOneOutputBelowMinimum_shouldReturnFalse() {
        // Arrange
        Wallet liveFederationWallet = new BridgeBtcWallet(context, List.of(activeFederation));
        Coin minimumPeginTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);

        BtcTransaction peginBtcTx = new BtcTransaction(btcMainnetParams);
        BtcECKey userPubKey = BitcoinTestUtils.getBtcEcKeyFromSeed("user");

        peginBtcTx.addInput(
            BitcoinTestUtils.createHash(1),
            0,
            ScriptBuilder.createInputScript(null, userPubKey)
        );

        Address federationAddress = activeFederation.getAddress();
        Coin valueBelowMinimum = minimumPeginTxValue.minus(Coin.SATOSHI);
        Coin valueAboveMinimum = minimumPeginTxValue.plus(Coin.SATOSHI);

        peginBtcTx.addOutput(valueBelowMinimum, federationAddress);
        peginBtcTx.addOutput(minimumPeginTxValue, federationAddress);
        peginBtcTx.addOutput(valueAboveMinimum, federationAddress);

        // Act
        boolean allUTXOsAboveMinimum = PegUtils.allUTXOsToFedAreAboveMinimumPeginValue(
            peginBtcTx,
            liveFederationWallet,
            minimumPeginTxValue,
            activations
        );

        // Assert
        assertFalse(allUTXOsAboveMinimum);
    }

    @Test
    void allUTXOsToFedAreAboveMinimumPeginValue_notPeginTx_shouldReturnFalse() {
        // Arrange
        Wallet liveFederationWallet = new BridgeBtcWallet(context, List.of(activeFederation));
        Coin minimumPeginTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);

        BtcTransaction peginBtcTx = new BtcTransaction(btcMainnetParams);
        BtcECKey userPubKey = BitcoinTestUtils.getBtcEcKeyFromSeed("user");

        peginBtcTx.addInput(
            BitcoinTestUtils.createHash(1),
            0,
            ScriptBuilder.createInputScript(null, userPubKey)
        );

        Address receiver = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "receiver");
        // sending enough value but to some random address
        peginBtcTx.addOutput(minimumPeginTxValue, receiver);

        // Act
        boolean allUTXOsAboveMinimum = PegUtils.allUTXOsToFedAreAboveMinimumPeginValue(
            peginBtcTx,
            liveFederationWallet,
            minimumPeginTxValue,
            activations
        );

        // Assert
        assertFalse(allUTXOsAboveMinimum);
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

        Script p2SHScript = ScriptBuilder.createP2SHOutputScript(retiringFederation.getRedeemScript());
        Script inputScript = p2SHScript.createEmptyInputScript(null, retiringFederation.getRedeemScript());
        btcTransaction.addInput(fundTxHash, FIRST_OUTPUT_INDEX, inputScript);
        btcTransaction.addInput(fundTxHash, 1, inputScript);
        btcTransaction.addInput(fundTxHash, 2, inputScript);

        btcTransaction.addOutput(Coin.COIN, activeFederation.getAddress());

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

        Script p2SHScript = ScriptBuilder.createP2SHOutputScript(retiringFederation.getRedeemScript());
        Script inputScript = p2SHScript.createEmptyInputScript(null, retiringFederation.getRedeemScript());
        btcTransaction.addInput(fundTxHash, FIRST_OUTPUT_INDEX, inputScript);
        btcTransaction.addInput(fundTxHash, 1, inputScript);
        btcTransaction.addInput(fundTxHash, 2, inputScript);

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

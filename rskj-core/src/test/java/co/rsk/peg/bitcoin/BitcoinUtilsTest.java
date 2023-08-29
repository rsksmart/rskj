package co.rsk.peg.bitcoin;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.TransactionInput;
import co.rsk.bitcoinj.crypto.TransactionSignature;
import co.rsk.bitcoinj.script.FastBridgeP2shErpRedeemScriptParser;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.wallet.RedeemData;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeMainNetConstants;
import co.rsk.peg.BridgeUtils;
import co.rsk.peg.Federation;
import co.rsk.peg.FederationTestUtils;
import co.rsk.peg.P2shErpFederation;
import co.rsk.peg.PegTestUtils;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

class BitcoinUtilsTest {
    private static final BridgeConstants bridgeMainnetConstants = BridgeMainNetConstants.getInstance();
    private static final NetworkParameters btcMainnetParams = bridgeMainnetConstants.getBtcParams();
    private static final int FIRST_OUTPUT_INDEX = 0;
    private static final int FIRST_INPUT_INDEX = 0;

    private List<BtcECKey> signers;
    private List<BtcECKey> pubKeys;
    private Address destinationAddress;

    @BeforeEach
    void init() {
        signers = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa01", "fa02", "fa03"}, true
        );
        pubKeys = signers.stream().map(BtcECKey::getPubKeyPoint).map(BtcECKey::fromPublicOnly)
            .collect(Collectors.toList());

        destinationAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "destinationAddress");
    }

    @Test
    void test_extracted_sigHash_is_valid_using_a_real_tx() {
        // Arrange
        // https://www.blockchain.com/explorer/transactions/btc/99ff14d8d0b339f6144291940c5f99e8fd4002fb300cebde963555fd395ee943
        String btcTxHex = "0200000001188d5f9de5b8db6f4be05da4ae4a2d150112c333cee829b7f096feb1ebcb473401000000fd3703004730440220630a0c2fbe56cc78ff6506c64a8013b4fb703ade2c1c23c3a9973d6bf1d59ac802200aa28a08deb20ead5bd84ee003f5f17dfbf643f2e96079b878eba798f155191d01473044022043e2e3c82a74a817425de96e8e317e3f96e462b0e96441f4e301b1e6d2df4d7602206ee7d453f61cc55d0ad6530438876a21ed2e3b240cd487cf9ddf2ad17a40047d01483045022100c312a5c6f87d1903f89f158bdb3e8f3385a049ebedb7bb1d4753176881e2981502201eb50518f76f47587c6781c1b39f7ede1349df01b65036e7d80a09252149662d0147304402203370ab37f6b24d723ccc7a6b9cd4dfe9f30449a64effb47d1f5e1727a93afa5f022004733ecc46c19c762f0a4372b882fcec1209990f7bf1426f94f79b29534816700147304402203af20ed490bf6b140e2df8770025e333869fa6f54a6cf152e6666c1054eb3424022023f255af0855464f26165a026ede6d6db7475c7442353a3dcd17dc4bc61fb14201004dc901645521020ace50bab1230f8002a0bfe619482af74b338cc9e4c956add228df47e6adae1c21025093f439fb8006fd29ab56605ffec9cdc840d16d2361004e1337a2f86d8bd2db21026b472f7d59d201ff1f540f111b6eb329e071c30a9d23e3d2bcd128fe73dc254c210275d473555de2733c47125f9702b0f870df1d817379f5587f09b6c40ed2c6c9492102a95f095d0ce8cb3b9bf70cc837e3ebe1d107959b1fa3f9b2d8f33446f9c8cbdb2103250c11be0561b1d7ae168b1f59e39cbc1fd1ba3cf4d2140c1a365b2723a2bf9321034851379ec6b8a701bd3eef8a0e2b119abb4bdde7532a3d6bcbff291b0daf3f252103b58a5da144f5abab2e03e414ad044b732300de52fa25c672a7f7b358887719062103e05bf6002b62651378b1954820539c36ca405cbb778c225395dd9ebff678029959ae670350cd00b275532102370a9838e4d15708ad14a104ee5606b36caaaaf739d833e67770ce9fd9b3ec80210257c293086c4d4fe8943deda5f890a37d11bebd140e220faa76258a41d077b4d42103c2660a46aa73078ee6016dee953488566426cf55fc8011edd0085634d75395f92103cd3e383ec6e12719a6c69515e5559bcbe037d0aa24c187e1e26ce932e22ad7b354ae68ffffffff0281c00e00000000001976a9140ab5592fc4c28f2301014da9981a3f1d7ad3d4af88ac597ebc2d4900000017a914056d0d9c5b14dd720d9f61fdb3f557c074f95cef8700000000";
        BtcTransaction btcTx = new BtcTransaction(btcMainnetParams, Hex.decode(btcTxHex));
        TransactionInput txInput = btcTx.getInput(FIRST_INPUT_INDEX);
        Script txInputScriptSig = txInput.getScriptSig();
        byte[] firstSignatureData = txInputScriptSig.getChunks().get(1).data;
        BtcECKey.ECDSASignature signature = TransactionSignature.decodeFromDER(firstSignatureData);
        Script redeemScriptFromInput = BridgeUtils.extractRedeemScriptFromInput(btcTx.getInput(0)).get();
        byte[] firstPubKey = redeemScriptFromInput.getChunks().get(2).data;

        // Act
        Optional<Sha256Hash> firstInputSigHash = BitcoinUtils.getFirstInputSigHash(btcTx);

        // Assert

        // It will be false if the extracted inputSigHash is different from the inputSigHash used for generating the signature.
        Assertions.assertTrue(BtcECKey.verify(firstInputSigHash.get().getBytes(), signature, firstPubKey));
    }

    @Test
    void test_getFirstInputSigHash_from_multisig() {
        // Arrange
        int totalSigners = signers.size();
        int requiredSignatures = totalSigners / 2 + 1;

        Script redeemScript = ScriptBuilder.createRedeemScript(requiredSignatures, signers);

        Address destinationAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "destinationAddress");

        BtcTransaction btcTx = new BtcTransaction(btcMainnetParams);
        btcTx.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        btcTx.addOutput(Coin.COIN, destinationAddress);

        RedeemData redeemData = RedeemData.of(pubKeys, redeemScript);

        Script p2SHScript = ScriptBuilder.createP2SHOutputScript(redeemScript);
        Script inputScript = p2SHScript.createEmptyInputScript(null, redeemData.redeemScript);

        btcTx.getInput(FIRST_INPUT_INDEX).setScriptSig(inputScript);

        Sha256Hash expectedSigHash = btcTx.hashForSignature(
            FIRST_INPUT_INDEX,
            redeemScript,
            BtcTransaction.SigHash.ALL,
            false
        );

        // Act
        Optional<Sha256Hash> sigHash = BitcoinUtils.getFirstInputSigHash(btcTx);

        // Assert
        Assertions.assertTrue(sigHash.isPresent());
        Assertions.assertEquals(expectedSigHash, sigHash.get());
    }

    @Test
    void test_getFirstInputSigHash_from_fed() {
        // Arrange
        Federation federation = new Federation(
            FederationTestUtils.getFederationMembersWithBtcKeys(signers),
            Instant.ofEpochMilli(1000L),
            0L,
            btcMainnetParams
        );

        BtcTransaction btcTx = new BtcTransaction(btcMainnetParams);
        btcTx.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        btcTx.addOutput(Coin.COIN, destinationAddress);

        Script p2SHScript = ScriptBuilder.createP2SHOutputScript(federation.getRedeemScript());
        Script inputScript = p2SHScript.createEmptyInputScript(null, federation.getRedeemScript());

        btcTx.getInput(FIRST_INPUT_INDEX).setScriptSig(inputScript);

        Sha256Hash expectedSigHash = btcTx.hashForSignature(
            FIRST_INPUT_INDEX,
            federation.getRedeemScript(),
            BtcTransaction.SigHash.ALL,
            false
        );

        // Act
        Optional<Sha256Hash> sigHash = BitcoinUtils.getFirstInputSigHash(btcTx);

        // Assert
        Assertions.assertTrue(sigHash.isPresent());
        Assertions.assertEquals(expectedSigHash, sigHash.get());
    }

    @Test
    void test_getFirstInputSigHash_from_p2shFed() {
        // Arrange
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.tbd600().forBlock(0);
        P2shErpFederation federation = new P2shErpFederation(
            FederationTestUtils.getFederationMembersWithBtcKeys(signers),
            Instant.ofEpochMilli(1000L),
            0L,
            btcMainnetParams,
            bridgeMainnetConstants.getErpFedPubKeysList(),
            bridgeMainnetConstants.getErpFedActivationDelay(),
            activations
        );

        BtcTransaction btcTx = new BtcTransaction(btcMainnetParams);
        btcTx.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        btcTx.addOutput(Coin.COIN, destinationAddress);

        Script p2SHScript = ScriptBuilder.createP2SHOutputScript(federation.getRedeemScript());
        Script fedInputScript = p2SHScript.createEmptyInputScript(null, federation.getRedeemScript());
        btcTx.getInput(FIRST_INPUT_INDEX).setScriptSig(fedInputScript);

        Sha256Hash expectedSigHash = btcTx.hashForSignature(
            FIRST_INPUT_INDEX,
            federation.getRedeemScript(),
            BtcTransaction.SigHash.ALL,
            false
        );

        // Act
        Optional<Sha256Hash> sigHash = BitcoinUtils.getFirstInputSigHash(btcTx);

        // Assert
        Assertions.assertTrue(sigHash.isPresent());
        Assertions.assertEquals(expectedSigHash, sigHash.get());
    }

    @Test
    void test_getFirstInputSigHash_from_flyoverFed() {
        // Arrange
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.tbd600().forBlock(0);

        P2shErpFederation p2shErpFederation = new P2shErpFederation(
            FederationTestUtils.getFederationMembersWithBtcKeys(signers),
            Instant.ofEpochMilli(1000L),
            0L,
            btcMainnetParams,
            bridgeMainnetConstants.getErpFedPubKeysList(),
            bridgeMainnetConstants.getErpFedActivationDelay(),
            activations
        );

        Script flyoverP2shErpRedeemScript = FastBridgeP2shErpRedeemScriptParser.createFastBridgeP2shErpRedeemScript(
            p2shErpFederation.getRedeemScript(),
            BitcoinTestUtils.createHash(1)
        );

        BtcTransaction btcTx = new BtcTransaction(btcMainnetParams);
        btcTx.addInput(BitcoinTestUtils.createHash(1), 0, new Script(new byte[]{}));
        btcTx.addOutput(Coin.COIN, destinationAddress);

        RedeemData redeemData = RedeemData.of(p2shErpFederation.getBtcPublicKeys(), flyoverP2shErpRedeemScript);
        Script flyoverP2sh = ScriptBuilder.createP2SHOutputScript(flyoverP2shErpRedeemScript);
        Script fedInputScript = flyoverP2sh.createEmptyInputScript(redeemData.keys.get(0), redeemData.redeemScript);
        btcTx.getInput(FIRST_INPUT_INDEX).setScriptSig(fedInputScript);

        Sha256Hash expectedSigHash = btcTx.hashForSignature(
            FIRST_INPUT_INDEX,
            flyoverP2shErpRedeemScript,
            BtcTransaction.SigHash.ALL,
            false
        );

        // Act
        Optional<Sha256Hash> sigHash = BitcoinUtils.getFirstInputSigHash(btcTx);

        // Assert
        Assertions.assertTrue(sigHash.isPresent());
        Assertions.assertEquals(expectedSigHash, sigHash.get());
    }

    @Test
    void test_getFirstInputSigHash_no_input() {
        // Arrange
        BtcTransaction btcTx = new BtcTransaction(btcMainnetParams);
        btcTx.addOutput(Coin.COIN, destinationAddress);

        // Act
        Optional<Sha256Hash> sigHash = BitcoinUtils.getFirstInputSigHash(btcTx);

        // Assert
        Assertions.assertFalse(sigHash.isPresent());
    }

    @Test
    void test_getFirstInputSigHash_invalid_input_no_redeem_script() {
        // Arrange
        Address destinationAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "destinationAddress");

        BtcTransaction btcTx = new BtcTransaction(btcMainnetParams);
        btcTx.addInput(BitcoinTestUtils.createHash(1), 0, new Script(new byte[]{}));
        btcTx.addOutput(Coin.COIN, destinationAddress);

        // Act
        Optional<Sha256Hash> sigHash = BitcoinUtils.getFirstInputSigHash(btcTx);

        // Assert
        Assertions.assertFalse(sigHash.isPresent());
    }

    @Test
    void test_getFirstInputSigHash_many_inputs() {
        // Arrange
        int totalSigners = signers.size();
        int requiredSignatures = totalSigners / 2 + 1;

        Script redeemScript = ScriptBuilder.createRedeemScript(requiredSignatures, signers);
        Script p2SHScript = ScriptBuilder.createP2SHOutputScript(requiredSignatures, pubKeys);

        Address destinationAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "destinationAddress");

        BtcTransaction btcTx = new BtcTransaction(btcMainnetParams);
        Set<Sha256Hash> sigHashes = new HashSet<>();

        for (int i = 0; i < 50; i++) {
            btcTx.addInput(BitcoinTestUtils.createHash(1), 0, new Script(new byte[]{}));
            RedeemData redeemData = RedeemData.of(pubKeys, redeemScript);

            Script inputScript = p2SHScript.createEmptyInputScript(redeemData.keys.get(0), redeemData.redeemScript);
            btcTx.getInput(i).setScriptSig(inputScript);

            Sha256Hash sigHash = btcTx.hashForSignature(
                FIRST_INPUT_INDEX,
                redeemScript,
                BtcTransaction.SigHash.ALL,
                false
            );
            sigHashes.add(sigHash);
        }
        btcTx.addOutput(Coin.COIN, destinationAddress);

        Sha256Hash expectedSigHash = btcTx.hashForSignature(
            FIRST_INPUT_INDEX,
            redeemScript,
            BtcTransaction.SigHash.ALL,
            false
        );

        // Act
        Optional<Sha256Hash> sigHash = BitcoinUtils.getFirstInputSigHash(btcTx);

        // Assert
        Assertions.assertTrue(sigHash.isPresent());
        Assertions.assertEquals(expectedSigHash, sigHash.get());
        Assertions.assertEquals(50, sigHashes.size());
    }
}

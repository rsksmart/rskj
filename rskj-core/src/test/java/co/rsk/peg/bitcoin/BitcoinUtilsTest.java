package co.rsk.peg.bitcoin;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.script.FastBridgeP2shErpRedeemScriptParser;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.wallet.RedeemData;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeMainNetConstants;
import co.rsk.peg.Federation;
import co.rsk.peg.FederationTestUtils;
import co.rsk.peg.P2shErpFederation;
import co.rsk.peg.PegTestUtils;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

class BitcoinUtilsTest {
    private static final BridgeConstants bridgeMainnetConstants = BridgeMainNetConstants.getInstance();
    private static final NetworkParameters btcMainnetParams = bridgeMainnetConstants.getBtcParams();
    private static final int FIRST_OUTPUT_INDEX = 0;
    private static final int FIRST_INPUT_INDEX = 0;

    List<BtcECKey> signers;
    List<BtcECKey> pubKeys;
    Address destinationAddress;

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
        Script p2SHScript = ScriptBuilder.createP2SHOutputScript(requiredSignatures, pubKeys);
        Script inputScript = p2SHScript.createEmptyInputScript(redeemData.keys.get(0), redeemData.redeemScript);
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

        Script fedInputScript = PegTestUtils.createBaseInputScriptThatSpendsFromTheFederation(federation);
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

        Script fedInputScript = PegTestUtils.createBaseInputScriptThatSpendsFromTheFederation(federation);
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

        for (int i = 1; i <= 50; i++) {
            btcTx.addInput(BitcoinTestUtils.createHash(1), 0, new Script(new byte[]{}));
            RedeemData redeemData = RedeemData.of(pubKeys, redeemScript);

            Script inputScript = p2SHScript.createEmptyInputScript(redeemData.keys.get(0), redeemData.redeemScript);
            btcTx.getInput(i-1).setScriptSig(inputScript);
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
    }
}

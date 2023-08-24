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
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

class BitcoinUtilsTest {
    private static final BridgeConstants bridgeMainnetConstants = BridgeMainNetConstants.getInstance();
    private static final NetworkParameters btcMainnetParams = bridgeMainnetConstants.getBtcParams();

    @Test
    void test_getFirstInputSigHash_from_multisig() {
        // Arrange
        List<BtcECKey> signers = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02")),
            BtcECKey.fromPrivate(Hex.decode("fa03"))
        );

        List<BtcECKey> pubKeys = signers.stream().map(BtcECKey::getPubKeyPoint).map(BtcECKey::fromPublicOnly)
            .collect(Collectors.toList());
        signers.sort(BtcECKey.PUBKEY_COMPARATOR);

        int totalSigners = signers.size();
        int requiredSignatures = totalSigners / 2 + 1;

        Script redeemScript = ScriptBuilder.createRedeemScript(requiredSignatures, signers);

        Address destinationAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "destinationAddress");

        BtcTransaction btcTx = new BtcTransaction(btcMainnetParams);
        btcTx.addInput(BitcoinTestUtils.createHash(1), 0, redeemScript);
        btcTx.addOutput(Coin.COIN, destinationAddress);

        RedeemData redeemData = RedeemData.of(pubKeys, redeemScript);
        Script p2SHScript = ScriptBuilder.createP2SHOutputScript(requiredSignatures, pubKeys);
        Script inputScript = p2SHScript.createEmptyInputScript(redeemData.keys.get(0), redeemData.redeemScript);
        btcTx.getInput(0).setScriptSig(inputScript);

        Sha256Hash expectedSigHash = btcTx.hashForSignature(
            0,
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
        List<BtcECKey> signers = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02")),
            BtcECKey.fromPrivate(Hex.decode("fa03"))
        );
        signers.sort(BtcECKey.PUBKEY_COMPARATOR);

        Federation federation = new Federation(
            FederationTestUtils.getFederationMembersWithBtcKeys(signers),
            Instant.ofEpochMilli(1000L),
            0L,
            btcMainnetParams
        );

        Address destinationAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "destinationAddress");

        BtcTransaction btcTx = new BtcTransaction(btcMainnetParams);
        btcTx.addInput(BitcoinTestUtils.createHash(1), 0, federation.getRedeemScript());
        btcTx.addOutput(Coin.COIN, destinationAddress);

        Script fedInputScript = PegTestUtils.createBaseInputScriptThatSpendsFromTheFederation(federation);
        btcTx.getInput(0).setScriptSig(fedInputScript);

        Sha256Hash expectedSigHash = btcTx.hashForSignature(
            0,
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
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.tbd600().forBlock(0);
        // Arrange
        List<BtcECKey> signers = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02")),
            BtcECKey.fromPrivate(Hex.decode("fa03"))
        );
        signers.sort(BtcECKey.PUBKEY_COMPARATOR);

        P2shErpFederation federation = new P2shErpFederation(
            FederationTestUtils.getFederationMembersWithBtcKeys(signers),
            Instant.ofEpochMilli(1000L),
            0L,
            btcMainnetParams,
            bridgeMainnetConstants.getErpFedPubKeysList(),
            bridgeMainnetConstants.getErpFedActivationDelay(),
            activations
        );

        Address destinationAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "destinationAddress");

        BtcTransaction btcTx = new BtcTransaction(btcMainnetParams);
        btcTx.addInput(BitcoinTestUtils.createHash(1), 0, new Script(new byte[]{}));
        btcTx.addOutput(Coin.COIN, destinationAddress);

        Script fedInputScript = PegTestUtils.createBaseInputScriptThatSpendsFromTheFederation(federation);
        btcTx.getInput(0).setScriptSig(fedInputScript);

        Sha256Hash expectedSigHash = btcTx.hashForSignature(
            0,
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
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.tbd600().forBlock(0);
        // Arrange
        List<BtcECKey> signers = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02")),
            BtcECKey.fromPrivate(Hex.decode("fa03"))
        );
        signers.sort(BtcECKey.PUBKEY_COMPARATOR);

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

        Script flyoverP2SH = ScriptBuilder.createP2SHOutputScript(flyoverP2shErpRedeemScript);
        
        Address destinationAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "destinationAddress");

        BtcTransaction btcTx = new BtcTransaction(btcMainnetParams);
        btcTx.addInput(BitcoinTestUtils.createHash(1), 0, new Script(new byte[]{}));
        btcTx.addOutput(Coin.COIN, destinationAddress);

        RedeemData redeemData = RedeemData.of(p2shErpFederation.getBtcPublicKeys(), flyoverP2shErpRedeemScript);
        Script fedInputScript = flyoverP2SH.createEmptyInputScript(redeemData.keys.get(0), redeemData.redeemScript);
        btcTx.getInput(0).setScriptSig(fedInputScript);

        Sha256Hash expectedSigHash = btcTx.hashForSignature(
            0,
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
        List<BtcECKey> signers = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02")),
            BtcECKey.fromPrivate(Hex.decode("fa03"))
        );
        signers.sort(BtcECKey.PUBKEY_COMPARATOR);

        Address destinationAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "destinationAddress");

        BtcTransaction btcTx = new BtcTransaction(btcMainnetParams);
        btcTx.addOutput(Coin.COIN, destinationAddress);

        // Act
        Optional<Sha256Hash> sigHash = BitcoinUtils.getFirstInputSigHash(btcTx);

        // Assert
        Assertions.assertFalse(sigHash.isPresent());
    }

    @Test
    void test_getFirstInputSigHash_many_inputs() {
        // Arrange
        List<BtcECKey> signers = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02")),
            BtcECKey.fromPrivate(Hex.decode("fa03"))
        );

        List<BtcECKey> pubKeys = signers.stream().map(BtcECKey::getPubKeyPoint).map(BtcECKey::fromPublicOnly)
            .collect(Collectors.toList());
        signers.sort(BtcECKey.PUBKEY_COMPARATOR);

        int totalSigners = signers.size();
        int requiredSignatures = totalSigners / 2 + 1;

        Script redeemScript = ScriptBuilder.createRedeemScript(requiredSignatures, signers);
        Script p2SHScript = ScriptBuilder.createP2SHOutputScript(requiredSignatures, pubKeys);

        Address destinationAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "destinationAddress");

        BtcTransaction btcTx = new BtcTransaction(btcMainnetParams);

        for (int i = 1; i <= 50; i++) {
            btcTx.addInput(BitcoinTestUtils.createHash(1), 0, redeemScript);
            RedeemData redeemData = RedeemData.of(pubKeys, redeemScript);

            Script inputScript = p2SHScript.createEmptyInputScript(redeemData.keys.get(0), redeemData.redeemScript);
            btcTx.getInput(i-1).setScriptSig(inputScript);
        }
        btcTx.addOutput(Coin.COIN, destinationAddress);

        Sha256Hash expectedSigHash = btcTx.hashForSignature(
            0,
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

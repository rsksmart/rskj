package co.rsk.peg.bitcoin;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.constants.BridgeTestNetConstants;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.federation.P2shErpFederationBuilder;
import co.rsk.peg.federation.P2shP2wshErpFederationBuilder;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static co.rsk.peg.bitcoin.BitcoinTestUtils.signWitnessTransactionInputFromP2shMultiSig;
import static co.rsk.peg.bitcoin.BitcoinUtils.addSpendingFederationBaseScript;
import static co.rsk.peg.bitcoin.BitcoinUtils.extractRedeemScriptFromInput;
import static org.junit.jupiter.api.Assertions.*;

class BitcoinUtilsLegacyTest {
    private final BridgeConstants bridgeConstants = BridgeMainNetConstants.getInstance();
    private final NetworkParameters networkParameters = bridgeConstants.getBtcParams();

    @Test
    void getMultiSigTransactionHashWithoutSignaturesBeforeRSKIP305_fromRealPeginFromP2PKHWithParseableScriptPubKey_shouldThrowISE() {
        // data from real tx https://mempool.space/testnet/tx/77a135b5f233671686e655e462efa5d87013d94b105b8fcacc219e78503866a6
        // arrange
        byte[] testnetRealPeginSerialized = Hex.decode("0200000002d5dfff21c0e1f0b02dcbcda77a56ffd6412b79d2081bc4bc0466cf3f0913b297010000006a47304402201dc13fabe4b29d0a596a84f6f15b3d2d636625a8aa02acb8a4635038322040e10220421d22271ca64b7c02b496dc916f092ea84a74e811f81a328f2f9d888aaee59b01210342e7b7961475e1fcb0e604ed34fc554f14ce7f931373646e98e463ae52a4b564fdffffffd5dfff21c0e1f0b02dcbcda77a56ffd6412b79d2081bc4bc0466cf3f0913b297020000006a47304402207e0add6292ac318db6657aeeb717818136f0f4d6e4efef35302ce72a79731fba0220297c3259eed72cd15fae7e0b9a63d2321e415eb6bd36c023bd179455f236147301210342e7b7961475e1fcb0e604ed34fc554f14ce7f931373646e98e463ae52a4b564fdffffff030000000000000000446a4252534b540147bc43b214c418c101b976f8bbb5101ed262a069011ae302de6607907116810e598b83897b00f764d5c0eff2d4911d78c411bf873de759e10d3b2eeaba04bc0300000000001976a914dfc505d84d81d346563fe9726a76c28e9ea8454588ac20a107000000000017a91405804450706addc3c6df3a400a22397ecaafe2d687cfba3d00");
        BtcTransaction testnetRealPegin = new BtcTransaction(BridgeTestNetConstants.getInstance().getBtcParams(), testnetRealPeginSerialized);

        // act & assert
        assertThrows(
            IllegalStateException.class,
            () -> BitcoinUtilsLegacy.getMultiSigTransactionHashWithoutSignaturesBeforeRSKIP305(testnetRealPegin)
        );
    }

    @Test
    void getMultiSigTransactionHashWithoutSignaturesBeforeRSKIP305_fromP2PKH_withParseableScriptPubKey_shouldThrowISE() {
        // arrange
        BtcTransaction tx = new BtcTransaction(networkParameters);
        Script parseableScriptPubKeyInputScript = ScriptBuilder.createInputScript(null, BtcECKey.fromPublicOnly(
            Hex.decode("0377a6c71c43d9fac4343f87538cd2880cf5ebefd3dd1d9aabdbbf454bca162de9")
        ));
        tx.addInput(BitcoinTestUtils.createHash(1), 0, parseableScriptPubKeyInputScript);

        Address someAddress = BitcoinTestUtils.createP2PKHAddress(networkParameters, "destinationAddress");
        tx.addOutput(Coin.COIN, someAddress);

        // act & assert
        int inputIndex = 0;
        Optional<Script> rs = extractRedeemScriptFromInput(tx, inputIndex);
        assertTrue(rs.isPresent());

        assertThrows(
            IllegalStateException.class,
            () -> BitcoinUtilsLegacy.getMultiSigTransactionHashWithoutSignaturesBeforeRSKIP305(tx)
        );
    }

    // copied tests from BitcoinUtilsTest
    // as we expect same behavior in all other cases
    @Test
    void getMultiSigTransactionHashWithoutSignatures_whenTransactionDoesNotHaveInputs_shouldReturnSameTxHash() {
        // arrange
        BtcTransaction transaction = new BtcTransaction(networkParameters);
        BtcTransaction transactionBeforeSigning = new BtcTransaction(networkParameters, transaction.bitcoinSerialize());
        Sha256Hash transactionHashBeforeRemovingSignatures = transactionBeforeSigning.getHash();

        // act
        Sha256Hash transactionHashWithoutSignatures = BitcoinUtilsLegacy.getMultiSigTransactionHashWithoutSignaturesBeforeRSKIP305(transaction);
        BtcTransaction transactionWithoutSignatures = BitcoinUtils.getMultiSigTransactionWithoutSignatures(transaction);

        // assert
        assertEquals(transactionHashBeforeRemovingSignatures, transactionHashWithoutSignatures);
        assertEquals(transactionBeforeSigning, transactionWithoutSignatures);
    }

    @Test
    void getMultiSigTransactionHashWithoutSignatures_whenTransactionIsSegwit_shouldReturnExpectedTxHash() {
        // arrange
        Federation federation = P2shP2wshErpFederationBuilder.builder().build();
        BtcTransaction prevTx = new BtcTransaction(networkParameters);
        Coin prevValue = Coin.COIN;
        prevTx.addOutput(prevValue, federation.getAddress());

        BtcTransaction transaction = new BtcTransaction(networkParameters);
        Script emptyScript = new Script(new byte[]{});
        transaction.addInput(BitcoinTestUtils.createHash(1), 0, emptyScript);
        addSpendingFederationBaseScript(
            transaction,
            0,
            federation.getRedeemScript(),
            federation.getFormatVersion()
        );

        BtcTransaction transactionBeforeSigning = new BtcTransaction(networkParameters, transaction.bitcoinSerialize());

        List<BtcECKey> keysToSign = BitcoinTestUtils.getBtcEcKeysFromSeeds(new String[]{
            "member01",
            "member02",
            "member03",
            "member04",
            "member05"
        }, true); // using some of the private keys from federation declared above
        signWitnessTransactionInputFromP2shMultiSig(
            transaction,
            0,
            prevValue,
            keysToSign
        );

        // act
        Sha256Hash transactionHashWithoutSignatures = BitcoinUtilsLegacy.getMultiSigTransactionHashWithoutSignaturesBeforeRSKIP305(transaction);
        BtcTransaction transactionWithoutSignatures = BitcoinUtils.getMultiSigTransactionWithoutSignatures(transaction);

        // assert
        assertEquals(transaction.getHash(), transactionHashWithoutSignatures);
        assertEquals(transactionBeforeSigning.getHash(), transactionHashWithoutSignatures);
        assertEquals(transactionBeforeSigning, transactionWithoutSignatures);
        assertEquals(transactionWithoutSignatures.getHash(), transactionHashWithoutSignatures);
    }

    @Test
    void getMultiSigTransactionHashWithoutSignatures_whenTxIsNotSegwitAndTransactionInputsDoNotHaveP2shMultiSigInputScript_shouldThrowIAE() {
        // arrange
        BtcTransaction transaction = new BtcTransaction(networkParameters);
        BtcECKey pubKey = BitcoinTestUtils.getBtcEcKeyFromSeed("abc");
        Script p2pkhScriptSig = ScriptBuilder.createInputScript(null, pubKey);
        transaction.addInput(BitcoinTestUtils.createHash(1), 0, p2pkhScriptSig);

        // act & assert
        assertThrows(IllegalArgumentException.class, () -> BitcoinUtilsLegacy.getMultiSigTransactionHashWithoutSignaturesBeforeRSKIP305(transaction));
        assertThrows(IllegalArgumentException.class, () -> BitcoinUtils.getMultiSigTransactionWithoutSignatures(transaction));
    }

    @Test
    void getMultiSigTransactionHashWithoutSignatures_whenTxIsNotSegwitButNotAllTransactionInputsHaveP2shMultiSigInputScript_shouldThrowIAE() {
        // arrange
        Federation federation = P2shErpFederationBuilder.builder().build();
        Script p2shMultiSigScriptSig = federation.getP2SHScript().createEmptyInputScript(null, federation.getRedeemScript());
        BtcECKey pubKey = BitcoinTestUtils.getBtcEcKeyFromSeed("abc");
        Script p2pkhScriptSig = ScriptBuilder.createInputScript(null, pubKey);

        BtcTransaction transaction = new BtcTransaction(networkParameters);
        transaction.addInput(BitcoinTestUtils.createHash(2), 0, p2shMultiSigScriptSig);
        transaction.addInput(BitcoinTestUtils.createHash(1), 0, p2pkhScriptSig);

        // act & assert
        assertThrows(IllegalArgumentException.class, () -> BitcoinUtilsLegacy.getMultiSigTransactionHashWithoutSignaturesBeforeRSKIP305(transaction));
        assertThrows(IllegalArgumentException.class, () -> BitcoinUtils.getMultiSigTransactionWithoutSignatures(transaction));
    }

    @Test
    void getMultiSigTransactionHashWithoutSignatures_whenTransactionIsLegacyAndInputsHaveP2shMultiSigInputScript_shouldReturnExpectedTxHash() {
        // arrange
        Federation federation = P2shErpFederationBuilder.builder().build();
        Script scriptSig = federation.getP2SHScript().createEmptyInputScript(null, federation.getRedeemScript());

        BtcTransaction transaction = new BtcTransaction(networkParameters);
        transaction.addInput(BitcoinTestUtils.createHash(1), 0, scriptSig);
        transaction.addInput(BitcoinTestUtils.createHash(2), 0, scriptSig);

        Address destinationAddress = BitcoinTestUtils.createP2PKHAddress(networkParameters, "destinationAddress");
        transaction.addOutput(Coin.COIN, destinationAddress);
        BtcTransaction transactionBeforeSigning = new BtcTransaction(networkParameters, transaction.bitcoinSerialize());
        Sha256Hash transactionHashBeforeSigning = transactionBeforeSigning.getHash();

        List<BtcECKey> keysToSign = BitcoinTestUtils.getBtcEcKeysFromSeeds(new String[]{
            "member01",
            "member02",
            "member03",
            "member04",
            "member05"
        }, true); // using private keys from federation declared above
        List<TransactionInput> inputs = transaction.getInputs();
        for (TransactionInput input : inputs) {
            BitcoinTestUtils.signLegacyTransactionInputFromP2shMultiSig(
                transaction,
                inputs.indexOf(input),
                keysToSign
            );
        }

        // act
        Sha256Hash transactionHashWithoutSignatures = BitcoinUtilsLegacy.getMultiSigTransactionHashWithoutSignaturesBeforeRSKIP305(transaction);
        BtcTransaction transactionWithoutSignatures = BitcoinUtils.getMultiSigTransactionWithoutSignatures(transaction);

        // assert
        assertEquals(transactionBeforeSigning, transactionWithoutSignatures);
        assertEquals(transactionHashBeforeSigning, transactionHashWithoutSignatures);
        assertEquals(transactionWithoutSignatures.getHash(), transactionHashWithoutSignatures);
    }
}

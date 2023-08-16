package co.rsk.peg;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.crypto.TransactionSignature;
import co.rsk.bitcoinj.script.P2shErpFederationRedeemScriptParser;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.config.BridgeTestNetConstants;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import org.junit.jupiter.api.Test;
import org.spongycastle.util.encoders.Hex;

import java.util.ArrayList;
import java.util.List;

import static co.rsk.bitcoinj.script.ScriptOpCodes.OP_0;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class P2shP2wshErpFederationTest {

//    @Test
//    void spendFromP2shP2wshAddress() throws Exception {
//        NetworkParameters networkParameters = BridgeTestNetConstants.getInstance().getBtcParams();
//
//        String wifPrivKey1 = "cMgRyc5tpWLWKfs2gSv6zRXu2ZaQSqwVtU6oNbQneSgCB13JCQNA";
//        String wifPrivKey2 = "cW19KKSRKHATNb4oPPvyaSqLCs82nqFyZqQbVY6zaD82YtERYL51";
//        String wifPrivKey3 = "cUq9ruifqrtMPMwwu6SrKPr6A7egwhvPZxwE7uMmWQdn11LsHpWi";
//
//        BtcECKey key1 = DumpedPrivateKey.fromBase58(networkParameters, wifPrivKey1).getKey();
//        BtcECKey key2 = DumpedPrivateKey.fromBase58(networkParameters, wifPrivKey2).getKey();
//        BtcECKey key3 = DumpedPrivateKey.fromBase58(networkParameters, wifPrivKey3).getKey();
//
//        List<BtcECKey> standardKeys = new ArrayList<>();
//        standardKeys.add(key1);
//        standardKeys.add(key2);
//        standardKeys.add(key3);
//
//        Script redeemScript = new ScriptBuilder().createRedeemScript(standardKeys.size()/2+1, standardKeys);
//
//        Coin prevValue = Coin.valueOf(10_000);
//        Coin value = Coin.valueOf(10_000);
//        Coin fee = Coin.valueOf(1_000);
//
//        assertDoesNotThrow(() -> FederationErpP2shP2wshTestUtils.spendFromP2shP2wshAddress(
//            networkParameters,
//            redeemScript,
//            standardKeys,
//            Sha256Hash.wrap("e692d0daeda4b41fc38304df2d8b6ad537e11c687c29d6334d4f3026ab859621"),
//            0,
//            Address.fromBase58(networkParameters,"msgc5Gtz2L9MVhXPDrFRCYPa16QgoZ2EjP"),
//            value,
//            false
//        ));
//    }

    @Test
    void spendFromP2shP2wshAddressWithNewRedeem() {

        NetworkParameters networkParameters = NetworkParameters.fromID(NetworkParameters.ID_TESTNET);

        String[] seeds = new String[67];
        for (int i = 0; i < 67; i++ ) {
            int j = i + 1;
            seeds[i] = ("fed" + j);
        }
        List<BtcECKey> publicKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            seeds,
            true
        );

        Script redeemScript = new ScriptBuilder().createNewRedeemScript(publicKeys.size() / 2 + 1, publicKeys);

        Script p2shP2wshOutputScript = ScriptBuilder.createP2SHP2WSHOutputScript(redeemScript);
        Address segwitAddress = Address.fromP2SHScript(
            networkParameters,
            p2shP2wshOutputScript
        );
        System.out.println(segwitAddress);

        Sha256Hash fundTxHash = Sha256Hash.wrap("acf3e31839e0ff5740c2f92d0a3c61a529e4773a2c4ec1f216d91d6ed4f7b8f3");
        int outputIndex = 0;
        Address destinationAddress = Address.fromBase58(networkParameters, "msgc5Gtz2L9MVhXPDrFRCYPa16QgoZ2EjP"); // testnet
        Coin value = Coin.valueOf(10_000);
        Coin fee = Coin.valueOf(3_000);

        BtcTransaction spendTx = new BtcTransaction(networkParameters);
        spendTx.addInput(fundTxHash, outputIndex, new Script(new byte[]{}));
        spendTx.addOutput(value.minus(fee), destinationAddress);
        spendTx.setVersion(2);

        byte[] redeemScriptHash = Sha256Hash.hash(redeemScript.getProgram());
        Script scriptSig = new ScriptBuilder().number(OP_0).data(redeemScriptHash).build();
        Script segwitScriptSig = new ScriptBuilder().data(scriptSig.getProgram()).build();
        spendTx.getInput(0).setScriptSig(segwitScriptSig);

        // Create signatures
        int inputIndex = 0;
        Sha256Hash sigHash = spendTx.hashForWitnessSignature(
            inputIndex,
            redeemScript,
            value,
            BtcTransaction.SigHash.ALL,
            false
        );

        int thresholdSignaturesSize = publicKeys.size() / 2 + 1;
        List<TransactionSignature> thresholdSignatures = new ArrayList<>();

        for (int i = 0; i < thresholdSignaturesSize; i++) {
            BtcECKey keyToSign = publicKeys.get(i);
            BtcECKey.ECDSASignature signature = keyToSign.sign(sigHash);
            TransactionSignature txSignature = new TransactionSignature(
                signature,
                BtcTransaction.SigHash.ALL,
                false
            );
            thresholdSignatures.add(txSignature);
        }

        TransactionWitness txWitness = TransactionWitness.createWitnessScriptWithNewRedeem(redeemScript, thresholdSignatures, publicKeys.size());
        spendTx.setWitness(inputIndex, txWitness);


        // Uncomment to print the raw tx in console and broadcast https://blockstream.info/testnet/tx/push
        System.out.println(Hex.toHexString(spendTx.bitcoinSerialize()));
    }

    @Test
    void spendFromP2shP2wshErpStandardFed() {
        NetworkParameters networkParameters = BridgeTestNetConstants.getInstance().getBtcParams();
        long activationDelay = 30;

        List<BtcECKey> standardKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{
                "fed1", "fed2", "fed3", "fed4", "fed5", "fed6", "fed7", "fed8", "fed9", "fed10",
                "fed11", "fed12", "fed13", "fed14", "fed15", "fed16", "fed17", "fed18", "fed19", "fed20"
            },
            true
        );

        List<BtcECKey> emergencyKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{
                "erp1", "erp2", "erp3", "erp4","erp5", "erp6", "erp7", "erp8","erp9", "erp10",
                "erp11", "erp12", "erp13", "erp14","erp15", "erp16", "erp17", "erp18","erp19", "erp20"
            },
            true
        );

        Script standardRedeem = new ScriptBuilder().createRedeemScript(standardKeys.size()/2+1, standardKeys);
        Script emergencyRedeem = new ScriptBuilder().createRedeemScript(emergencyKeys.size()/2+1, emergencyKeys);
        Script redeemScript = P2shErpFederationRedeemScriptParser.createP2shP2wshErpRedeemScript(standardRedeem, emergencyRedeem, activationDelay);

        Script p2shP2wshOutputScript = ScriptBuilder.createP2SHP2WSHOutputScript(redeemScript);
        Address segwitAddress = Address.fromP2SHScript(
            networkParameters,
            p2shP2wshOutputScript
        );
        System.out.println(segwitAddress);

        Coin value = Coin.valueOf(10_000);

        // Spend from standard multisig
        assertDoesNotThrow(() -> FederationErpP2shP2wshTestUtils.spendFromP2shP2wshErpFed(
            networkParameters,
            redeemScript,
            activationDelay,
            standardKeys,
            Sha256Hash.wrap("214bed3040e1432bf23b6126a7e8ffc83ba7da4d54fe899ee12510f878444ea1"),
            0,
            Address.fromBase58(networkParameters,"msgc5Gtz2L9MVhXPDrFRCYPa16QgoZ2EjP"), // testnet
            value,
            false
        ));

/*        // Spend from emergency multisig
        assertDoesNotThrow(() -> FederationErpP2shP2wshTestUtils.spendFromP2shP2wshErpFed(
            networkParameters,
            redeemScript,
            activationDelay,
            standardKeys,
            Sha256Hash.wrap("b863291d286ba627d527dc8ec10f1c9ad4438f618fae032d8200fcb9b2577adc"),
            0,
            Address.fromBase58(networkParameters,"msgc5Gtz2L9MVhXPDrFRCYPa16QgoZ2EjP"), // testnet
            value,
            true
        ));*/
    }

    @Test
    void spendFromHugeFedWithFlyover() throws Exception {
        NetworkParameters networkParameters = BridgeTestNetConstants.getInstance().getBtcParams();
        long activationDelay = 30;

        List<BtcECKey> standardKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fed1", "fed2", "fed3", "fed4", "fed5", "fed6", "fed7", "fed8", "fed9", "fed10",
                "fed11", "fed12", "fed13", "fed14", "fed15", "fed16", "fed17", "fed18", "fed19", "fed20"},
            true
        );

        List<BtcECKey> emergencyKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"erp1", "erp2", "erp3", "erp4", "erp5", "erp6", "erp7", "erp8", "erp9", "erp10",
                "erp11", "erp12", "erp13", "erp14", "erp15", "erp16", "erp17", "erp18", "erp19", "erp20"},
            true
        );

        Sha256Hash flyoverDerivationPath = Sha256Hash.wrap("0100000000000000000000000000000000000000000000000000000000000000");

        Script standardRedeem = new ScriptBuilder().createRedeemScript(standardKeys.size()/2+1, standardKeys);
        Script emergencyRedeem = new ScriptBuilder().createRedeemScript(emergencyKeys.size()/2+1, emergencyKeys);
        Script redeemScript = P2shErpFederationRedeemScriptParser.createP2shP2wshErpRedeemScriptWithFlyover(standardRedeem, emergencyRedeem, flyoverDerivationPath, activationDelay);

        Script p2shP2wshOutputScript = ScriptBuilder.createP2SHP2WSHOutputScript(redeemScript);
        Address segwitAddress = Address.fromP2SHScript(
            networkParameters,
            p2shP2wshOutputScript
        );
        System.out.println(segwitAddress);

        Coin value = Coin.valueOf(100_000);

        // Spend from standard multisig
        assertDoesNotThrow(() -> FederationErpP2shP2wshTestUtils.spendFromP2shP2wshErpFed(
            networkParameters,
            redeemScript,
            activationDelay,
            standardKeys,
            Sha256Hash.wrap("ca9c3ff2685d65a0adf571a30d77c6d12857af9edfbf8f3a19ca4c1fc1eb47f5"), // mainnet tx to 20-20 members and flyover - used
            0,
            Address.fromBase58(networkParameters,"12MXsCtte9onzqaHwN5VcnwZKGd7oDSsQq"),
            value,
            false
        ));

        // Spend from emergency multisig
        assertDoesNotThrow(() -> FederationErpP2shP2wshTestUtils.spendFromP2shP2wshErpFed(
            networkParameters,
            redeemScript,
            activationDelay,
            standardKeys,
            Sha256Hash.wrap("ca9c3ff2685d65a0adf571a30d77c6d12857af9edfbf8f3a19ca4c1fc1eb47f5"), // mainnet tx to 20-20 members and flyover - used
            0,
            Address.fromBase58(networkParameters,"12MXsCtte9onzqaHwN5VcnwZKGd7oDSsQq"),
            value,
            false
        ));
    }
}

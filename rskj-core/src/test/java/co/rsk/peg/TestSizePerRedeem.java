package co.rsk.peg;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.crypto.TransactionSignature;
import co.rsk.bitcoinj.script.P2shP2wshErpFederationNewRedeemScriptParser;
import co.rsk.bitcoinj.script.P2shP2wshErpFederationRedeemScriptParser;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import org.junit.jupiter.api.Test;
import org.spongycastle.util.encoders.Hex;

import java.util.ArrayList;
import java.util.List;

import static co.rsk.bitcoinj.script.ScriptOpCodes.OP_0;

public class TestSizePerRedeem {
    @Test
    void currentRedeem() {
        NetworkParameters networkParameters = NetworkParameters.fromID(NetworkParameters.ID_TESTNET); // testnet
        long activationDelay = 30;

        String[] seeds = new String[20];
        for (int i = 0; i < 20; i++ ) {
            int j = i + 1;
            seeds[i] = ("fed" + j);
        }
        List<BtcECKey> standardKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            seeds,
            true
        );

        String[] emergencySeeds = new String[20];
        for (int i = 0; i < 20; i++ ) {
            int j = i + 1;
            emergencySeeds[i] = ("erp" + j);
        }
        List<BtcECKey> emergencyKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            emergencySeeds,
            true
        );

        Script standardRedeemScript = new ScriptBuilder().createRedeemScript(standardKeys.size() / 2 + 1, standardKeys);
        Script emergencyRedeemScript = new ScriptBuilder().createRedeemScript(emergencyKeys.size() / 2 + 1, emergencyKeys);
        Script redeemScript = P2shP2wshErpFederationRedeemScriptParser.createP2shP2wshErpRedeemScript(standardRedeemScript, emergencyRedeemScript, activationDelay);

        Script p2shP2wshOutputScript = ScriptBuilder.createP2SHP2WSHOutputScript(redeemScript);
        Address segwitAddress = Address.fromP2SHScript(
            networkParameters,
            p2shP2wshOutputScript
        );
        System.out.println(segwitAddress);

        byte[] redeemScriptHash = Sha256Hash.hash(redeemScript.getProgram());
        Script scriptSig = new ScriptBuilder().number(OP_0).data(redeemScriptHash).build();
        Script segwitScriptSig = new ScriptBuilder().data(scriptSig.getProgram()).build();

        // create tx
        BtcTransaction spendTx = new BtcTransaction(networkParameters);
        spendTx.setVersion(2);

        //Address destinationAddress = Address.fromBase58(networkParameters, "12MXsCtte9onzqaHwN5VcnwZKGd7oDSsQq"); // mainnet
        Address destinationAddress = Address.fromBase58(networkParameters, "msgc5Gtz2L9MVhXPDrFRCYPa16QgoZ2EjP"); // testnet

        Coin valueFromFunding = Coin.valueOf(10_000);
        Coin fee = Coin.valueOf(50_000);
        Coin amountToSend = valueFromFunding.multiply(20).minus(fee);

        spendTx.addOutput(amountToSend, destinationAddress);

        int redeemScriptLength = redeemScript.getProgram().length;
        int thresholdSignaturesSize = standardKeys.size() / 2 + 1;
        int weightPerInput = calculateWeightPerInput(thresholdSignaturesSize, redeemScriptLength);
        int maxStandardWeight = 400000;

        List <UTXO> utxos = new ArrayList<>();
        for (int i = 0; i < Math.floorDiv(maxStandardWeight, weightPerInput); i++) {
            UTXO utxo = new UTXO(
                Sha256Hash.of(new byte[]{1}),
                i,
                Coin.COIN,
                0,
                false,
                redeemScript
            );
            utxos.add(utxo);
        }

        Sha256Hash fundTxHash = Sha256Hash.wrap("32a3715b8eea5189f008d981ed909f8ca65ba5c78696368dbdb61b4e2e512208"); //utxos.get(0).getHash();
        int approximatedWeight = spendTx.bitcoinSerialize().length;
        int index = 0;

        while (approximatedWeight + weightPerInput < maxStandardWeight /* + 4000 && index < 160*/) {
            spendTx.addInput(fundTxHash, index, new Script(new byte[]{}));
            spendTx.getInput(index).setScriptSig(segwitScriptSig);

            index ++;
            approximatedWeight += weightPerInput;
        }

        for (int i = 0; i < spendTx.getInputs().size(); i++) {
            // Create signatures
            Sha256Hash sigHash = spendTx.hashForWitnessSignature(
                i,
                redeemScript,
                valueFromFunding,
                BtcTransaction.SigHash.ALL,
                false
            );
            List<TransactionSignature> thresholdSignatures = new ArrayList<>();

            for (int j = 0; j < thresholdSignaturesSize; j++) {
                BtcECKey keyToSign = standardKeys.get(j);
                BtcECKey.ECDSASignature signature = keyToSign.sign(sigHash);
                TransactionSignature txSignature = new TransactionSignature(
                    signature,
                    BtcTransaction.SigHash.ALL,
                    false
                );
                thresholdSignatures.add(txSignature);
            }
            TransactionWitness txWitness = TransactionWitness.createWitnessErpStandardScript(redeemScript, thresholdSignatures);

            spendTx.setWitness(i, txWitness);
        }

        // Uncomment to print the raw tx in console and broadcast https://blockstream.info/testnet/tx/push
        System.out.println(Hex.toHexString(spendTx.bitcoinSerialize()));
    }
    @Test
    void newRedeem() {
        NetworkParameters networkParameters = NetworkParameters.fromID(NetworkParameters.ID_TESTNET); // testnet
        long activationDelay = 30;

        String[] seeds = new String[61];
        for (int i = 0; i < 61; i++ ) {
            int j = i + 1;
            seeds[i] = ("fed" + j);
        }
        List<BtcECKey> standardKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            seeds,
            true
        );

        String[] emergencySeeds = new String[4];
        for (int i = 0; i < 4; i++ ) {
            int j = i + 1;
            emergencySeeds[i] = ("erp" + j);
        }
        List<BtcECKey> emergencyKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            emergencySeeds,
            true
        );

        Script standardRedeemScript = new ScriptBuilder().createNewRedeemScript(standardKeys.size() / 2 + 1, standardKeys);
        Script emergencyRedeemScript = new ScriptBuilder().createNewRedeemScript(emergencyKeys.size() / 2 + 1, emergencyKeys);
        Script redeemScript = P2shP2wshErpFederationNewRedeemScriptParser.createErpP2shP2wshNewRedeemScript(standardRedeemScript, emergencyRedeemScript, activationDelay);

        Script p2shP2wshOutputScript = ScriptBuilder.createP2SHP2WSHOutputScript(redeemScript);
        Address segwitAddress = Address.fromP2SHScript(
            networkParameters,
            p2shP2wshOutputScript
        );
        System.out.println(segwitAddress);

        byte[] redeemScriptHash = Sha256Hash.hash(redeemScript.getProgram());
        Script scriptSig = new ScriptBuilder().number(OP_0).data(redeemScriptHash).build();
        Script segwitScriptSig = new ScriptBuilder().data(scriptSig.getProgram()).build();

        // create tx
        BtcTransaction spendTx = new BtcTransaction(networkParameters);
        spendTx.setVersion(2);

        //Address destinationAddress = Address.fromBase58(networkParameters, "12MXsCtte9onzqaHwN5VcnwZKGd7oDSsQq"); // mainnet
        Address destinationAddress = Address.fromBase58(networkParameters, "msgc5Gtz2L9MVhXPDrFRCYPa16QgoZ2EjP"); // testnet

        Coin valueFromFunding = Coin.valueOf(10_000);
        Coin fee = Coin.valueOf(100_000);
        Coin amount = valueFromFunding.multiply(20);

        spendTx.addOutput(amount.minus(fee), destinationAddress);

        int redeemScriptLength = redeemScript.getProgram().length;
        int thresholdSignaturesSize = standardKeys.size() / 2 + 1;
        int weightPerInput = calculateWeightPerInput(thresholdSignaturesSize, redeemScriptLength);
        int maxStandardWeight = 400000;

        List <UTXO> utxos = new ArrayList<>();
        for (int i = 0; i < Math.floorDiv(maxStandardWeight, weightPerInput); i++) {
            UTXO utxo = new UTXO(
                Sha256Hash.of(new byte[]{1}),
                i,
                Coin.COIN,
                0,
                false,
                redeemScript
            );
            utxos.add(utxo);
        }

        Sha256Hash fundTxHash = utxos.get(0).getHash();
        int approximatedWeight = spendTx.bitcoinSerialize().length;
        int index = 0;

        while (approximatedWeight + weightPerInput < maxStandardWeight) {
            spendTx.addInput(fundTxHash, index, new Script(new byte[]{}));
            spendTx.getInput(index).setScriptSig(segwitScriptSig);

            index ++;
            approximatedWeight += weightPerInput;
        }

        for (int i = 0; i < spendTx.getInputs().size(); i++) {
            // Create signatures
            Sha256Hash sigHash = spendTx.hashForWitnessSignature(
                i,
                redeemScript,
                valueFromFunding,
                BtcTransaction.SigHash.ALL,
                false
            );
            List<TransactionSignature> thresholdSignatures = new ArrayList<>();

            for (int j = 0; j < thresholdSignaturesSize; j++) {
                BtcECKey keyToSign = standardKeys.get(j);
                BtcECKey.ECDSASignature signature = keyToSign.sign(sigHash);
                TransactionSignature txSignature = new TransactionSignature(
                    signature,
                    BtcTransaction.SigHash.ALL,
                    false
                );
                thresholdSignatures.add(txSignature);
            }
            TransactionWitness txWitness = TransactionWitness.createWitnessErpStandardNewScript(redeemScript, thresholdSignatures, standardKeys.size());

            spendTx.setWitness(i, txWitness);
        }

        // Uncomment to print the raw tx in console and broadcast https://blockstream.info/testnet/tx/push
        System.out.println(Hex.toHexString(spendTx.bitcoinSerialize()));
    }

    public int calculateWeightPerInput(int thresholdSignaturesSize, int redeemScriptSize) {
        int bytesPerInput = 0;
        int weightPerInput = 0;

        bytesPerInput += 40; // for prevTxHash, index, nSequence
        bytesPerInput += 36; // script sig
        weightPerInput += bytesPerInput * 3; // until here we have the baseSize per input

        bytesPerInput += thresholdSignaturesSize * 73; // signatures
        bytesPerInput += thresholdSignaturesSize - 1; // empty signatures
        bytesPerInput += 1; // op notif
        bytesPerInput += redeemScriptSize; // redeem size
        weightPerInput += bytesPerInput;

        return weightPerInput;
    }
}

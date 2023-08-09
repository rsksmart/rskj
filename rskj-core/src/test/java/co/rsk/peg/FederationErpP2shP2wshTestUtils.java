package co.rsk.peg;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.crypto.TransactionSignature;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import org.bouncycastle.util.encoders.Hex;

import java.util.ArrayList;
import java.util.List;

import static co.rsk.bitcoinj.script.ScriptOpCodes.OP_0;
import static co.rsk.peg.ReleaseTransactionBuilder.BTC_TX_VERSION_2;

public class FederationErpP2shP2wshTestUtils {
    public static void spendFromP2shP2wshAddress(
        NetworkParameters networkParameters,
        Script redeemScript,
        List<BtcECKey> signers,
        Sha256Hash fundTxHash,
        int outputIndex,
        Address receiver,
        Coin prevValue,
        Coin value) {

        BtcTransaction spendTx = new BtcTransaction(networkParameters);
        spendTx.addInput(fundTxHash, outputIndex, redeemScript);
        spendTx.addOutput(value, receiver);
        spendTx.setVersion(BTC_TX_VERSION_2);

        // Create signatures
        Sha256Hash sigHash = spendTx.hashForWitnessSignature(
            0,
            redeemScript,
            prevValue,
            BtcTransaction.SigHash.ALL,
            false
        );

        byte[] redeemScriptHash = Sha256Hash.hash(redeemScript.getProgram());
        Script scriptSig = new ScriptBuilder().number(OP_0).data(redeemScriptHash).build();
        Script segwitScriptSig = new ScriptBuilder().data(scriptSig.getProgram()).build();

        spendTx.getInput(0).setScriptSig(segwitScriptSig);

        int totalSigners = signers.size();
        List<TransactionSignature> allTxSignatures = new ArrayList<>();

        for (int i = 0; i < totalSigners; i++) {
            BtcECKey keyToSign = signers.get(i);
            BtcECKey.ECDSASignature signature = keyToSign.sign(sigHash);
            TransactionSignature txSignature = new TransactionSignature(
                signature,
                BtcTransaction.SigHash.ALL,
                false
            );
            allTxSignatures.add(txSignature);
        }

        int requiredSignatures = totalSigners / 2 + 1;
        List<TransactionSignature> txSignatures = allTxSignatures.subList(0, requiredSignatures);

        TransactionWitness txWitness = TransactionWitness.createWitnessScript(redeemScript, txSignatures);
        spendTx.setWitness(0, txWitness);

        // Uncomment to print the raw tx in console and broadcast https://blockstream.info/testnet/tx/push
        System.out.println(Hex.toHexString(spendTx.bitcoinSerialize()));
    }


    public static void spendFromP2shP2wshErpStandardFed(
        NetworkParameters networkParameters,
        Script redeemScript,
        List<BtcECKey> signers,
        Sha256Hash fundTxHash,
        int outputIndex,
        Address receiver,
        Coin prevValue,
        Coin value
    ) {

        BtcTransaction spendTx = new BtcTransaction(networkParameters);
        spendTx.setVersion(BTC_TX_VERSION_2);
        spendTx.addInput(fundTxHash, outputIndex, redeemScript);
        spendTx.addOutput(value, receiver);

        // Create signatures
        Sha256Hash sigHash = spendTx.hashForWitnessSignature(
            0,
            redeemScript,
            prevValue,
            BtcTransaction.SigHash.ALL,
            false
        );

        byte[] redeemScriptHash = Sha256Hash.hash(redeemScript.getProgram());
        Script scriptSig = new ScriptBuilder().number(OP_0).data(redeemScriptHash).build();
        Script segwitScriptSig = new ScriptBuilder().data(scriptSig.getProgram()).build();

        spendTx.getInput(0).setScriptSig(segwitScriptSig);

        int totalSigners = signers.size();
        List<TransactionSignature> allTxSignatures = new ArrayList<>();

        for (int i = 0; i < totalSigners; i++) {
            BtcECKey keyToSign = signers.get(i);
            BtcECKey.ECDSASignature signature = keyToSign.sign(sigHash);
            TransactionSignature txSignature = new TransactionSignature(
                signature,
                BtcTransaction.SigHash.ALL,
                false
            );
            allTxSignatures.add(txSignature);
        }

        int requiredSignatures = totalSigners / 2 + 1;
        List<TransactionSignature> txSignatures = allTxSignatures.subList(0, requiredSignatures);

        TransactionWitness txWitness = TransactionWitness.createWitnessErpScript(redeemScript, txSignatures);
        spendTx.setWitness(0, txWitness);

        int witnessScriptSize = (int) calculateWitnessScriptSize(txWitness); // HAS TO BE LOWER THAN 10000
        String rawTx = Hex.toHexString(spendTx.bitcoinSerialize());
        int txTotalSize = rawTx.length() / 2;

        double estimatedTxVirtualSize = calculateTxSize(witnessScriptSize, txTotalSize);

        Coin fee = prevValue.minus(value);
        Coin estimatedFeeRate = fee.divide((long) estimatedTxVirtualSize);

        // Uncomment to print the raw tx in console and broadcast https://blockstream.info/testnet/tx/push
        System.out.println(Hex.toHexString(spendTx.bitcoinSerialize()));
    }

    public static void spendFromP2shP2wshErpEmergencyFed(
        NetworkParameters networkParameters,
        Script redeemScript,
        long activationDelay,
        List<BtcECKey> signers,
        Sha256Hash fundTxHash,
        int outputIndex,
        Address receiver,
        Coin prevValue,
        Coin value
    ) {
        BtcTransaction spendTx = new BtcTransaction(networkParameters);
        spendTx.setVersion(BTC_TX_VERSION_2);
        spendTx.addInput(fundTxHash, outputIndex, redeemScript);
        spendTx.addOutput(value, receiver);
        spendTx.getInput(0).setSequenceNumber(activationDelay);

        // Create signatures
        Sha256Hash sigHash = spendTx.hashForWitnessSignature(
            0,
            redeemScript,
            prevValue,
            BtcTransaction.SigHash.ALL,
            false
        );

        byte[] redeemScriptHash = Sha256Hash.hash(redeemScript.getProgram());
        Script scriptSig = new ScriptBuilder().number(OP_0).data(redeemScriptHash).build();
        Script segwitScriptSig = new ScriptBuilder().data(scriptSig.getProgram()).build();

        spendTx.getInput(0).setScriptSig(segwitScriptSig);

        int totalSigners = signers.size();
        List<TransactionSignature> allTxSignatures = new ArrayList<>();

        for (int i = 0; i < totalSigners; i++) {
            BtcECKey keyToSign = signers.get(i);
            BtcECKey.ECDSASignature signature = keyToSign.sign(sigHash);
            TransactionSignature txSignature = new TransactionSignature(
                signature,
                BtcTransaction.SigHash.ALL,
                false
            );
            allTxSignatures.add(txSignature);
        }

        int requiredSignatures = totalSigners / 2 + 1;
        List<TransactionSignature> txSignatures = allTxSignatures.subList(0, requiredSignatures);

        TransactionWitness txWitness = TransactionWitness.createWitnessErpEmergencyScript(redeemScript, txSignatures);
        spendTx.setWitness(0, txWitness);

        // Uncomment to print the raw tx in console and broadcast https://blockstream.info/testnet/tx/push
        System.out.println(Hex.toHexString(spendTx.bitcoinSerialize()));
    }

    public static double calculateTxSize(int witnessScriptSize, int txTotalSize) {
        int estimatedBaseSize = (txTotalSize - witnessScriptSize);

        // this is how tx weight is calculated
        double estimatedTxWeight = txTotalSize + (3 * estimatedBaseSize);

        // virtual size can be calculated from either of this two calculations
        double estimatedTxVirtualSize = Math.max((0.25 * txTotalSize) + (0.75 * estimatedBaseSize), estimatedTxWeight / 4);

        return estimatedTxVirtualSize;
    }

    public static double calculateWitnessScriptSize(TransactionWitness txWitness) {
        int witnessSize = 0;
        for (int i = 0; i < txWitness.getPushCount(); i++) {
            witnessSize += txWitness.getPush(i).length;
        }
        return witnessSize;
    }

}

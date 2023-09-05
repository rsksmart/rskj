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

    public static void spendFromP2shP2wshErpFed(
        NetworkParameters networkParameters,
        Script redeemScript,
        long activationDelay,
        List<BtcECKey> signers,
        Sha256Hash fundTxHash,
        int outputIndex,
        LegacyAddress receiver,
        Coin inputValue,
        boolean spendsFromEmergency) {

        Coin fee = Coin.valueOf(1_000);

        BtcTransaction spendTx = new BtcTransaction(networkParameters);
        spendTx.addInput(fundTxHash, outputIndex, new Script(new byte[]{}));
        spendTx.addOutput(inputValue.minus(fee), receiver);
        spendTx.setVersion(BTC_TX_VERSION_2);
/*        if (spendsFromEmergency) {
            spendTx.getInput(0).setSequenceNumber(activationDelay);
        }*/

        // Create signatures
        int inputIndex = 0;
        Sha256Hash sigHash = spendTx.hashForWitnessSignature(
            inputIndex,
            redeemScript,
            inputValue,
            BtcTransaction.SigHash.ALL,
            false
        );

        byte[] redeemScriptHash = Sha256Hash.hash(redeemScript.getProgram());
        Script scriptSig = new ScriptBuilder().number(OP_0).data(redeemScriptHash).build();
        Script segwitScriptSig = new ScriptBuilder().data(scriptSig.getProgram()).build();
        spendTx.getInput(0).setScriptSig(segwitScriptSig);

        int requiredSignatures = signers.size() / 2 + 1;
        List<TransactionSignature> signatures = new ArrayList<>();

        for (int i = 0; i < requiredSignatures; i++) {
            BtcECKey keyToSign = signers.get(i);
            BtcECKey.ECDSASignature signature = keyToSign.sign(sigHash);
            TransactionSignature txSignature = new TransactionSignature(
                signature,
                BtcTransaction.SigHash.ALL,
                false
            );
            signatures.add(txSignature);
        }

        TransactionWitness txWitness = TransactionWitness.createWitnessErpStandardScript(redeemScript, signatures);
        spendTx.setWitness(inputIndex, txWitness);

        int size;
        int totalSize;
        int baseSize;

        int inputsQuantity = spendTx.getInputs().size();
        int outputsQuantity = spendTx.getOutputs().size();

        baseSize = calculateTxBaseSize(spendTx, inputsQuantity, outputsQuantity);
        totalSize = 1 + 1; // 1 byte before inputs + 1 byte before outputs
        totalSize += baseSize + estimatedBytesForSigning(redeemScript.getProgram().length, requiredSignatures, inputsQuantity);

        size = calculateWitnessTxVirtualSize(baseSize, totalSize);

        Coin satsPerByte = Coin.valueOf(10);
        Coin estimatedFee = satsPerByte.multiply(size);

        // Uncomment to print the raw tx in console and broadcast https://blockstream.info/testnet/tx/push
        System.out.println(Hex.toHexString(spendTx.bitcoinSerialize()));
    }

    public static int calculateTxBaseSize(BtcTransaction spendTx, int inputsQuantity, int outputsQuantity) {
        int baseSize = 0;

        for (int i = 0; i < inputsQuantity; i++) {
            byte[] input = spendTx.getInput(i).bitcoinSerialize();
            System.out.println(Hex.toHexString(input));
            baseSize += input.length;
        }

        for (int i = 0; i < outputsQuantity; i++) {
            byte[] output = spendTx.getOutput(i).bitcoinSerialize();
            System.out.println(Hex.toHexString(output));
            baseSize += output.length;
        }

        baseSize += 4; // version
        baseSize += 1; // marker
        baseSize += 1; // flag
        baseSize += 4; // locktime

        return baseSize;
    }


    public static int calculateWitnessTxWeight(int txBaseSize, int txTotalSize) {

        // As described in https://github.com/bitcoin/bips/blob/master/bip-0141.mediawiki#transaction-size-calculations
        int txWeight = txTotalSize + (3 * txBaseSize);
        return txWeight;
    }

    public static int calculateWitnessTxVirtualSize(int txBaseSize, int txTotalSize) {
        double txWeight = calculateWitnessTxWeight(txBaseSize, txTotalSize);

        // As described in https://github.com/bitcoin/bips/blob/master/bip-0141.mediawiki#transaction-size-calculations
        int txVirtualSize = (int) Math.ceil(txWeight / 4);
        return txVirtualSize;
    }

    public static int estimatedBytesForSigning(int redeemScriptSize, int requiredSignatures, int inputsQuantity) {
        int estimatedBytesForSignatures = requiredSignatures * 73;
        estimatedBytesForSignatures += 1 + 1; // empty byte before sigs, op_notif argument
        int someBytes = 3; // redeem script size in hexa
        return (estimatedBytesForSignatures + redeemScriptSize + someBytes) * inputsQuantity;
    }
}

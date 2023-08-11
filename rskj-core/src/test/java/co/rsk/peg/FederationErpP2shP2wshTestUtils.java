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
        Address receiver,
        Coin inputValue,
        boolean spendsFromEmergency) {

        BtcTransaction spendTx = new BtcTransaction(networkParameters);
        spendTx.addInput(fundTxHash, outputIndex, new Script(new byte[]{}));
        spendTx.addOutput(inputValue, receiver); // This value will be updated once the fee is calculated
        spendTx.setVersion(BTC_TX_VERSION_2);
        if (spendsFromEmergency) {
            spendTx.getInput(0).setSequenceNumber(activationDelay);
        }

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

        byte[] notIfArgument = spendsFromEmergency ? new byte[] {1} : new byte[] {};
        TransactionWitness txWitness = TransactionWitness.createWitnessErpScript(redeemScript, signatures, notIfArgument);
        spendTx.setWitness(inputIndex, txWitness);


        Coin satsPerByte = Coin.valueOf(10);
        int txVirtualSize = calculateTxVirtualSize(spendTx);
        Coin fee = satsPerByte.multiply(txVirtualSize);
        spendTx.getOutput(0).setValue(inputValue.minus(fee));

        // Uncomment to print the raw tx in console and broadcast https://blockstream.info/testnet/tx/push
        System.out.println(Hex.toHexString(spendTx.bitcoinSerialize()));
    }

    public static int calculateTxBaseSize(BtcTransaction spendTx) {
        int baseSize = 0;

        int inputsQuantity = spendTx.getInputs().size();
        for (int i = 0; i < inputsQuantity; i++) {
            byte[] input = spendTx.getInput(i).bitcoinSerialize();
            baseSize += input.length;
        }

        int outputsQuantity = spendTx.getOutputs().size();
        for (int i = 0; i < outputsQuantity; i++) {
            byte[] output = spendTx.getOutput(i).bitcoinSerialize();
            baseSize += output.length;
        }

        baseSize += 4; // version size
        baseSize += 1; // marker size
        baseSize += 1; // flag size
        baseSize += 4; // locktime size

        return baseSize;
    }

    public static int calculateTxWeight(BtcTransaction spendTx) {
        int txTotalSize = spendTx.bitcoinSerialize().length;
        int txBaseSize = calculateTxBaseSize(spendTx);

        // As described in https://github.com/bitcoin/bips/blob/master/bip-0141.mediawiki#transaction-size-calculations
        int txWeight = txTotalSize + (3 * txBaseSize);
        return txWeight;
    }

    public static int calculateTxVirtualSize(BtcTransaction spendTx) {
        double txWeight = calculateTxWeight(spendTx);

        // As described in https://github.com/bitcoin/bips/blob/master/bip-0141.mediawiki#transaction-size-calculations
        int txVirtualSize = (int) Math.ceil(txWeight / 4);
        return txVirtualSize;
    }
}

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

        Coin fee = Coin.valueOf(1_000);
        Coin outputValue = inputValue.minus(fee);

        BtcTransaction spendTx = new BtcTransaction(networkParameters);
        spendTx.addInput(fundTxHash, outputIndex, new Script(new byte[]{}));
        spendTx.addOutput(outputValue, receiver);
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

        // Uncomment to print the raw tx in console and broadcast https://blockstream.info/testnet/tx/push
        System.out.println(Hex.toHexString(spendTx.bitcoinSerialize()));
    }
}

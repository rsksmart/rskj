package co.rsk.peg;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.crypto.TransactionSignature;
import co.rsk.bitcoinj.script.P2shP2wshErpFederationNewRedeemScriptParser;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import org.junit.jupiter.api.Test;
import org.spongycastle.util.encoders.Hex;

import java.util.ArrayList;
import java.util.List;

import static co.rsk.bitcoinj.script.ScriptOpCodes.OP_0;

public class TestSizeWithNewRedeem {
    @Test
    void checkSizes() {
        int maxStandardSize = 100000;

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

        //Address destinationAddress = Address.fromBase58(networkParameters, "12MXsCtte9onzqaHwN5VcnwZKGd7oDSsQq"); // mainnet
        Address destinationAddress = Address.fromBase58(networkParameters, "msgc5Gtz2L9MVhXPDrFRCYPa16QgoZ2EjP"); // testnet

        Coin value = Coin.valueOf(10_000);
        Coin fee = Coin.valueOf(2_600);
        Coin amount = value.multiply(2);

        BtcTransaction spendTx = new BtcTransaction(networkParameters);
        spendTx.addOutput(amount.minus(fee), destinationAddress);
        spendTx.setVersion(2);

        byte[] redeemScriptHash = Sha256Hash.hash(redeemScript.getProgram());
        Script scriptSig = new ScriptBuilder().number(OP_0).data(redeemScriptHash).build();
        Script segwitScriptSig = new ScriptBuilder().data(scriptSig.getProgram()).build();

        int currentSize = spendTx.bitcoinSerialize().length;
        int currentBaseSize = spendTx.bitcoinSerialize().length;
        int bytesPerInput = 0;
        int baseBytesPerInput;
        bytesPerInput += 40; // for prevTxHash, index, nSequence
        bytesPerInput += 36; // script sig
        baseBytesPerInput = bytesPerInput;

        int thresholdSignaturesSize = standardKeys.size() / 2 + 1;
        bytesPerInput += thresholdSignaturesSize * 75; // signatures
        bytesPerInput += thresholdSignaturesSize - 1; // empty signatures
        bytesPerInput += 1; // op notif
        bytesPerInput += redeemScript.getProgram().length; // redeem

        //int outputIndex = 0;
        //while ( outputIndex < maxNumberOfOutputs /*&& currentSize + bytesPerInput < maxStandardSize*/) {

        Sha256Hash fundTxHash = Sha256Hash.wrap("589d01c0b4fcba2fe48bcddc9315029aae204dc487b6a8e4712e2cd4775c67e2");
        int currentInput = 0;
        for (int k = 5; k < 7; k++)  {
            int i = currentInput;
            spendTx.addInput(fundTxHash, k, new Script(new byte[]{}));

            // Create signatures
            Sha256Hash sigHash = spendTx.hashForWitnessSignature(
                i,
                redeemScript,
                value,
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
            spendTx.getInput(i).setScriptSig(segwitScriptSig);

            currentBaseSize += baseBytesPerInput;
            currentSize = spendTx.bitcoinSerialize().length;
            currentInput += 1;
        }

        System.out.println(currentInput);
        System.out.println(currentSize);
        System.out.println(Math.ceil(currentBaseSize * 3 + currentSize)/4);

        // Uncomment to print the raw tx in console and broadcast https://blockstream.info/testnet/tx/push
        System.out.println(Hex.toHexString(spendTx.bitcoinSerialize()));
    }
}

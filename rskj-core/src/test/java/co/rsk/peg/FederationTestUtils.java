/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.peg;

import static co.rsk.bitcoinj.script.ScriptOpCodes.OP_0;
import static co.rsk.peg.ReleaseTransactionBuilder.BTC_TX_VERSION_2;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.crypto.TransactionSignature;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import java.math.BigInteger;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.ECKey;

public class FederationTestUtils {

    public static Federation getFederation(Integer... federationMemberPks) {
        return new Federation(
            getFederationMembersFromPks(federationMemberPks),
            ZonedDateTime.parse("2017-06-10T02:30:01Z").toInstant(),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );
    }

    public static List<FederationMember> getFederationMembers(int memberCount) {
        List<FederationMember> result = new ArrayList<>();
        for (int i = 1; i <= memberCount; i++) {
            result.add(new FederationMember(
                BtcECKey.fromPrivate(BigInteger.valueOf((i) * 100)),
                ECKey.fromPrivate(BigInteger.valueOf((i) * 101)),
                ECKey.fromPrivate(BigInteger.valueOf((i) * 102))
            ));
        }
        result.sort(FederationMember.BTC_RSK_MST_PUBKEYS_COMPARATOR);
        return result;
    }

    public static List<FederationMember> getFederationMembersFromPks(Integer... pks) {
        return Arrays.stream(pks).map(n -> new FederationMember(
            BtcECKey.fromPrivate(BigInteger.valueOf(n)),
            ECKey.fromPrivate(BigInteger.valueOf(n+1)),
            ECKey.fromPrivate(BigInteger.valueOf(n+2))
        )).collect(Collectors.toList());
    }

    public static List<FederationMember> getFederationMembersWithBtcKeys(List<BtcECKey> keys) {
        return keys.stream()
            .map(btcKey -> new FederationMember(btcKey, new ECKey(), new ECKey()))
            .collect(Collectors.toList());
    }

    public static List<FederationMember> getFederationMembersWithKeys(List<BtcECKey> pks) {
        return pks.stream().map(pk -> getFederationMemberWithKey(pk)).collect(Collectors.toList());
    }

    public static FederationMember getFederationMemberWithKey(BtcECKey pk) {
        ECKey ethKey = ECKey.fromPublicOnly(pk.getPubKey());
        return new FederationMember(pk, ethKey, ethKey);
    }

    public static void spendFromErpFed(
        NetworkParameters networkParameters,
        ErpFederation federation,
        List<BtcECKey> signers,
        Sha256Hash fundTxHash,
        int outputIndex,
        Address receiver,
        Coin value,
        boolean signWithEmergencyMultisig) {

        BtcTransaction spendTx = new BtcTransaction(networkParameters);
        spendTx.addInput(fundTxHash, outputIndex, new Script(new byte[]{}));
        spendTx.addOutput(value, receiver);

        if (signWithEmergencyMultisig) {
            spendTx.setVersion(BTC_TX_VERSION_2);
            spendTx.getInput(0).setSequenceNumber(federation.getActivationDelay());
        }

        // Create signatures
        Sha256Hash sigHash = spendTx.hashForSignature(
            0,
            federation.getRedeemScript(),
            BtcTransaction.SigHash.ALL,
            false
        );

        int totalSigners = signers.size();
        List<BtcECKey.ECDSASignature> allSignatures = new ArrayList<>();
        for (int i = 0; i < totalSigners; i++) {
            BtcECKey keyToSign = signers.get(i);
            BtcECKey.ECDSASignature signature = keyToSign.sign(sigHash);
            allSignatures.add(signature);
        }

        // Try different signature permutations
        int requiredSignatures = totalSigners / 2 + 1;
        int permutations = totalSigners % 2 == 0 ? requiredSignatures - 1 : requiredSignatures;
        for (int i = 0; i < permutations; i++) {
            List<BtcECKey.ECDSASignature> signatures = allSignatures.subList(i, requiredSignatures + i);
            Script inputScript = createInputScriptSig(federation.getRedeemScript(), signatures, signWithEmergencyMultisig);
            spendTx.getInput(0).setScriptSig(inputScript);
            inputScript.correctlySpends(
                spendTx,
                0,
                federation.getP2SHScript(),
                Script.ALL_VERIFY_FLAGS
            );
        }

        // Uncomment to print the raw tx in console and broadcast https://blockstream.info/testnet/tx/push
//        System.out.println(Hex.toHexString(spendTx.bitcoinSerialize()));
    }

    private static Script createInputScriptSig(
        Script fedRedeemScript,
        List<BtcECKey.ECDSASignature> signatures,
        boolean signWithTheEmergencyMultisig) {

        ScriptBuilder scriptBuilder = new ScriptBuilder();

        scriptBuilder = scriptBuilder.number(0);
        for (BtcECKey.ECDSASignature signature : signatures) {
            TransactionSignature txSignature = new TransactionSignature(
                signature,
                BtcTransaction.SigHash.ALL,
                false
            );
            byte[] txSignatureEncoded = txSignature.encodeToBitcoin();

            scriptBuilder = scriptBuilder.data(txSignatureEncoded);
        }
        int flowOpCode = signWithTheEmergencyMultisig ? 1 : 0;

        return scriptBuilder
            .number(flowOpCode)
            .data(fedRedeemScript.getProgram())
            .build();
    }

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

    public static void spendFromP2shP2wshErpFed(
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

/*        if (signWithEmergencyMultisig) {
            spendTx.getInput(0).setSequenceNumber(activationDelay);
        }*/

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

        // Uncomment to print the raw tx in console and broadcast https://blockstream.info/testnet/tx/push
        System.out.println(Hex.toHexString(spendTx.bitcoinSerialize()));
    }

}

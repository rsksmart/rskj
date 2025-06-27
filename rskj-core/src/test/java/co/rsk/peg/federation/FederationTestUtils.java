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

package co.rsk.peg.federation;

import static co.rsk.peg.PegTestUtils.createBaseInputScriptThatSpendsFromTheFederation;
import static co.rsk.peg.ReleaseTransactionBuilder.BTC_TX_VERSION_2;
import static co.rsk.peg.bitcoin.BitcoinUtils.buildSegwitScriptSig;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.crypto.TransactionSignature;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.federation.constants.FederationConstants;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.ECKey;

public final class FederationTestUtils {

    public static final List<BtcECKey> REGTEST_FEDERATION_PRIVATE_KEYS = Arrays.asList(
        BtcECKey.fromPrivate(Hex.decode("45c5b07fc1a6f58892615b7c31dca6c96db58c4bbc538a6b8a22999aaa860c32")),
        BtcECKey.fromPrivate(Hex.decode("505334c7745df2fc61486dffb900784505776a898377172ffa77384892749179")),
        BtcECKey.fromPrivate(Hex.decode("bed0af2ce8aa8cb2bc3f9416c9d518fdee15d1ff15b8ded28376fcb23db6db69"))
    );

    private FederationTestUtils() {
    }

    public static ErpFederation getErpFederation(NetworkParameters networkParameters) {
        final List<BtcECKey> fedSigners = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa01", "fa02", "fa03", "fa04", "fa05", "fa06", "fa07", "fa08", "fa09"}, true
        );

        return getErpFederationWithPrivKeys(networkParameters, fedSigners);
    }

    public static ErpFederation getErpFederationWithPrivKeys(NetworkParameters networkParameters, List<BtcECKey> fedSigners) {
        final List<BtcECKey> erpSigners = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fb01", "fb02", "fb03", "fb04"}, true
        );

        List<FederationMember> fedMember = FederationTestUtils.getFederationMembersWithBtcKeys(
            fedSigners
        );

        FederationArgs federationArgs = new FederationArgs(
            fedMember,
            Instant.ofEpochMilli(0),
            0,
            networkParameters
        );

        long erpFedActivationDelay = 52_560; // Mainnet value

        return FederationFactory.buildP2shErpFederation(
            federationArgs,
            erpSigners,
            erpFedActivationDelay
        );
    }

    public static Federation getFederation(Integer... federationMemberPks) {
        FederationArgs federationArgs = new FederationArgs(
            getFederationMembersFromPks(federationMemberPks),
            Instant.ofEpochMilli(0),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );
        return FederationFactory.buildStandardMultiSigFederation(
            federationArgs
        );
    }

    public static Federation getFederationWithPrivateKeys(List<BtcECKey> federationMemberKeys) {
        FederationArgs federationArgs = new FederationArgs(
            getFederationMembersWithBtcKeys(federationMemberKeys),
            Instant.ofEpochMilli(0),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );
        return FederationFactory.buildStandardMultiSigFederation(
            federationArgs
        );
    }

    public static Federation getGenesisFederation(FederationConstants federationConstants) {
        final long GENESIS_FEDERATION_CREATION_BLOCK_NUMBER = 1L;
        final List<BtcECKey> genesisFederationPublicKeys = federationConstants.getGenesisFederationPublicKeys();
        final List<FederationMember> federationMembers = FederationMember.getFederationMembersFromKeys(genesisFederationPublicKeys);
        final Instant genesisFederationCreationTime = federationConstants.getGenesisFederationCreationTime();
        final FederationArgs federationArgs = new FederationArgs(
            federationMembers,
            genesisFederationCreationTime,
            GENESIS_FEDERATION_CREATION_BLOCK_NUMBER,
            federationConstants.getBtcParams()
        );

        return FederationFactory.buildStandardMultiSigFederation(federationArgs);
    }

    public static List<FederationMember> getFederationMembers(int memberCount) {
        List<FederationMember> result = new ArrayList<>();
        for (long i = 1; i <= memberCount; i++) {
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
            .toList();
    }

    public static List<FederationMember> getFederationMembersWithKeys(List<BtcECKey> pks) {
        return pks.stream().map(FederationTestUtils::getFederationMemberWithKey).toList();
    }

    public static FederationMember getFederationMemberWithKey(BtcECKey pk) {
        ECKey ethKey = ECKey.fromPublicOnly(pk.getPubKey());
        return new FederationMember(pk, ethKey, ethKey);
    }

    public static ErpFederation createP2shErpFederation(FederationConstants federationConstants, List<BtcECKey> federationKeys) {
        federationKeys.sort(BtcECKey.PUBKEY_COMPARATOR);
        List<FederationMember> fedMembers = getFederationMembersWithBtcKeys(federationKeys);
        Instant creationTime = Instant.ofEpochMilli(1000L);
        NetworkParameters btcParams = federationConstants.getBtcParams();
        List<BtcECKey> erpPubKeys = federationConstants.getErpFedPubKeysList();
        long activationDelay = federationConstants.getErpFedActivationDelay();

        FederationArgs federationArgs = new FederationArgs(fedMembers, creationTime, 0L, btcParams);
        return FederationFactory.buildP2shErpFederation(federationArgs, erpPubKeys, activationDelay);
    }

    public static void spendFromErpSegwitCompatibleFed(
        NetworkParameters networkParameters,
        ErpFederation federation,
        List<BtcECKey> signers,
        Sha256Hash fundTxHash,
        int outputIndex,
        Address receiver,
        Coin value) {

        BtcTransaction spendTx = new BtcTransaction(networkParameters);
        spendTx.addInput(fundTxHash, outputIndex, new Script(new byte[]{}));
        spendTx.addOutput(value, receiver);

        int inputIndex = 0;
        spendTx.setVersion(BTC_TX_VERSION_2);
        spendTx.getInput(inputIndex).setSequenceNumber(federation.getActivationDelay());

        // Create signatures
        Script redeemScript = federation.getRedeemScript();
        Sha256Hash sigHash = spendTx.hashForWitnessSignature(
            inputIndex,
            redeemScript,
            value,
            BtcTransaction.SigHash.ALL,
            false
        );

        int numberOfSignaturesRequired = federation.getNumberOfEmergencySignaturesRequired();
        List<TransactionSignature> signatures = new ArrayList<>();
        for (int i = 0; i < numberOfSignaturesRequired; i++) {
            BtcECKey keyToSign = signers.get(i);
            BtcECKey.ECDSASignature signature = keyToSign.sign(sigHash);
            TransactionSignature txSignature = new TransactionSignature(
                signature,
                BtcTransaction.SigHash.ALL,
                false
            );
            signatures.add(txSignature);
        }

        TransactionWitness witness = createSpendingFederationScriptForEmergencyKeys(redeemScript, signatures, federation.getNumberOfEmergencySignaturesRequired());
        spendTx.setWitness(inputIndex, witness);

        Script segwitScriptSig = buildSegwitScriptSig(redeemScript);
        TransactionInput input = spendTx.getInput(inputIndex);
        input.setScriptSig(segwitScriptSig);

        Script inputScript = spendTx.getInput(inputIndex).getScriptSig();
        inputScript.correctlySpends(
            spendTx,
            0,
            federation.getP2SHScript(),
            Script.ALL_VERIFY_FLAGS
        );

    }

    public static TransactionWitness createSpendingFederationScriptForEmergencyKeys(Script redeemScript, List<TransactionSignature> signatures, int requiredSignatures) {
        int pushForByteArray = 1;
        int pushForOpNotif = 1;
        int pushForRedeemScript = 1;
        int witnessSize = pushForRedeemScript + pushForOpNotif + requiredSignatures + pushForByteArray;

        List<byte[]> pushes = new ArrayList<>(witnessSize);
        // signatures to be used
        for (int i = 0; i < requiredSignatures; i++) {
            pushes.add(signatures.get(i).encodeToBitcoin());
        }

        // empty signatures
        int remainingSpace = signatures.size() - requiredSignatures;
        for (int i = 0; i < remainingSpace; i ++) {
            pushes.add(new byte[0]);
        }
        // IMPORTANT: The argument of OP_IF/NOTIF in P2WSH must be minimal.
        // Ref: https://github.com/bitcoin/bips/blob/master/bip-0141.mediawiki#new-script-semantics
        pushes.add(new byte[] {1});
        pushes.add(redeemScript.getProgram());
        return TransactionWitness.of(pushes);
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
        for (BtcECKey keyToSign : signers) {
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

    public static void addSignatures(Federation federation, List<BtcECKey> signers, BtcTransaction tx){
        Script fedInputScript = createBaseInputScriptThatSpendsFromTheFederation(federation);
        tx.getInput(0).setScriptSig(fedInputScript);

        Sha256Hash sighash = tx.hashForSignature(
            0,
            federation.getRedeemScript(),
            BtcTransaction.SigHash.ALL,
            false
        );

        int totalSigners = signers.size();
        int requiredSignatures = totalSigners / 2 + 1;

        for (int i = 0; i < requiredSignatures; i++) {
            BtcECKey privateKey = signers.get(i);
            BtcECKey publicKey = BtcECKey.fromPublicOnly(privateKey.getPubKeyPoint().getEncoded(true));

            BtcECKey.ECDSASignature signature = privateKey.sign(sighash);
            TransactionSignature txSig = new TransactionSignature(signature, BtcTransaction.SigHash.ALL, false);

            int sigIndex = fedInputScript.getSigInsertionIndex(sighash, publicKey);
            fedInputScript = ScriptBuilder.updateScriptWithSignature(
                fedInputScript,
                txSig.encodeToBitcoin(),
                sigIndex,
                1,
                1
            );
        }
        tx.getInput(0).setScriptSig(fedInputScript);
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
}

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

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.crypto.TransactionSignature;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import java.math.BigInteger;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import co.rsk.peg.constants.BridgeConstants;
import org.ethereum.crypto.ECKey;

public final class FederationTestUtils {

    private FederationTestUtils() {
    }

    public static Federation getFederation(Integer... federationMemberPks) {
        FederationArgs federationArgs = new FederationArgs(
            getFederationMembersFromPks(federationMemberPks),
            ZonedDateTime.parse("2017-06-10T02:30:01Z").toInstant(),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );
        return FederationFactory.buildStandardMultiSigFederation(
            federationArgs
        );
    }

    public static Federation getGenesisFederation(BridgeConstants bridgeConstants) {
        final long GENESIS_FEDERATION_CREATION_BLOCK_NUMBER = 1L;
        final List<BtcECKey> genesisFederationPublicKeys = bridgeConstants.getGenesisFederationPublicKeys();
        final List<FederationMember> federationMembers = FederationMember.getFederationMembersFromKeys(genesisFederationPublicKeys);
        final Instant genesisFederationCreationTime = bridgeConstants.getGenesisFederationCreationTime();
        final FederationArgs federationArgs = new FederationArgs(federationMembers, genesisFederationCreationTime, GENESIS_FEDERATION_CREATION_BLOCK_NUMBER, bridgeConstants.getBtcParams());
        return FederationFactory.buildStandardMultiSigFederation(federationArgs);
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

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

package co.rsk.peg.performance;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.TransactionInput;
import co.rsk.bitcoinj.crypto.TransactionSignature;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.script.ScriptChunk;
import co.rsk.bitcoinj.store.BtcBlockStore;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.Bridge;
import co.rsk.peg.BridgeStorageProvider;
import co.rsk.peg.Federation;
import co.rsk.peg.PegTestUtils;
import org.ethereum.TestUtils;
import org.ethereum.core.Repository;
import org.ethereum.crypto.HashUtil;
import org.ethereum.vm.exception.VMException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Disabled
class AddSignatureTest extends BridgePerformanceTestCase {
    private BtcTransaction releaseTx;
    private Keccak256 rskTxHash;
    private BtcECKey federatorThatSignsKey;

    // Keys for the regtest genesis federation, which
    // we use for benchmarking this
    private static final List<BtcECKey> federatorKeys = Arrays.asList(
            BtcECKey.fromPrivate(HashUtil.keccak256("federator1".getBytes(StandardCharsets.UTF_8))),
            BtcECKey.fromPrivate(HashUtil.keccak256("federator2".getBytes(StandardCharsets.UTF_8))),
            BtcECKey.fromPrivate(HashUtil.keccak256("federator3".getBytes(StandardCharsets.UTF_8)))
    );

    @Test
    void addSignature() throws VMException {
        ExecutionStats stats = new ExecutionStats("addSignature");

        addSignature_nonFullySigned(100, stats);
        addSignature_fullySigned(100, stats);

        Assertions.assertTrue(BridgePerformanceTest.addStats(stats));
    }

    private void addSignature_nonFullySigned(int times, ExecutionStats stats) throws VMException {
        executeAndAverage(
                "addSignature-nonFullySigned",
                times,
                getABIEncoder(),
                getInitializerFor(0),
                Helper.getZeroValueValueTxBuilderFromFedMember(),
                Helper.getRandomHeightProvider(10),
                stats
        );
    }

    private void addSignature_fullySigned(int times, ExecutionStats stats) throws VMException {
        executeAndAverage(
                "addSignature-fullySigned",
                times,
                getABIEncoder(),
                getInitializerFor(bridgeConstants.getGenesisFederation().getNumberOfSignaturesRequired()-1),
                Helper.getZeroValueValueTxBuilderFromFedMember(),
                Helper.getRandomHeightProvider(10),
                stats
        );
    }

    private ABIEncoder getABIEncoder() {
        return (int executionIndex) -> Bridge.ADD_SIGNATURE.encode(new Object[]{
                federatorThatSignsKey.getPubKey(),
                getSignaturesFor(releaseTx, federatorThatSignsKey),
                rskTxHash.getBytes()
            });
    }

    private BridgeStorageProviderInitializer getInitializerFor(int numSignatures) {
        final int minNumInputs = 1;
        final int maxNumInputs = 10;

        return (BridgeStorageProvider provider, Repository repository, int executionIndex, BtcBlockStore blockStore) -> {
            releaseTx = new BtcTransaction(networkParameters);

            Federation federation = bridgeConstants.getGenesisFederation();

            // Receiver and amounts
            BtcECKey to = new BtcECKey();
            Address toAddress = to.toAddress(networkParameters);
            Coin releaseAmount = Coin.CENT.multiply(Helper.randomInRange(10, 100));

            releaseTx.addOutput(releaseAmount, toAddress);

            // Input generation
            int numInputs = Helper.randomInRange(minNumInputs, maxNumInputs);
            for (int i = 0; i < numInputs; i++) {
                Coin inputAmount = releaseAmount.divide(numInputs);
                BtcTransaction inputTx = new BtcTransaction(networkParameters);
                inputTx.addOutput(inputAmount, federation.getAddress());
                releaseTx
                        .addInput(inputTx.getOutput(0))
                        .setScriptSig(PegTestUtils.createBaseInputScriptThatSpendsFromTheFederation(federation));
            }

            // Partial signing according to numSignatures asked for
            List<BtcECKey> keysSelection = new ArrayList<>(federatorKeys);
            Collections.shuffle(keysSelection);
            int index = 0;
            int actualNumSignatures = Math.min(numSignatures, keysSelection.size()-1);
            while (index < actualNumSignatures) {
                signInputsWith(releaseTx, keysSelection.get(index));
                index++;
            }
            // Federator that needs to sign (method call)
            federatorThatSignsKey = keysSelection.get(index);

            // Random tx hash that we then use for the method call
            rskTxHash = TestUtils.randomHash(String.valueOf(numSignatures));

            // Get the tx into the txs waiting for signatures
            try {
                provider.getRskTxsWaitingForSignatures().put(rskTxHash, releaseTx);
            } catch (IOException e) {
                throw new RuntimeException("Exception while trying to gather txs waiting for signatures for storage initialization");
            }
        };
    }

    private List<byte[]> getSignaturesFor(BtcTransaction tx, BtcECKey key) {
        List<byte[]> signatures = new ArrayList<>();

        int inputIndex = 0;
        for (TransactionInput txIn : tx.getInputs()) {
            Script inputScript = txIn.getScriptSig();
            List<ScriptChunk> chunks = inputScript.getChunks();
            byte[] program = chunks.get(chunks.size() - 1).data;
            Script redeemScript = new Script(program);
            Sha256Hash sighash = tx.hashForSignature(inputIndex, redeemScript, BtcTransaction.SigHash.ALL, false);
            BtcECKey.ECDSASignature sig = key.sign(sighash);
            signatures.add(sig.encodeToDER());
            inputIndex++;
        }

        return signatures;
    }

    private void signInputsWith(BtcTransaction tx, BtcECKey key) {
        List<byte[]> signatures = getSignaturesFor(tx, key);

        for (int i = 0; i < tx.getInputs().size(); i++) {
            TransactionInput input = tx.getInput(i);
            Script inputScript = input.getScriptSig();
            List<ScriptChunk> chunks = inputScript.getChunks();
            byte[] program = chunks.get(chunks.size() - 1).data;
            Script redeemScript = new Script(program);
            Sha256Hash sighash = tx.hashForSignature(i, redeemScript, BtcTransaction.SigHash.ALL, false);
            int sigIndex = inputScript.getSigInsertionIndex(sighash, key);
            BtcECKey.ECDSASignature sig = BtcECKey.ECDSASignature.decodeFromDER(signatures.get(i));
            TransactionSignature txSig = new TransactionSignature(sig, BtcTransaction.SigHash.ALL, false);
            inputScript = ScriptBuilder.updateScriptWithSignature(inputScript, txSig.encodeToBitcoin(), sigIndex, 1, 1);
            input.setScriptSig(inputScript);
        }
    }


}

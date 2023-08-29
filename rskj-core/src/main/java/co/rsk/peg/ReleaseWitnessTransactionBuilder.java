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

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.crypto.TransactionSignature;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.wallet.SendRequest;
import co.rsk.bitcoinj.wallet.Wallet;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static co.rsk.bitcoinj.script.ScriptOpCodes.OP_0;

public class ReleaseWitnessTransactionBuilder {

    public static final int BTC_TX_VERSION_2 = 2;

    public class BuildResult {
        private final BtcTransaction btcTx;
        private final List<UTXO> selectedUTXOs;
        private final Response responseCode;

        public BuildResult(BtcTransaction btcTx, List<UTXO> selectedUTXOs, Response responseCode) {
            this.btcTx = btcTx;
            this.selectedUTXOs = selectedUTXOs;
            this.responseCode = responseCode;
        }

        public BtcTransaction getBtcTx() {
            return btcTx;
        }

        public List<UTXO> getSelectedUTXOs() {
            return selectedUTXOs;
        }

        public Response getResponseCode() {
            return responseCode;
        }
    }

    private interface SendRequestConfigurator {
        void configure(SendRequest sr);
    }

    private static final Logger logger = LoggerFactory.getLogger("ReleaseWitnessTransactionBuilder");

    private final NetworkParameters params;
    private final Wallet wallet;
    private final Address changeAddress;
    private final Script redeemScript;
    private final List<BtcECKey> signers;
    private final Coin feePerKb;
    private final ActivationConfig.ForBlock activations;

    public ReleaseWitnessTransactionBuilder(
        NetworkParameters params,
        Wallet wallet,
        Address changeAddress,
        Script redeemScript,
        List<BtcECKey> signers,
        Coin feePerKb,
        ActivationConfig.ForBlock activations
    ) {
        this.params = params;
        this.wallet = wallet;
        this.changeAddress = changeAddress;
        this.redeemScript = redeemScript;
        this.signers = signers;
        this.feePerKb = feePerKb;
        this.activations = activations;
    }

    public Wallet getWallet() {
        return wallet;
    }

    public Address getChangeAddress() {
        return changeAddress;
    }

    public Coin getFeePerKb() {
        return feePerKb;
    }

    public BuildResult buildAmountTo(Address to, Coin amount, boolean isSegwit) {
        return buildWithConfiguration((SendRequest sr) -> {
            sr.tx.addOutput(amount, to);
            sr.changeAddress = changeAddress;
            sr.isSegwit = isSegwit;
        }, String.format("sending %s to %s", amount, to));
    }

    public BuildResult buildBatchedPegouts(List<ReleaseRequestQueue.Entry> entries) {
        return buildWithConfiguration((SendRequest sr) -> {
            for (ReleaseRequestQueue.Entry entry : entries) {
                sr.tx.addOutput(entry.getAmount(), entry.getDestination());
            }
            sr.changeAddress = changeAddress;
        }, String.format("batching %d pegouts", entries.size()));
    }

    public BuildResult buildEmptyWalletTo(Address to) {
        return buildWithConfiguration((SendRequest sr) -> {
            sr.tx.addOutput(Coin.ZERO, to);
            sr.changeAddress = to;
            sr.emptyWallet = true;
        }, String.format("emptying wallet to %s", to));
    }

    private BuildResult buildWithConfiguration(
        SendRequestConfigurator sendRequestConfigurator,
        String operationDescription) {

        // Build a tx and send request and configure it
        BtcTransaction btcTx = new BtcTransaction(params);

        btcTx.setVersion(BTC_TX_VERSION_2);

        SendRequest sr = SendRequest.forTx(btcTx);
        // Default settings
        defaultSettingsConfigurator.configure(sr);
        // Specific settings
        sendRequestConfigurator.configure(sr);

        try {
            wallet.completeTx(sr);

            // Disconnect input from output because we don't need the reference and it interferes serialization
            for (TransactionInput transactionInput : btcTx.getInputs()) {
                transactionInput.disconnect();
            }

            List<UTXO> selectedUTXOs = wallet
                .getUTXOProvider().getOpenTransactionOutputs(wallet.getWatchedAddresses()).stream()
                .filter(utxo ->
                    btcTx.getInputs().stream().anyMatch(input ->
                        input.getOutpoint().getHash().equals(utxo.getHash()) &&
                            input.getOutpoint().getIndex() == utxo.getIndex()
                    )
                )
                .collect(Collectors.toList());

            byte[] redeemScriptHash = Sha256Hash.hash(redeemScript.getProgram());
            Script scriptSig = new ScriptBuilder().number(OP_0).data(redeemScriptHash).build();
            Script segwitScriptSig = new ScriptBuilder().data(scriptSig.getProgram()).build();

            int requiredSignatures = signers.size() / 2 + 1;

            List<TransactionSignature> txSignatures = Stream
                .generate(TransactionSignature::dummy)
                .limit(requiredSignatures)
                .collect(Collectors.toList());

            TransactionWitness txWitness = TransactionWitness.createWitnessErpStandardScript(redeemScript, txSignatures);

            int txInputsSize = btcTx.getInputs().size();
            for (int i = 0; i < txInputsSize; i++) {
                btcTx.getInput(i).setScriptSig(segwitScriptSig);
                btcTx.setWitness(i, txWitness);
            }

            return new BuildResult(btcTx, selectedUTXOs, Response.SUCCESS);
        } catch (InsufficientMoneyException e) {
            logger.warn(String.format("Not enough BTC in the wallet to complete %s", operationDescription), e);
            // Comment out panic logging for now
            // panicProcessor.panic("nomoney", "Not enough confirmed BTC in the federation wallet to complete " + rskTxHash + " " + btcTx);
            return new BuildResult(null, null, Response.INSUFFICIENT_MONEY);
        } catch (Wallet.CouldNotAdjustDownwards e) {
            logger.warn(String.format("A user output could not be adjusted downwards to pay tx fees %s", operationDescription), e);
            // Comment out panic logging for now
            // panicProcessor.panic("couldnotadjustdownwards", "A user output could not be adjusted downwards to pay tx fees " + rskTxHash + " " + btcTx);
            return new BuildResult(null, null, Response.COULD_NOT_ADJUST_DOWNWARDS);
        } catch (Wallet.ExceededMaxTransactionSize e) {
            logger.warn(String.format("Tx size too big %s", operationDescription), e);
            // Comment out panic logging for now
            // panicProcessor.panic("exceededmaxtransactionsize", "Tx size too big " + rskTxHash + " " + btcTx);
            return new BuildResult(null, null, Response.EXCEED_MAX_TRANSACTION_SIZE);
        } catch (UTXOProviderException e) {
            logger.warn(String.format("UTXO provider exception sending %s", operationDescription), e);
            // Comment out panic logging for now
            // panicProcessor.panic("utxoprovider", "UTXO provider exception " + rskTxHash + " " + btcTx);
            return new BuildResult(null, null, Response.UTXO_PROVIDER_EXCEPTION);
        }
    }

    private final SendRequestConfigurator defaultSettingsConfigurator = (SendRequest sr) -> {
        sr.missingSigsMode = Wallet.MissingSigsMode.USE_OP_ZERO;
        sr.feePerKb = getFeePerKb();
        sr.shuffleOutputs = false;
        sr.recipientsPayFees = true;
    };

    protected enum Response {
        SUCCESS,
        INSUFFICIENT_MONEY,
        COULD_NOT_ADJUST_DOWNWARDS,
        EXCEED_MAX_TRANSACTION_SIZE,
        UTXO_PROVIDER_EXCEPTION
    }

}


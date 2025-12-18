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
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.wallet.RedeemData;
import co.rsk.bitcoinj.wallet.SendRequest;
import co.rsk.bitcoinj.wallet.Wallet;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.federation.FederationFormatVersion;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static co.rsk.peg.PegUtils.getFlyoverFederationAddress;
import static co.rsk.peg.bitcoin.BitcoinUtils.addSpendingFederationBaseScript;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Given a set of UTXOs, a ReleaseTransactionBuilder
 * knows how to build a release transaction
 * of a certain amount to a certain address,
 * and how to signal the used UTXOs so they
 * can be invalidated.
 *
 * @author Ariel Mendelzon
 */
public class ReleaseTransactionBuilder {

    public static final int BTC_TX_VERSION_1 = 1;
    public static final int BTC_TX_VERSION_2 = 2;

    public record BuildResult(BtcTransaction btcTx, List<UTXO> selectedUTXOs, Response responseCode) {}

    private interface SendRequestConfigurator {
        void configure(SendRequest sr);
    }

    private static final Logger logger = LoggerFactory.getLogger("ReleaseTransactionBuilder");

    private final NetworkParameters params;
    private final Wallet wallet;
    private final int federationFormatVersion;
    private final Address changeAddress;
    private final Coin feePerKb;
    private final ActivationConfig.ForBlock activations;

    /**
     * Creates a release transaction builder.
     *
     * @param params                        network params
     * @param wallet                        wallet to be used to build the release tx
     * @param federationFormatVersion       needed to correctly set the redeem input data (script sig for legacy fed, witness for segwit fed)
     * @param changeAddress                 address to send change to
     * @param feePerKb                      fee per kb
     * @param activations                   activations
     */
    public ReleaseTransactionBuilder(
        NetworkParameters params,
        Wallet wallet,
        int federationFormatVersion,
        Address changeAddress,
        Coin feePerKb,
        ActivationConfig.ForBlock activations
    ) {
        this.params = params;
        this.wallet = wallet;
        this.federationFormatVersion = federationFormatVersion;
        this.changeAddress = changeAddress;
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

    public BuildResult buildAmountTo(Address to, Coin amount) {
        return buildWithConfiguration((SendRequest sr) -> {
            sr.tx.addOutput(amount, to);
            sr.changeAddress = changeAddress;
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

    public BuildResult buildSvpFundTransaction(Federation proposedFederation, Keccak256 proposedFederationFlyoverPrefix, Coin svpFundTxOutputsValue) {
        return buildWithConfiguration((SendRequest sr) -> {
            sr.tx.addOutput(svpFundTxOutputsValue, proposedFederation.getAddress());
            sr.tx.addOutput(svpFundTxOutputsValue, getFlyoverFederationAddress(params, proposedFederationFlyoverPrefix, proposedFederation));
            sr.changeAddress = changeAddress;
            sr.recipientsPayFees = false;
        }, String.format("sending %s in svp fund transaction", svpFundTxOutputsValue));
    }

    public BuildResult buildMigrationTransaction(Coin migrationValue, Address destinationAddress) {
        return buildWithConfiguration((SendRequest sr) -> {
            if (!activations.isActive(ConsensusRule.RSKIP376)){
                sr.tx.setVersion(BTC_TX_VERSION_1);
            }
            sr.tx.addOutput(migrationValue, destinationAddress);
            sr.changeAddress = destinationAddress;
        }, String.format("sending %s in migration transaction", migrationValue));
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
        String operationDescription
    ) {
        // Build a tx and send request and configure it
        BtcTransaction btcTx = setDefaultTxConfig();
        SendRequest sr = setSrConfiguration(sendRequestConfigurator, btcTx);

        try {
            completeTx(sr);

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
                .toList();

            return new BuildResult(btcTx, selectedUTXOs, Response.SUCCESS);
        } catch (InsufficientMoneyException e) {
            logger.warn(String.format("Not enough BTC in the wallet to complete %s", operationDescription), e);
            return new BuildResult(null, null, Response.INSUFFICIENT_MONEY);
        } catch (Wallet.CouldNotAdjustDownwards e) {
            logger.warn(String.format("A user output could not be adjusted downwards to pay tx fees %s", operationDescription), e);
            return new BuildResult(null, null, Response.COULD_NOT_ADJUST_DOWNWARDS);
        } catch (Wallet.DustySendRequested e) {
            logger.warn(String.format("Tx contains a dust output %s", operationDescription), e);
            return new BuildResult(null, null, Response.DUSTY_SEND_REQUESTED);
        } catch (Wallet.ExceededMaxTransactionSize e) {
            logger.warn(String.format("Tx size too big %s", operationDescription), e);
            return new BuildResult(null, null, Response.EXCEED_MAX_TRANSACTION_SIZE);
        } catch (UTXOProviderException e) {
            logger.warn(String.format("UTXO provider exception sending %s", operationDescription), e);
            return new BuildResult(null, null, Response.UTXO_PROVIDER_EXCEPTION);
        }
    }

    private BtcTransaction setDefaultTxConfig() {
        // Build a tx and send request and configure it
        BtcTransaction btcTx = new BtcTransaction(params);
        if (activations.isActive(ConsensusRule.RSKIP201)) {
            btcTx.setVersion(BTC_TX_VERSION_2);
        }

        return btcTx;
    }

    private SendRequest setSrConfiguration(SendRequestConfigurator sendRequestConfigurator, BtcTransaction btcTx) {
        SendRequest sr = SendRequest.forTx(btcTx);
        // Default settings
        defaultSettingsConfigurator.configure(sr);
        // Specific settings
        sendRequestConfigurator.configure(sr);

        if (federationFormatVersion == FederationFormatVersion.P2SH_P2WSH_ERP_FEDERATION.getFormatVersion()) {
            sr.signInputs = false;
            sr.isSegwitCompatible = true;
        }

        return sr;
    }

    private void completeTx(SendRequest sr) throws InsufficientMoneyException {
        wallet.completeTx(sr);

        if (!sr.isSegwitCompatible) {
            return;
        }

        signSegwitTxInputs(sr.tx);
    }

    private void signSegwitTxInputs(BtcTransaction tx) {
        for (int i = 0; i < tx.getInputs().size(); i++) {
            TransactionInput txInput = tx.getInput(i);
            RedeemData redeemData = txInput.getConnectedRedeemData(wallet);
            checkNotNull(redeemData, "Transaction exists in wallet that we cannot redeem: %s", txInput.getOutpoint().getHash());
            Script redeemScript = redeemData.redeemScript;

            addSpendingFederationBaseScript(tx, i, redeemScript, federationFormatVersion);
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
        DUSTY_SEND_REQUESTED,
        EXCEED_MAX_TRANSACTION_SIZE,
        UTXO_PROVIDER_EXCEPTION
    }
}

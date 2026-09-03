package co.rsk.test.builders;

import co.rsk.bitcoinj.core.*;
import co.rsk.peg.bitcoin.BitcoinUtils;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.federation.FederationTestUtils;
import co.rsk.peg.federation.P2shP2wshErpFederationBuilder;
import java.util.ArrayList;
import java.util.List;

public class MigrationTransactionBuilder {
    private final List<BtcTransaction> prevTxs;
    private final List<Coin> outputValues;
    private NetworkParameters networkParameters;
    private Federation activeFederation;
    private Federation retiringFederation;
    private List<BtcECKey> signingKeys;
    private boolean signTransaction;

    private MigrationTransactionBuilder() {
        this.networkParameters = NetworkParameters.fromID(NetworkParameters.ID_MAINNET);
        this.activeFederation = P2shP2wshErpFederationBuilder.builder().build();
        this.retiringFederation = P2shP2wshErpFederationBuilder.builder().build();
        this.prevTxs = new ArrayList<>();
        this.outputValues = new ArrayList<>();
        this.signTransaction = false;
    }

    public static MigrationTransactionBuilder builder() {
        return new MigrationTransactionBuilder();
    }

    public MigrationTransactionBuilder withNetworkParameters(NetworkParameters networkParameters) {
        this.networkParameters = networkParameters;
        return this;
    }

    public MigrationTransactionBuilder withActiveFederation(Federation activeFederation) {
        this.activeFederation = activeFederation;
        return this;
    }

    public MigrationTransactionBuilder withRetiringFederation(Federation retiringFederation) {
        this.retiringFederation = retiringFederation;
        return this;
    }

    public MigrationTransactionBuilder withPrevTx(BtcTransaction prevTx) {
        this.prevTxs.add(prevTx);
        return this;
    }

    public MigrationTransactionBuilder withOutput(Coin value) {
        this.outputValues.add(value);
        return this;
    }

    public MigrationTransactionBuilder withSignatures(List<BtcECKey> signingKeys) {
        this.signTransaction = true;
        this.signingKeys = signingKeys;
        return this;
    }

    public BtcTransaction build() {
        BtcTransaction migrationTx = new BtcTransaction(networkParameters);

        addInputsToMigrationTx(migrationTx);
        addOutputsToTransaction(migrationTx);
        signInputs(migrationTx);

        return migrationTx;
    }

    private void signInputs(BtcTransaction migrationTx) {
        if (!signTransaction) {
            return;
        }

        FederationTestUtils.signInputs(retiringFederation, signingKeys, migrationTx);
    }

    private void addInputsToMigrationTx(BtcTransaction migrationTx) {
        if (prevTxs.isEmpty()) {
            BtcTransaction defaultPrevTx = new BtcTransaction(networkParameters);
            defaultPrevTx.addOutput(Coin.COIN, retiringFederation.getAddress());
            prevTxs.add(defaultPrevTx);
        }

        int inputIndex = 0;
        for (BtcTransaction prevTx : prevTxs) {
            for (TransactionOutput prevTxOutput : prevTx.getOutputs()) {
                migrationTx.addInput(prevTxOutput);

                BitcoinUtils.addSpendingFederationBaseScript(
                    migrationTx,
                    inputIndex,
                    retiringFederation.getRedeemScript(),
                    retiringFederation.getFormatVersion()
                );

                inputIndex++;
            }
        }
    }

    private void addOutputsToTransaction(BtcTransaction transaction) {
        if (outputValues.isEmpty()) {
            // no explicit outputs, so the whole migrated value goes in a single output
            Coin totalAmount = Coin.ZERO;
            for (BtcTransaction prevTx : prevTxs) {
                totalAmount = totalAmount.add(prevTx.getOutputSum());
            }
            addOutputToActiveFederation(transaction, totalAmount);
            return;
        }

        for (Coin outputValue : outputValues) {
            addOutputToActiveFederation(transaction, outputValue);
        }
    }

    private void addOutputToActiveFederation(BtcTransaction transaction, Coin value) {
        TransactionOutput output = new TransactionOutput(
            networkParameters,
            null,
            value,
            activeFederation.getAddress()
        );
        transaction.addOutput(output);
    }
}

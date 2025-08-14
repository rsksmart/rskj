package co.rsk.test.builders;

import co.rsk.bitcoinj.core.*;
import co.rsk.peg.bitcoin.BitcoinUtils;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.federation.P2shP2wshErpFederationBuilder;
import java.util.ArrayList;
import java.util.List;

public class MigrationTransactionBuilder {
    private final List<BtcTransaction> prevTxs;
    private NetworkParameters networkParameters;
    private Federation activeFederation;
    private Federation retiringFederation;

    private MigrationTransactionBuilder() {
        this.networkParameters = NetworkParameters.fromID(NetworkParameters.ID_MAINNET);
        this.activeFederation = P2shP2wshErpFederationBuilder.builder().build();
        this.retiringFederation = P2shP2wshErpFederationBuilder.builder().build();
        this.prevTxs = new ArrayList<>();
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

    public BtcTransaction build() {
        BtcTransaction migrationTx = new BtcTransaction(networkParameters);

        addInputsToMigrationTx(migrationTx);
        addOutputsToTransaction(migrationTx);

        return migrationTx;
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
            }
            BitcoinUtils.addSpendingFederationBaseScript(
                migrationTx,
                inputIndex,
                retiringFederation.getRedeemScript(),
                retiringFederation.getFormatVersion()
            );

            inputIndex++;
        }
    }

    private void addOutputsToTransaction(BtcTransaction transaction) {
        Coin totalAmount = Coin.ZERO;
        for (BtcTransaction prevTx : prevTxs) {
            totalAmount = totalAmount.add(prevTx.getOutputSum());
        }
        TransactionOutput output = new TransactionOutput(
            networkParameters,
            null,
            totalAmount,
            activeFederation.getAddress()
        );
        transaction.addOutput(output);
    }
}

package co.rsk.test.builders;

import co.rsk.bitcoinj.core.*;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.bitcoin.BitcoinUtils;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.federation.P2shP2wshErpFederationBuilder;
import java.util.ArrayList;
import java.util.List;

public class MigrationTransactionBuilder {
    private NetworkParameters networkParameters;
    private Federation activeFederation;
    private Federation retiringFederation;
    private final List<UTXO> utxos;

    private MigrationTransactionBuilder() {
        this.networkParameters = NetworkParameters.fromID(NetworkParameters.ID_MAINNET);
        this.activeFederation = P2shP2wshErpFederationBuilder.builder().build();
        this.retiringFederation = P2shP2wshErpFederationBuilder.builder().build();
        this.utxos = new ArrayList<>();
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

    public MigrationTransactionBuilder withUTXO(UTXO utxo) {
        this.utxos.add(utxo);
        return this;
    }

    public BtcTransaction build() {
        BtcTransaction migrationTx = new BtcTransaction(networkParameters);

        addInputsToTransaction(migrationTx);
        addOutputsToTransaction(migrationTx);

        return migrationTx;
    }

    private void addInputsToTransaction(BtcTransaction transaction) {
        if (utxos.isEmpty()) {
            UTXO defaultUtxo = new UTXO(
                BitcoinTestUtils.createHash(11),
                0,
                Coin.COIN,
                0,
                false,
                retiringFederation.getP2SHScript()
            );
            utxos.add(defaultUtxo);
        }

        int inputIndex = 0;
        for (UTXO utxo : utxos) {
            TransactionInput input = new TransactionInput(
                networkParameters,
                null,
                new byte[]{},
                new TransactionOutPoint(networkParameters, utxo.getIndex(), utxo.getHash())
            );

            transaction.addInput(input);
            BitcoinUtils.addSpendingFederationBaseScript(
                transaction,
                inputIndex,
                retiringFederation.getRedeemScript(),
                retiringFederation.getFormatVersion()
            );

            inputIndex++;
        }
    }

    private void addOutputsToTransaction(BtcTransaction transaction) {
        Coin totalAmount = utxos.stream().reduce(
            Coin.ZERO,
            (sum, utxo) -> sum.add(utxo.getValue()),
            Coin::add
        );
        TransactionOutput output = new TransactionOutput(
            networkParameters,
            null,
            totalAmount,
            activeFederation.getAddress()
        );
        transaction.addOutput(output);
    }
}

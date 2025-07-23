package co.rsk.test.builders;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.Script;
import co.rsk.peg.bitcoin.*;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.federation.P2shP2wshErpFederationBuilder;
import java.util.*;

public class PegoutTransactionBuilder {
    private NetworkParameters networkParameters;
    private List<BtcECKey> signingKeys;
    private Federation activeFederation;

    private final List<TransactionInput> inputs;
    private final List<TransactionOutput> outputs;

    private Coin changeAmount;
    private boolean signTransaction;

    private PegoutTransactionBuilder() {
        List<BtcECKey> defaultKeys = getDefaultFederationBtcKeys();
        this.activeFederation = P2shP2wshErpFederationBuilder.builder()
            .withMembersBtcPublicKeys(defaultKeys)
            .build();

        int signersRequired = activeFederation.getNumberOfSignaturesRequired();
        this.signingKeys = new ArrayList<>(defaultKeys.subList(0, signersRequired));

        this.networkParameters = NetworkParameters.fromID(NetworkParameters.ID_MAINNET);
        this.changeAmount = Coin.COIN.div(2);
        this.signTransaction = false;

        this.inputs = new ArrayList<>();
        this.outputs = new ArrayList<>();
    }

    public static PegoutTransactionBuilder builder() {
        return new PegoutTransactionBuilder();
    }

    public PegoutTransactionBuilder withNetworkParameters(NetworkParameters networkParameters) {
        this.networkParameters = networkParameters;
        return this;
    }

    public PegoutTransactionBuilder withActiveFederation(Federation activeFederation) {
        this.activeFederation = activeFederation;
        return this;
    }

    public PegoutTransactionBuilder withInput(Sha256Hash spendTxHash, int outputIndex) {
        TransactionInput input = new TransactionInput(
            networkParameters,
            null,
            new byte[]{},
            new TransactionOutPoint(networkParameters, outputIndex, spendTxHash)
        );

        inputs.add(input);
        return this;
    }

    public PegoutTransactionBuilder withOutput(Coin amount, Address address) {
        TransactionOutput output = new TransactionOutput(networkParameters, null, amount, address);
        outputs.add(output);
        return this;
    }

    public PegoutTransactionBuilder withChangeAmount(Coin changeAmount) {
        this.changeAmount = changeAmount;
        return this;
    }

    public PegoutTransactionBuilder withSignatures() {
        signTransaction = true;
        return this;
    }

    public PegoutTransactionBuilder withSignatures(List<BtcECKey> signingKeys) {
        signTransaction = true;
        this.signingKeys = signingKeys;
        return this;
    }

    public BtcTransaction build() {
        BtcTransaction pegoutTransaction = new BtcTransaction(networkParameters);

        addInputsToTransaction(pegoutTransaction);
        addOutputsToTransaction(pegoutTransaction);
        addChangeOutput(pegoutTransaction);

        return pegoutTransaction;
    }

    private void addInputsToTransaction(BtcTransaction transaction) {
        if (inputs.isEmpty()) {
            // Add a default input if no inputs are specified
            TransactionInput defaultInput = getDefaultInput();
            inputs.add(defaultInput);
        }

        int inputIndex = 0;
        for (TransactionInput input : inputs) {
            transaction.addInput(input);
            BitcoinUtils.addSpendingFederationBaseScript(
                transaction,
                inputIndex,
                activeFederation.getRedeemScript(),
                activeFederation.getFormatVersion()
            );

            if (signTransaction) {
                BitcoinTestUtils.signTransactionInputFromP2shMultiSig(transaction, inputIndex, signingKeys);
            }

            inputIndex++;
        }
    }

    private TransactionInput getDefaultInput() {
        Script activeFederationRedeemScript = activeFederation.getRedeemScript();
        int outputIndex = 0;
        Sha256Hash spendTxHash = BitcoinTestUtils.createHash(10);

        return new TransactionInput(
            networkParameters,
            null,
            activeFederationRedeemScript.getProgram(),
            new TransactionOutPoint(networkParameters, outputIndex, spendTxHash)
        );
    }

    private void addOutputsToTransaction(BtcTransaction transaction) {
        if (outputs.isEmpty()) {
            // Add default user output if no outputs are specified
            TransactionOutput defaultOutput = getDefaultUserOutput();
            outputs.add(defaultOutput);
        }

        for (TransactionOutput output : outputs) {
            transaction.addOutput(output);
        }
    }

    private TransactionOutput getDefaultUserOutput() {
        Address receiverAddress = BitcoinTestUtils.createP2PKHAddress(networkParameters, "receiver");
        Coin amount = Coin.COIN;

        return new TransactionOutput(networkParameters, null, amount, receiverAddress);
    }

    private void addChangeOutput(BtcTransaction transaction) {
        Address changeAddress = activeFederation.getAddress();
        TransactionOutput changeOutput = new TransactionOutput(networkParameters, null, changeAmount, changeAddress);
        transaction.addOutput(changeOutput);
    }

    private List<BtcECKey> getDefaultFederationBtcKeys() {
        for (int i=0; i<20; i++) {
            signingKeys.add(BitcoinTestUtils.getBtcEcKeyFromSeed("signer" + i));
        }
        return signingKeys;
    }
}

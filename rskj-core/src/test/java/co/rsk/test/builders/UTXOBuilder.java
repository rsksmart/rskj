package co.rsk.test.builders;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.peg.bitcoin.BitcoinTestUtils;

public class UTXOBuilder {
    private Sha256Hash transactionHash;
    private long transactionIndex;
    private Coin value;
    private int height;
    private boolean isCoinbase;
    private Script outputScript;

    private UTXOBuilder() {
        transactionHash = BitcoinTestUtils.createHash(0);
        transactionIndex = 0;
        value = Coin.COIN;
        height = 0;
        isCoinbase = false;
        NetworkParameters networkParameters = NetworkParameters.fromID(NetworkParameters.ID_MAINNET);
        Address owner = BitcoinTestUtils.createP2PKHAddress(networkParameters, "seed");
        outputScript = ScriptBuilder.createOutputScript(owner);
    }

    public static UTXOBuilder builder() {
        return new UTXOBuilder();
    }

    public UTXOBuilder withTransactionHash(Sha256Hash transactionHash) {
        this.transactionHash = transactionHash;
        return this;
    }

    public UTXOBuilder withTransactionIndex(long transactionIndex) {
        this.transactionIndex = transactionIndex;
        return this;
    }

    public UTXOBuilder withValue(Coin value) {
        this.value = value;
        return this;
    }

    public UTXOBuilder withHeight(int height) {
        this.height = height;
        return this;
    }

    public UTXOBuilder withIsCoinbase(boolean isCoinbase) {
        this.isCoinbase = isCoinbase;
        return this;
    }

    public UTXOBuilder withOutputScript(Script outputScript) {
        this.outputScript = outputScript;
        return this;
    }

    public UTXO build() {
        return new UTXO(
            this.transactionHash,
            this.transactionIndex,
            this.value,
            this.height,
            this.isCoinbase,
            this.outputScript
        );
    }
}

package co.rsk.test.builders;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.peg.bitcoin.BitcoinTestUtils;

public class UTXOBuilder {
    private Sha256Hash transactionHash;
    private long outputIndex;
    private Coin value;
    private int blockHeight;
    private boolean isCoinbase;
    private Script scriptPubKey;

    private UTXOBuilder() {
        transactionHash = BitcoinTestUtils.createHash(1);
        outputIndex = 0;
        value = Coin.COIN;
        blockHeight = 10;
        isCoinbase = false;
        scriptPubKey = getDefaultOutputScript();
    }

    private Script getDefaultOutputScript() {
        NetworkParameters networkParameters = NetworkParameters.fromID(NetworkParameters.ID_MAINNET);
        Address sender = BitcoinTestUtils.createP2PKHAddress(networkParameters, "seed");
        return ScriptBuilder.createOutputScript(sender);
    }

    public static UTXOBuilder builder() {
        return new UTXOBuilder();
    }

    public UTXOBuilder withTransactionHash(Sha256Hash transactionHash) {
        this.transactionHash = transactionHash;
        return this;
    }

    public UTXOBuilder withOutpointIndex(long outpointIndex) {
        this.outputIndex = outpointIndex;
        return this;
    }

    public UTXOBuilder withValue(Coin value) {
        this.value = value;
        return this;
    }

    public UTXOBuilder withBlockHeight(int blockHeight) {
        this.blockHeight = blockHeight;
        return this;
    }

    public UTXOBuilder isCoinbase(boolean isCoinbase) {
        this.isCoinbase = isCoinbase;
        return this;
    }

    public UTXOBuilder withScriptPubKey(Script scriptPubKey) {
        this.scriptPubKey = scriptPubKey;
        return this;
    }

    public UTXO build() {
        return new UTXO(
            this.transactionHash,
            this.outputIndex,
            this.value,
            this.blockHeight,
            this.isCoinbase,
            this.scriptPubKey
        );
    }
}

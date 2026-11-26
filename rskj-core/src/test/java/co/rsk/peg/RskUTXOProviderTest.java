package co.rsk.peg;

import static org.junit.jupiter.api.Assertions.*;

import co.rsk.bitcoinj.core.*;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.test.builders.UTXOBuilder;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class RskUTXOProviderTest {
    private static final NetworkParameters btcMainnetParams = BridgeMainNetConstants.getInstance().getBtcParams();
    private static final int BTC_CHAIN_HEAD_HEIGHT = 870_000;

    private final List<UTXO> utxos = UTXOBuilder.builder().buildManyFromSameTx(2);

    @Test
    void constructor_withNegativeBtcChainHeadHeight_throwsIllegalArgumentException() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new RskUTXOProvider(btcMainnetParams, utxos, -1)
        );
        assertEquals("Invalid BTC chain head height: -1", exception.getMessage());
    }

    @Test
    void getOpenTransactionOutputs_returnsGivenUtxos() throws UTXOProviderException {
        RskUTXOProvider utxoProvider = new RskUTXOProvider(btcMainnetParams, utxos, BTC_CHAIN_HEAD_HEIGHT);

        assertEquals(utxos, utxoProvider.getOpenTransactionOutputs(Collections.emptyList()));
    }

    @Test
    void getChainHeadHeight_returnsGivenHeight() {
        RskUTXOProvider utxoProvider = new RskUTXOProvider(btcMainnetParams, utxos, BTC_CHAIN_HEAD_HEIGHT);

        assertEquals(BTC_CHAIN_HEAD_HEIGHT, utxoProvider.getChainHeadHeight());
    }

    @Test
    void getChainHeadHeight_withZeroHeight_returnsZero() {
        RskUTXOProvider utxoProvider = new RskUTXOProvider(btcMainnetParams, utxos, 0);

        assertEquals(0, utxoProvider.getChainHeadHeight());
    }

    @Test
    void getChainHeadHeight_beforeCardamom_returnsMaxValue() {
        RskUTXOProvider utxoProvider = new RskUTXOProvider(
            btcMainnetParams,
            utxos,
            RskUTXOProvider.BTC_CHAIN_HEAD_HEIGHT_BEFORE_CARDAMOM
        );

        assertEquals(Integer.MAX_VALUE, utxoProvider.getChainHeadHeight());
    }

    @Test
    void getParams_returnsGivenParams() {
        RskUTXOProvider utxoProvider = new RskUTXOProvider(btcMainnetParams, utxos, BTC_CHAIN_HEAD_HEIGHT);

        assertEquals(btcMainnetParams, utxoProvider.getParams());
    }
}

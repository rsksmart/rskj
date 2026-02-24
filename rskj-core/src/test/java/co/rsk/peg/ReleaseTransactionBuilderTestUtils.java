package co.rsk.peg;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.TransactionInput;
import co.rsk.bitcoinj.core.UTXO;

import java.util.List;

import static co.rsk.peg.ReleaseTransactionBuilder.BTC_TX_VERSION_1;
import static co.rsk.peg.ReleaseTransactionBuilder.BTC_TX_VERSION_2;
import static co.rsk.peg.ReleaseTransactionBuilder.Response;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReleaseTransactionBuilderTestUtils {
    public static void assertBuildResultResponseCode(Response expectedResponseCode, ReleaseTransactionBuilder.BuildResult releaseTransactionBuilderResult) {
        ReleaseTransactionBuilder.Response actualResponseCode = releaseTransactionBuilderResult.responseCode();
        assertEquals(expectedResponseCode, actualResponseCode);
    }

    public static void assertBtcTxVersionIs1(BtcTransaction releaseTransaction) {
        assertEquals(BTC_TX_VERSION_1, releaseTransaction.getVersion());
    }

    public static void assertBtcTxVersionIs2(BtcTransaction releaseTransaction) {
        assertEquals(BTC_TX_VERSION_2, releaseTransaction.getVersion());
    }

    public static void assertSelectedUtxosBelongToTheInputs(List<UTXO> utxos, List<TransactionInput> inputs) {
        for (UTXO utxo : utxos) {
            List<TransactionInput> matchingInputs = inputs.stream().
                filter(input -> input.getOutpoint().getHash().equals(utxo.getHash())
                    && input.getOutpoint().getIndex() == utxo.getIndex()).toList();
            assertEquals(1, matchingInputs.size());
        }
    }
}

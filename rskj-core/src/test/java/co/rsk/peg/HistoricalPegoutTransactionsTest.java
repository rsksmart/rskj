package co.rsk.peg;

import co.rsk.bitcoinj.core.NetworkParameters;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class HistoricalPegoutTransactionsTest {

    @Test
    void testGet_existingTestnetTransaction_returnsPegoutTx() {
        String rskTxHash = "2d1b35c663d6c0c02380aba68e656cdc61cb7d412c31b19d330b637b4957a64c";
        String expectedPegoutTx = "a9bdc4e4a48a3e3754b2722b3e61eeca9ae4009379a62a8313acf485c79171c1";

        Optional<String> result = HistoricalPegoutTransactions.get(rskTxHash, NetworkParameters.ID_TESTNET);

        assertTrue(result.isPresent());
        assertEquals(expectedPegoutTx, result.get());
    }

    @Test
    void testGet_existingMainnetTransaction_returnsPegoutTx() {
        String rskTxHash = "8472c6d227fe867f04859ad819a0585b7c2dc953896c37d9fd04d4019941ac18";
        String expectedPegoutTx = "49796a89abfd770308cf4f4a8c49e3f97ac2f0edb33bdba98434603c82135802";

        Optional<String> result = HistoricalPegoutTransactions.get(rskTxHash, NetworkParameters.ID_MAINNET);

        assertTrue(result.isPresent());
        assertEquals(expectedPegoutTx, result.get());
    }

    @Test
    void testGet_unknownTransaction_returnsEmptyOptional() {
        Optional<String> result = HistoricalPegoutTransactions.get("nonexistent-hash", NetworkParameters.ID_TESTNET);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGet_unsupportedNetwork_throwsException() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> HistoricalPegoutTransactions.get(
                        "any-hash",
                        "unknown-network"
                )
        );
        assertTrue(exception.getMessage().contains("Historical pegout transactions are not defined"));
        }
}

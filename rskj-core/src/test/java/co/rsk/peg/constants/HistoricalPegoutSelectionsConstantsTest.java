package co.rsk.peg.constants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.crypto.Keccak256;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.stream.Collectors;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.HashUtil;
import org.junit.jupiter.api.Test;

class HistoricalPegoutSelectionsConstantsTest {

    // Representative entries (first and last of each dataset) used as fixtures.
    private static final Keccak256 MAINNET_FIRST_RSK_TX =
        new Keccak256("8472c6d227fe867f04859ad819a0585b7c2dc953896c37d9fd04d4019941ac18");
    private static final Sha256Hash MAINNET_FIRST_BTC_TX =
        Sha256Hash.wrap("49796a89abfd770308cf4f4a8c49e3f97ac2f0edb33bdba98434603c82135802");
    private static final Keccak256 MAINNET_LAST_RSK_TX =
        new Keccak256("67ccabfd373e9d92b8bc9e86bb0f38856d3de2ed62491ab6d1a4ce6bf6c3a4b5");
    private static final Sha256Hash MAINNET_LAST_BTC_TX =
        Sha256Hash.wrap("d2ca62b50287a300122672a9b05e08422ec36e41d0424c2ba7612bf1ca96d607");
    private static final Keccak256 TESTNET_FIRST_RSK_TX =
        new Keccak256("2d1b35c663d6c0c02380aba68e656cdc61cb7d412c31b19d330b637b4957a64c");
    private static final Sha256Hash TESTNET_FIRST_BTC_TX =
        Sha256Hash.wrap("a9bdc4e4a48a3e3754b2722b3e61eeca9ae4009379a62a8313acf485c79171c1");
    private static final Keccak256 TESTNET_LAST_RSK_TX =
        new Keccak256("d3e94aac06e45556359d3d42a7d8eeca3e6fa89972bc921245f9d1892b1da9eb");
    private static final Sha256Hash TESTNET_LAST_BTC_TX =
        Sha256Hash.wrap("5bd422c96cabc0c4adecc4d7a2a23dd7c18ace92fb86348910e596449570a45f");
    private static final Keccak256 UNKNOWN_RSK_TX =
        new Keccak256("0000000000000000000000000000000000000000000000000000000000000000");

    @Test
    void mainnet_knownKeys_returnRecordedSelection() {
        HistoricalPegoutSelectionsConstants mainnet = HistoricalPegoutSelectionsMainNetConstants.getInstance();

        assertEquals(Optional.of(MAINNET_FIRST_BTC_TX), mainnet.getSelectedPegoutBtcTxHash(MAINNET_FIRST_RSK_TX));
        assertEquals(Optional.of(MAINNET_LAST_BTC_TX), mainnet.getSelectedPegoutBtcTxHash(MAINNET_LAST_RSK_TX));
    }

    @Test
    void testnet_knownKeys_returnRecordedSelection() {
        HistoricalPegoutSelectionsConstants testnet = HistoricalPegoutSelectionsTestNetConstants.getInstance();

        assertEquals(Optional.of(TESTNET_FIRST_BTC_TX), testnet.getSelectedPegoutBtcTxHash(TESTNET_FIRST_RSK_TX));
        assertEquals(Optional.of(TESTNET_LAST_BTC_TX), testnet.getSelectedPegoutBtcTxHash(TESTNET_LAST_RSK_TX));
    }

    @Test
    void mainnetKeyOnTestnet_returnsEmpty() {
        // A key that exists on mainnet must not resolve against the testnet dataset.
        assertEquals(Optional.empty(),
            HistoricalPegoutSelectionsTestNetConstants.getInstance().getSelectedPegoutBtcTxHash(MAINNET_FIRST_RSK_TX));
    }

    @Test
    void unknownKey_returnsEmpty() {
        assertEquals(Optional.empty(),
            HistoricalPegoutSelectionsMainNetConstants.getInstance().getSelectedPegoutBtcTxHash(UNKNOWN_RSK_TX));
    }

    @Test
    void regtest_recordsNoSelections() {
        HistoricalPegoutSelectionsConstants regtest = HistoricalPegoutSelectionsRegTestConstants.getInstance();

        assertTrue(regtest.getSelections().isEmpty());
        assertEquals(Optional.empty(), regtest.getSelectedPegoutBtcTxHash(MAINNET_FIRST_RSK_TX));
    }

    @Test
    void datasets_haveExpectedSizes() {
        // Guards the consensus-critical datasets against accidental additions/removals.
        assertEquals(17, HistoricalPegoutSelectionsMainNetConstants.getInstance().getSelections().size());
        assertEquals(53, HistoricalPegoutSelectionsTestNetConstants.getInstance().getSelections().size());
    }

    @Test
    void mainnetDataset_matchesPinnedDigest() {
        assertEquals("504f1aeb5364bd0bb99609868d2437a1cf40be4508483ff9255d8f37fd7c5544",
            digestOf(HistoricalPegoutSelectionsMainNetConstants.getInstance()));
    }

    @Test
    void testnetDataset_matchesPinnedDigest() {
        assertEquals("1121533b823aeb59483536040683d6b189d32278ec9d45ca5673cda3e1935e1c",
            digestOf(HistoricalPegoutSelectionsTestNetConstants.getInstance()));
    }

    @Test
    void selections_areImmutable() {
        assertThrows(UnsupportedOperationException.class,
            () -> HistoricalPegoutSelectionsMainNetConstants.getInstance().getSelections()
                .put(UNKNOWN_RSK_TX, MAINNET_FIRST_BTC_TX));
    }

    /**
     * Digest over every pair in the dataset, so that changing a single character of any hash, or swapping
     * a key with its value, fails the build. The size test only catches additions and removals, and both
     * sides are 32 bytes, so neither mistake is otherwise detectable. Sorting is required: the iteration
     * order of a {@code Map.ofEntries} map is unspecified.
     *
     * <p>A failure here means the consensus dataset changed. Re-pin only after confirming the change is
     * intended and independently validated.</p>
     */
    private static String digestOf(HistoricalPegoutSelectionsConstants constants) {
        String canonical = constants.getSelections().entrySet().stream()
            .map(entry -> entry.getKey() + ":" + entry.getValue())
            .sorted()
            .collect(Collectors.joining("\n"));

        return Hex.toHexString(HashUtil.sha256(canonical.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void bridgeConstants_exposeTheirNetworkDataset() {
        assertSame(HistoricalPegoutSelectionsMainNetConstants.getInstance(),
            BridgeMainNetConstants.getInstance().getHistoricalPegoutSelectionsConstants());
        assertSame(HistoricalPegoutSelectionsTestNetConstants.getInstance(),
            BridgeTestNetConstants.getInstance().getHistoricalPegoutSelectionsConstants());
        assertSame(HistoricalPegoutSelectionsRegTestConstants.getInstance(),
            new BridgeRegTestConstants().getHistoricalPegoutSelectionsConstants());
    }
}

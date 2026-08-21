/*
 * This file is part of RskJ
 * Copyright (C) 2024 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package co.rsk.peg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.constants.BridgeRegTestConstants;
import co.rsk.peg.constants.BridgeTestNetConstants;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class HistoricalPegoutSelectionsTest {

    private static final BridgeConstants MAINNET = BridgeMainNetConstants.getInstance();
    private static final BridgeConstants TESTNET = BridgeTestNetConstants.getInstance();
    private static final BridgeConstants REGTEST = new BridgeRegTestConstants();

    // Representative entries (first and last put() of each dataset) used as fixtures.
    private static final String MAINNET_FIRST_RSK_TX = "8472c6d227fe867f04859ad819a0585b7c2dc953896c37d9fd04d4019941ac18";
    private static final String MAINNET_FIRST_BTC_TX = "49796a89abfd770308cf4f4a8c49e3f97ac2f0edb33bdba98434603c82135802";
    private static final String MAINNET_LAST_RSK_TX = "67ccabfd373e9d92b8bc9e86bb0f38856d3de2ed62491ab6d1a4ce6bf6c3a4b5";
    private static final String MAINNET_LAST_BTC_TX = "d2ca62b50287a300122672a9b05e08422ec36e41d0424c2ba7612bf1ca96d607";
    private static final String TESTNET_FIRST_RSK_TX = "2d1b35c663d6c0c02380aba68e656cdc61cb7d412c31b19d330b637b4957a64c";
    private static final String TESTNET_FIRST_BTC_TX = "a9bdc4e4a48a3e3754b2722b3e61eeca9ae4009379a62a8313acf485c79171c1";
    private static final String TESTNET_LAST_RSK_TX = "d3e94aac06e45556359d3d42a7d8eeca3e6fa89972bc921245f9d1892b1da9eb";
    private static final String TESTNET_LAST_BTC_TX = "5bd422c96cabc0c4adecc4d7a2a23dd7c18ace92fb86348910e596449570a45f";
    private static final String UNKNOWN_RSK_TX = "0000000000000000000000000000000000000000000000000000000000000000";

    @Test
    void hasHistoricalData_mainnet_isTrue() {
        assertTrue(HistoricalPegoutSelections.hasHistoricalData(MAINNET));
    }

    @Test
    void hasHistoricalData_testnet_isTrue() {
        assertTrue(HistoricalPegoutSelections.hasHistoricalData(TESTNET));
    }

    @Test
    void hasHistoricalData_regtest_isFalse() {
        assertFalse(HistoricalPegoutSelections.hasHistoricalData(REGTEST));
    }

    @Test
    void getSelectedBtcTxHash_knownMainnetKeys_returnRecordedSelection() {
        assertEquals(Optional.of(MAINNET_FIRST_BTC_TX),
            HistoricalPegoutSelections.getSelectedBtcTxHash(MAINNET_FIRST_RSK_TX, MAINNET));
        assertEquals(Optional.of(MAINNET_LAST_BTC_TX),
            HistoricalPegoutSelections.getSelectedBtcTxHash(MAINNET_LAST_RSK_TX, MAINNET));
    }

    @Test
    void getSelectedBtcTxHash_knownTestnetKeys_returnRecordedSelection() {
        assertEquals(Optional.of(TESTNET_FIRST_BTC_TX),
            HistoricalPegoutSelections.getSelectedBtcTxHash(TESTNET_FIRST_RSK_TX, TESTNET));
        assertEquals(Optional.of(TESTNET_LAST_BTC_TX),
            HistoricalPegoutSelections.getSelectedBtcTxHash(TESTNET_LAST_RSK_TX, TESTNET));
    }

    @Test
    void getSelectedBtcTxHash_mainnetKeyOnTestnet_returnsEmpty() {
        // A key that exists on mainnet must not resolve against the testnet dataset.
        assertEquals(Optional.empty(),
            HistoricalPegoutSelections.getSelectedBtcTxHash(MAINNET_FIRST_RSK_TX, TESTNET));
    }

    @Test
    void getSelectedBtcTxHash_unknownKey_returnsEmpty() {
        assertEquals(Optional.empty(),
            HistoricalPegoutSelections.getSelectedBtcTxHash(UNKNOWN_RSK_TX, MAINNET));
    }

    @Test
    void getSelectedBtcTxHash_normalizesZeroXPrefix() {
        assertEquals(Optional.of(MAINNET_FIRST_BTC_TX),
            HistoricalPegoutSelections.getSelectedBtcTxHash("0x" + MAINNET_FIRST_RSK_TX, MAINNET));
    }

    @Test
    void getSelectedBtcTxHash_normalizesUpperCase() {
        assertEquals(Optional.of(MAINNET_FIRST_BTC_TX),
            HistoricalPegoutSelections.getSelectedBtcTxHash(MAINNET_FIRST_RSK_TX.toUpperCase(), MAINNET));
        assertEquals(Optional.of(MAINNET_FIRST_BTC_TX),
            HistoricalPegoutSelections.getSelectedBtcTxHash(("0X" + MAINNET_FIRST_RSK_TX).toUpperCase(), MAINNET));
    }

    @Test
    void getSelectedBtcTxHash_unsupportedNetwork_throws() {
        // regtest has no historical dataset; a direct lookup must fail loudly rather than silently miss.
        assertThrows(IllegalStateException.class,
            () -> HistoricalPegoutSelections.getSelectedBtcTxHash(UNKNOWN_RSK_TX, REGTEST));
    }

    @Test
    void getSelectedBtcTxHash_nullRskTxHash_throws() {
        assertThrows(NullPointerException.class,
            () -> HistoricalPegoutSelections.getSelectedBtcTxHash(null, MAINNET));
    }

    @Test
    void getSelectedBtcTxHash_nullBridgeConstants_throws() {
        assertThrows(NullPointerException.class,
            () -> HistoricalPegoutSelections.getSelectedBtcTxHash(MAINNET_FIRST_RSK_TX, null));
    }

    @Test
    void hasHistoricalData_nullBridgeConstants_throws() {
        assertThrows(NullPointerException.class, () -> HistoricalPegoutSelections.hasHistoricalData(null));
    }

    @Test
    void datasets_haveExpectedSizes() throws Exception {
        // Guards the consensus-critical datasets against accidental additions/removals.
        assertEquals(17, datasetSize("MAINNET_SELECTIONS"));
        assertEquals(53, datasetSize("TESTNET_SELECTIONS"));
    }

    @Test
    void datasets_keysAndValuesAre64CharLowercaseHex() throws Exception {
        assertWellFormed("MAINNET_SELECTIONS");
        assertWellFormed("TESTNET_SELECTIONS");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> dataset(String fieldName) throws Exception {
        Field field = HistoricalPegoutSelections.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (Map<String, String>) field.get(null);
    }

    private static int datasetSize(String fieldName) throws Exception {
        return dataset(fieldName).size();
    }

    private static void assertWellFormed(String fieldName) throws Exception {
        for (Map.Entry<String, String> e : dataset(fieldName).entrySet()) {
            assertTrue(e.getKey().matches("[0-9a-f]{64}"), "malformed key: " + e.getKey());
            assertTrue(e.getValue().matches("[0-9a-f]{64}"), "malformed value: " + e.getValue());
        }
    }
}

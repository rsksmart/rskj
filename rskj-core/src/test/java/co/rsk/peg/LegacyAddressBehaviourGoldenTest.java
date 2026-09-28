package co.rsk.peg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.rsk.bitcoinj.core.LegacyAddress;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.crypto.Keccak256;
import java.util.Collections;
import java.util.List;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;

/**
 * Pins the byte-level behaviour of every legacy path that goes through {@link LegacyAddress}.
 *
 * <p>This exists because replacing {@code LegacyAddress} with the upstream
 * {@code LegacyAddress}/{@code LegacyAddress}/{@code SegwitAddress} hierarchy is <b>not</b> gated by any
 * consensus rule: it takes effect the moment the jar ships, for every block, including replaying
 * history from genesis. So the refactor has to be byte-for-byte behaviour preserving, and these
 * assertions are what says so.</p>
 *
 * <p>The expected values are derived from the RLP and base58check specifications rather than
 * captured from a run, so the test checks the format rather than checking the code against
 * itself.</p>
 */
class LegacyAddressBehaviourGoldenTest {

    private static final NetworkParameters MAINNET =
        NetworkParameters.fromID(NetworkParameters.ID_MAINNET);

    private static final byte[] PUB_KEY_HASH = Hex.decode("f7ee9ab7297134a0ccc76f3d50e94def17488f2c");
    private static final byte[] SCRIPT_HASH  = Hex.decode("be56929d90f9eec61155469953f2e2e7ef400c6e");
    private static final Keccak256 RSK_TX_HASH = new Keccak256(Hex.decode("11".repeat(32)));
    private static final Coin HALF_BTC = Coin.valueOf(50_000_000L);

    // --- address rendering -------------------------------------------------

    @Test
    void p2pkhAddress_rendersTheSameBase58() {
        assertEquals("1PbwjuQP3y9F3ZnbbWUvue4zpgkQuSbgD5", new LegacyAddress(MAINNET, PUB_KEY_HASH).toBase58());
    }

    @Test
    void p2shAddress_rendersTheSameBase58() {
        assertEquals("3K3S2AmwUVYHSKaMmtzsxmfmMts1s9RsXe",
            LegacyAddress.fromP2SHHash(MAINNET, SCRIPT_HASH).toBase58());
    }

    @Test
    void toStringMatchesToBase58() {
        // The peg-out event and the whitelist both rely on this
        LegacyAddress address = new LegacyAddress(MAINNET, PUB_KEY_HASH);
        assertEquals(address.toBase58(), address.toString());
    }

    // --- output scripts ----------------------------------------------------

    @Test
    void p2pkhOutputScript_isUnchanged() {
        Script script = ScriptBuilder.createOutputScript(new LegacyAddress(MAINNET, PUB_KEY_HASH));
        assertEquals("76a914f7ee9ab7297134a0ccc76f3d50e94def17488f2c88ac",
            Hex.toHexString(script.getProgram()));
    }

    @Test
    void p2shOutputScript_isUnchanged() {
        Script script = ScriptBuilder.createOutputScript(LegacyAddress.fromP2SHHash(MAINNET, SCRIPT_HASH));
        assertEquals("a914be56929d90f9eec61155469953f2e2e7ef400c6e87",
            Hex.toHexString(script.getProgram()));
    }

    // --- peg-out queue serialization ---------------------------------------

    private static ReleaseRequestQueue queueWith(Keccak256 rskTxHash) {
        LegacyAddress destination = new LegacyAddress(MAINNET, PUB_KEY_HASH);
        List<ReleaseRequestQueue.Entry> entries =
            Collections.singletonList(new ReleaseRequestQueue.Entry(destination, HALF_BTC, rskTxHash));

        return new ReleaseRequestQueue(entries);
    }

    @Test
    void releaseRequestQueue_withoutTxHash_isUnchanged() {
        byte[] serialized = BridgeSerializationUtils.serializeReleaseRequestQueue(queueWith(null));

        assertEquals("da94f7ee9ab7297134a0ccc76f3d50e94def17488f2c8402faf080",
            Hex.toHexString(serialized));
    }

    @Test
    void releaseRequestQueue_withTxHash_isUnchanged() {
        byte[] serialized =
            BridgeSerializationUtils.serializeReleaseRequestQueueWithTxHash(queueWith(RSK_TX_HASH));

        assertEquals(
            "f83b94f7ee9ab7297134a0ccc76f3d50e94def17488f2c8402faf080"
                + "a01111111111111111111111111111111111111111111111111111111111111111",
            Hex.toHexString(serialized));
    }

    @Test
    void releaseRequestQueue_roundTripsToTheSameAddress() {
        byte[] serialized =
            BridgeSerializationUtils.serializeReleaseRequestQueueWithTxHash(queueWith(RSK_TX_HASH));

        List<ReleaseRequestQueue.Entry> read =
            BridgeSerializationUtils.deserializeReleaseRequestQueue(serialized, MAINNET, true);

        assertEquals(1, read.size());
        // The stored form carries no version byte; the P2PKH header is stamped on read
        assertEquals("1PbwjuQP3y9F3ZnbbWUvue4zpgkQuSbgD5", read.get(0).getDestination().toString());
        assertEquals(HALF_BTC, read.get(0).getAmount());
        assertEquals(RSK_TX_HASH, read.get(0).getRskTxHash());
    }

    // --- equality ----------------------------------------------------------

    @Test
    void addressEquality_isByVersionAndBytes() {
        // The federation recognises its own UTXOs by address equality. If the refactor changes
        // these semantics the federation stops seeing its funds, which no peg-out test would catch.
        LegacyAddress one = new LegacyAddress(MAINNET, PUB_KEY_HASH);
        LegacyAddress other = new LegacyAddress(MAINNET, PUB_KEY_HASH);

        assertEquals(one, other);
        assertEquals(one.hashCode(), other.hashCode());
        assertTrue(LegacyAddress.fromP2SHHash(MAINNET, PUB_KEY_HASH).isP2SHAddress());
        // same 20 bytes, different version byte, must not be equal
        assertNotEquals(one, LegacyAddress.fromP2SHHash(MAINNET, PUB_KEY_HASH));
    }
}

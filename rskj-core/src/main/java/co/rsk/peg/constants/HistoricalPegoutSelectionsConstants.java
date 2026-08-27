package co.rsk.peg.constants;

import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.crypto.Keccak256;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Historical record of which pegout {@code getNextPegoutWithEnoughConfirmations} selected when more than
 * one entry was eligible at the same call, keyed by the confirming {@code updateCollections} rsk tx hash.
 *
 * <p>Before RSKIP559, the next pegout to confirm was picked with {@code stream().findFirst()} over a
 * {@link java.util.HashSet}, whose iteration order is an implementation accident that changed between
 * Java 17 and Java 21. This lookup lets a node reproduce the exact historic selection on any JVM, without
 * depending on HashSet iteration order. After RSKIP559 activates, selection is deterministic via
 * {@code Entry.BTC_TX_COMPARATOR} sorting and this table is no longer consulted.</p>
 *
 * <p>Only calls with more than one eligible entry are recorded — with zero or one eligible entry the
 * selection is already deterministic. Networks without a pre-RSKIP559 chain to reproduce leave the table
 * empty, which simply makes every lookup miss.</p>
 *
 * <p>Subclasses build their table with {@link java.util.Map#ofEntries}, which rejects a duplicated key at
 * construction. Filling a mutable map with {@code put} would silently overwrite one instead.</p>
 */
public class HistoricalPegoutSelectionsConstants {

    protected Map<Keccak256, Sha256Hash> selections = Collections.emptyMap();

    /**
     * The btc tx hash of the pegout historically selected by the {@code updateCollections} identified by
     * {@code rskTxHash}, or empty if that call is not recorded (i.e. it did not have more than one
     * eligible entry, or this network has no historical data).
     */
    public Optional<Sha256Hash> getSelectedPegoutBtcTxHash(Keccak256 rskTxHash) {
        return Optional.ofNullable(selections.get(rskTxHash));
    }
}

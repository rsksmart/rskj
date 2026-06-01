package org.ethereum.config.blockchain.upgrades;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.NonNull;
import com.google.common.annotations.VisibleForTesting;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;

import co.rsk.bitcoinj.core.Sha256Hash;

/**
 * Fixing some historical outputs for pegouts ordering functionality
 * to make full node sync compatible with new algorithm.
 *
 * RSKIP559 changed behaviour of
 * {@link co.rsk.peg.PegoutsWaitingForConfirmations}.
 * It is not fully compatible while syncing historical data.
 * To mitigate this we override some RSKIP559 algorithm outputs
 * with values provided in the current class.
 *
 * Such approach would allow us to have only one deterministic logic branch
 * with some corner cases overwrites for historical data.
 */
public class PegoutsOverwrites {

    public static record PegoutRef(Sha256Hash btcTxHash, long rskBlock) {
        private static final String FORMAT_ERROR = String.format(
                "'blockchain.config.%s' has wrong format. Expected SHA256HEX@LONG.",
                ActivationConfig.PEGOUTS_OVERWRITES_RULES);

        public static PegoutRef parse(String value) {
            if (value == null || value.isEmpty()) {
                throw new IllegalArgumentException(FORMAT_ERROR);
            }

            String[] parts = value.split("@", -1);
            if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
                throw new IllegalArgumentException(FORMAT_ERROR);
            }

            Sha256Hash btcTxHash;
            try {
                btcTxHash = Sha256Hash.wrap(parts[0]);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(String.format(
                        "'blockchain.config.%s' TX hash '%s' is invalid: %s",
                        ActivationConfig.PEGOUTS_OVERWRITES_RULES,
                        parts[0],
                        ex.getMessage()), ex);
            }

            long rskBlock;
            try {
                rskBlock = Long.parseLong(parts[1]);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(String.format(
                        "'blockchain.config.%s' block number '%s' is invalid: %s",
                        ActivationConfig.PEGOUTS_OVERWRITES_RULES,
                        parts[1],
                        ex.getMessage()), ex);
            }

            return new PegoutRef(btcTxHash, rskBlock);
        }

        @Override
        public String toString() {
            return String.format("%s@%d", btcTxHash, rskBlock);
        }
    }

    /**
     * Block number to next pegout waiting for confirmation.
     */
    private HashMap<Long, PegoutRef> pegouts = new HashMap<>();

    @VisibleForTesting
    public PegoutsOverwrites() {
    }

    @VisibleForTesting
    public PegoutsOverwrites(HashMap<Long, PegoutRef> overwrites) {
        this.pegouts = overwrites;
    }

    public PegoutsOverwrites(Config config) {

        for (Map.Entry<String, ConfigValue> e : config.entrySet()) {
            long key;
            try {
                key = Long.parseLong(e.getKey());
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(String.format(
                        "'blockchain.config.%s' keys must be numbers! %s",
                        ActivationConfig.PEGOUTS_OVERWRITES_RULES,
                        ex.getMessage()));
            }

            if (this.pegouts.containsKey(key)) {
                throw new IllegalArgumentException(String.format(
                        "'blockchain.config.%s' all keys must be unique. Duplicate found for %s.",
                        ActivationConfig.PEGOUTS_OVERWRITES_RULES,
                        key));
            }

            String stringRef;
            if (e.getValue().unwrapped() instanceof String v) {
                stringRef = v;
            } else {
                throw new IllegalArgumentException(String.format(
                        "'blockchain.config.%s' values must be a \"SHA256HEX@LONG\" string, not %s!",
                        ActivationConfig.PEGOUTS_OVERWRITES_RULES,
                        e.getValue().valueType()));
            }

            PegoutRef value = PegoutRef.parse(stringRef);
            this.pegouts.put(key, value);
        }
    }

    @NonNull
    public Optional<PegoutRef> getPegoutRef(Long currentBlockNumber) {
        return Optional.ofNullable(this.pegouts.get(currentBlockNumber));
    }
}

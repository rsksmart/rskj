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
 * RSKIP559 changed behaviour of {@link co.rsk.peg.PegoutsWaitingForConfirmations}.
 * It is not fully compatible while syncing historical data.
 * To mitigate this we override some RSKIP559 algorithm outputs
 * with values provided in the current class.
 *
 * Such approach would allow us to have only one deterministic logic branch
 * with some corner cases overwrites for historical data.
 */
public class PegoutsOverwrites {

    /**
     * Block number to next pegout waiting for confirmation.
     */
    private HashMap<Long, Sha256Hash> pegouts = new HashMap<>();

    @VisibleForTesting
    public PegoutsOverwrites() {
    }

    @VisibleForTesting
    public PegoutsOverwrites(HashMap<Long, Sha256Hash> overwrites) {
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
                    ex.getMessage()
                ));
            }

            if (this.pegouts.containsKey(key)) {
                throw new IllegalArgumentException(String.format(
                    "'blockchain.config.%s' all keys must be unique. Duplicate found for %s.",
                    ActivationConfig.PEGOUTS_OVERWRITES_RULES,
                    key
                ));
            }

            String valueHex;
            if (e.getValue().unwrapped() instanceof String v) {
                valueHex = v;
            } else {
                throw new IllegalArgumentException(String.format(
                    "'blockchain.config.%s' values must be valid HEX strings, not %s!",
                    ActivationConfig.PEGOUTS_OVERWRITES_RULES,
                    e.getValue().valueType()
                ));
            }

            Sha256Hash value;
            try {
                value = Sha256Hash.wrap(valueHex);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(String.format(
                    "'blockchain.config.%s' values must be a valid Sha256 HEX representation. %s",
                    ActivationConfig.PEGOUTS_OVERWRITES_RULES,
                    ex
                ));
            }

            this.pegouts.put(key, value);
        }
    }

    @NonNull
    public Optional<Sha256Hash> getPegoutHash(Long currentBlockNumber) {
        return Optional.ofNullable(this.pegouts.get(currentBlockNumber));
    }
}

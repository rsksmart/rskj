package co.rsk.peg.storage;

import org.ethereum.vm.DataWord;

public enum FederationStorageIndexKey {

    NEW_FEDERATION_BTC_UTXOS_KEY("newFederationBtcUTXOs"),
    NEW_FEDERATION_BTC_UTXOS_KEY_FOR_TESTNET_PRE_HOP("newFederationBtcUTXOsForTestnet"),
    NEW_FEDERATION_BTC_UTXOS_KEY_FOR_TESTNET_POST_HOP("newFedBtcUTXOsForTestnetPostHop"),
    OLD_FEDERATION_BTC_UTXOS_KEY("oldFederationBtcUTXOs"),

    NEW_FEDERATION_KEY("newFederation"),
    OLD_FEDERATION_KEY("oldFederation"),
    PENDING_FEDERATION_KEY("pendingFederation"),

    FEDERATION_ELECTION_KEY("federationElection"),

    ACTIVE_FEDERATION_CREATION_BLOCK_HEIGHT_KEY("activeFedCreationBlockHeight"),
    NEXT_FEDERATION_CREATION_BLOCK_HEIGHT_KEY("nextFedCreationBlockHeight"),

    LAST_RETIRED_FEDERATION_P2SH_SCRIPT_KEY("lastRetiredFedP2SHScript"),

    // Format version keys
    NEW_FEDERATION_FORMAT_VERSION("newFederationFormatVersion"),
    OLD_FEDERATION_FORMAT_VERSION("oldFederationFormatVersion"),
    PENDING_FEDERATION_FORMAT_VERSION("pendingFederationFormatVersion")
    ;

    private final String key;

    FederationStorageIndexKey(String key) {
        this.key = key;
    }

    public DataWord getKey() {
        return DataWord.fromString(key);
    }
}

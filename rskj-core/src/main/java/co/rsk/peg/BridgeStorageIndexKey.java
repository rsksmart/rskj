package co.rsk.peg;

import org.ethereum.vm.DataWord;

public enum BridgeStorageIndexKey {
    NEW_FEDERATION_BTC_UTXOS_KEY("newFederationBtcUTXOs"),
    NEW_FEDERATION_BTC_UTXOS_KEY_FOR_TESTNET_PRE_HOP("newFederationBtcUTXOsForTestnet"),
    NEW_FEDERATION_BTC_UTXOS_KEY_FOR_TESTNET_POST_HOP("newFedBtcUTXOsForTestnetPostHop"),
    OLD_FEDERATION_BTC_UTXOS_KEY("oldFederationBtcUTXOs"),
    BTC_TX_HASHES_ALREADY_PROCESSED_KEY("btcTxHashesAP"),
    RELEASE_REQUEST_QUEUE("releaseRequestQueue"),
    PEGOUTS_WAITING_FOR_CONFIRMATIONS("releaseTransactionSet"),
    PEGOUTS_WAITING_FOR_SIGNATURES("rskTxsWaitingFS"),
    NEW_FEDERATION_KEY("newFederation"),
    OLD_FEDERATION_KEY("oldFederation"),
    PENDING_FEDERATION_KEY("pendingFederation"),
    FEDERATION_ELECTION_KEY("federationElection"),
    LOCK_ONE_OFF_WHITELIST_KEY("lockWhitelist"),
    LOCK_UNLIMITED_WHITELIST_KEY("unlimitedLockWhitelist"),
    LOCKING_CAP_KEY("lockingCap"),
    RELEASE_REQUEST_QUEUE_WITH_TXHASH("releaseRequestQueueWithTxHash"),
    PEGOUTS_WAITING_FOR_CONFIRMATIONS_WITH_TXHASH_KEY("releaseTransactionSetWithTxHash"),
    RECEIVE_HEADERS_TIMESTAMP("receiveHeadersLastTimestamp"),
    // Federation creation keys
    ACTIVE_FEDERATION_CREATION_BLOCK_HEIGHT_KEY("activeFedCreationBlockHeight"),
    NEXT_FEDERATION_CREATION_BLOCK_HEIGHT_KEY("nextFedCreationBlockHeight"),
    LAST_RETIRED_FEDERATION_P2SH_SCRIPT_KEY("lastRetiredFedP2SHScript"),
    // Version keys and versions
    NEW_FEDERATION_FORMAT_VERSION("newFederationFormatVersion"),
    OLD_FEDERATION_FORMAT_VERSION("oldFederationFormatVersion"),
    PENDING_FEDERATION_FORMAT_VERSION("pendingFederationFormatVersion"),
    NEXT_PEGOUT_HEIGHT_KEY("nextPegoutHeight"),

    // Compound keys
    BTC_TX_HASH_AP("btcTxHashAP"),
    COINBASE_INFORMATION("coinbaseInformation"),
    BTC_BLOCK_HEIGHT("btcBlockHeight"),
    FAST_BRIDGE_HASH_USED_IN_BTC_TX("fastBridgeHashUsedInBtcTx"),
    FAST_BRIDGE_FEDERATION_INFORMATION("fastBridgeFederationInformation"),

    PEGOUT_TX_SIG_HASH("pegoutTxSigHash"),
    ;

    private final String key;

    BridgeStorageIndexKey(String key) {
        this.key = key;
    }

    public DataWord getKey() {
        return DataWord.fromString(key);
    }

    public DataWord getCompoundKey(String delimiter, String identifier) {
        return DataWord.fromLongString(key + delimiter + identifier);
    }
}

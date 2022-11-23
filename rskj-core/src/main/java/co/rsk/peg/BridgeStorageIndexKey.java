package co.rsk.peg;

import org.ethereum.vm.DataWord;

public enum BridgeStorageIndexKey {
    NEW_FEDERATION_BTC_UTXOS_KEY("newFederationBtcUTXOs"),
    NEW_FEDERATION_BTC_UTXOS_KEY_FOR_TESTNET_PRE_HOP("newFederationBtcUTXOsForTestnet"),
    NEW_FEDERATION_BTC_UTXOS_KEY_FOR_TESTNET_POST_HOP("newFedBtcUTXOsForTestnetPostHop"),
    OLD_FEDERATION_BTC_UTXOS_KEY("oldFederationBtcUTXOs"),
    BTC_TX_HASHES_ALREADY_PROCESSED_KEY("btcTxHashesAP"),
    RELEASE_REQUEST_QUEUE("releaseRequestQueue"),
    RELEASE_TX_SET("releaseTransactionSet"),
    RSK_TXS_WAITING_FOR_SIGNATURES_KEY("rskTxsWaitingFS"),
    NEW_FEDERATION_KEY("newFederation"),
    OLD_FEDERATION_KEY("oldFederation"),
    PENDING_FEDERATION_KEY("pendingFederation"),
    FEDERATION_ELECTION_KEY("federationElection"),
    LOCK_ONE_OFF_WHITELIST_KEY("lockWhitelist"),
    LOCK_UNLIMITED_WHITELIST_KEY("unlimitedLockWhitelist"),
    FEE_PER_KB_KEY("feePerKb"),
    FEE_PER_KB_ELECTION_KEY("feePerKbElection"),
    LOCKING_CAP_KEY("lockingCap"),
    RELEASE_REQUEST_QUEUE_WITH_TXHASH("releaseRequestQueueWithTxHash"),
    RELEASE_TX_SET_WITH_TXHASH("releaseTransactionSetWithTxHash"),
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
    ;

    private String key;

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

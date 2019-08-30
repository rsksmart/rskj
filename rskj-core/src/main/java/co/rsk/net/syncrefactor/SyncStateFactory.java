package co.rsk.net.syncrefactor;

import co.rsk.crypto.Keccak256;
import co.rsk.net.NodeID;
import co.rsk.net.sync.PeersInformation;
import co.rsk.net.sync.SyncConfiguration;
import co.rsk.validators.BlockHeaderValidationRule;

public class SyncStateFactory {

    private SyncConfiguration syncConfiguration;
    private PeersInformation peersInformation;
    private SyncMessager syncMessager;
    private BlockHeaderValidationRule blockHeaderValidationRule;

    public SyncStateFactory(SyncConfiguration syncConfiguration,
                            PeersInformation peersInformation,
                            SyncMessager syncMessager,
                            BlockHeaderValidationRule blockHeaderValidationRule) {
        this.syncConfiguration = syncConfiguration;
        this.peersInformation = peersInformation;
        this.syncMessager = syncMessager;
        this.blockHeaderValidationRule = blockHeaderValidationRule;
    }

    public SyncState newCheckingBestHeaderSyncState(NodeID selectedPeerId,
                                                    Keccak256 bestBlockHash) {
        return new CheckingBestHeaderSyncState(
                this,
                syncMessager,
                peersInformation,
                blockHeaderValidationRule,
                selectedPeerId,
                bestBlockHash);
    }

    public SyncState newDecidingSyncState() {

        return new DecidingSyncState(
                this,
                syncConfiguration,
                peersInformation,
                syncMessager);
    }
}

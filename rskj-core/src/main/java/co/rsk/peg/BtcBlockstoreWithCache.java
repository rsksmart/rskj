package co.rsk.peg;

import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.StoredBlock;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.bitcoinj.store.BtcBlockStore;

public interface BtcBlockstoreWithCache extends BtcBlockStore {

    StoredBlock getFromCache(Sha256Hash hash) throws BlockStoreException;
}

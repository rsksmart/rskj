package co.rsk.peg;

import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.StoredBlock;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.bitcoinj.store.BtcBlockStore;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;

public class BlockHashByHeightIndex {

    private final BtcBlockStoreWithCache btcBlockStore;
    private final long maxDepthForInitialization;
    private final int confirmationsRequired;

    private static Map<Integer, Pair<Sha256Hash, Optional<Sha256Hash>>> index;
    private static int maxHeight;
    private int minHeight;

    private Map<Integer, Pair<Sha256Hash, Optional<Sha256Hash>>> getIndex() {
        if (index == null) {
            index = new HashMap<>();
        }
        return index;
    }

    private void setMaxHeight(int newValue) {
        if (maxHeight < newValue) {
            maxHeight = newValue;
        }
    }

    public BlockHashByHeightIndex(
        BtcBlockStoreWithCache  btcBlockStore,
        long maxDepthForInitialization,
        int confirmationsRequired
    ) {
        this.btcBlockStore = btcBlockStore;
        this.maxDepthForInitialization = maxDepthForInitialization;
        this.confirmationsRequired = confirmationsRequired;
        this.minHeight = 0;

        this.generateIndex();
    }

    private void generateIndex() {
        try {
            StoredBlock storedBlock = btcBlockStore.getChainHead();
            setMaxHeight(storedBlock.getHeight());
            while(storedBlock != null && this.getIndex().size() < maxDepthForInitialization) {
                this.getIndex().put(
                    storedBlock.getHeight(),
                    Pair.of(
                        storedBlock.getHeader().getHash(),
                        Optional.empty() // TODO: como voy a encontrar los que no son mainchain de una?!
                    )
                );
                this.minHeight = storedBlock.getHeight();
                storedBlock = storedBlock.getPrev(btcBlockStore);
            }
        } catch (BlockStoreException e) {
            // TODO: esto es solo para poder testear ahora
        }
    }

    public void processNewBlocks() {
        try {
            Sha256Hash latestBlockHash = this.getIndex().get(this.maxHeight).getLeft();
            StoredBlock storedBlock = btcBlockStore.getChainHead();
            setMaxHeight(storedBlock.getHeight());
            while (!storedBlock.getHeader().getHash().equals(latestBlockHash)) {
                this.getIndex().put(
                    storedBlock.getHeight(),
                    Pair.of(
                        storedBlock.getHeader().getHash(),
                        Optional.empty() // TODO: como voy a encontrar los que no son mainchain de una?!
                    )
                );
                storedBlock = storedBlock.getPrev(btcBlockStore);
            }
        } catch (BlockStoreException e) {
            // TODO: esto es solo para poder testear ahora
        }
    }

    public Optional<Pair<Sha256Hash, Optional<Sha256Hash>>> get(Integer height) {
        if (height > this.maxHeight ||
            height < this.minHeight ||
            height > this.maxHeight - this.confirmationsRequired ||
            !this.index.containsKey(height)
        ) {
            return Optional.empty();
        }
        return Optional.of(this.index.get(height));
    }

}

package co.rsk.blocks;

import org.ethereum.core.Block;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.datasource.LevelDbDataSource;
import org.ethereum.db.IndexedBlockStore;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.ethereum.db.IndexedBlockStore.BLOCK_INFO_SERIALIZER;

/**
 * Created by Sergio Demian Lerner on 12/17/2018.
 */
public class BlockstoreBlockPlayer implements BlockPlayer, AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger("blockplayer");
    private IndexedBlockStore blockStore;
    private long blockNumber;
    private DB indexDB;
    private KeyValueDataSource blocksDB;

    public BlockstoreBlockPlayer(String filename, long blockNumber) {
        this.blockNumber = blockNumber;
        // filePath : must not include the "/blocks/" subdirectory.
        File blockIndexDirectory = new File(filename + "/blocks/");
        File dbFile = new File(blockIndexDirectory, "index");
        if (!blockIndexDirectory.exists()) {
            logger.error("Cannot open database to replay blocks fromb: ", filename);
            return;
        }

        indexDB = DBMaker.fileDB(dbFile)
                .closeOnJvmShutdown()
                .make();

        Map<Long, List<IndexedBlockStore.BlockInfo>> indexMap = indexDB.hashMapCreate("index")
                .keySerializer(Serializer.LONG)
                .valueSerializer(BLOCK_INFO_SERIALIZER)
                .counterEnable()
                .makeOrGet();

        blocksDB = new LevelDbDataSource("blocks", filename);
        blocksDB.init();

        blockStore = new IndexedBlockStore(indexMap, blocksDB, indexDB);
    }

    public Block readBlock() {
        Block result = blockStore.getChainBlockByNumber(blockNumber);
        if (result != null) {
            blockNumber++;
        }
        return result;
    }

    @Override
    public void close() throws Exception {
        if (indexDB != null) {
            indexDB.close();
        }
        if (blocksDB != null) {
            blocksDB.close();
        }
    }
}
package co.rsk.db;

import co.rsk.crypto.Keccak256;
import org.ethereum.TestUtils;
import org.ethereum.db.IndexedBlockStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapdb.DBMaker;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MapDbBlockIndexIntTest {

    private MapDBBlocksIndex mapDBBlocksIndex;

    @BeforeEach
    void setUp() {
        mapDBBlocksIndex = new MapDBBlocksIndex(DBMaker.memoryDB().make());
    }


    @Test
    void removeBlock_byNumber() {
        Keccak256 hash1 = new Keccak256(TestUtils.generateBytes("block1", 32));
        Keccak256 hash2 = new Keccak256(TestUtils.generateBytes("block2", 32));
        List<IndexedBlockStore.BlockInfo> blockInfoList = new ArrayList<>();
        blockInfoList.add(getBlockInfo(hash1));
        blockInfoList.add(getBlockInfo(hash2));
        mapDBBlocksIndex.putBlocks(1, blockInfoList);
        mapDBBlocksIndex.removeBlock(1L, hash1);
        List<IndexedBlockStore.BlockInfo> savedBlockInfos = mapDBBlocksIndex.getBlocksByNumber(1L);
        assertEquals(1, savedBlockInfos.size());
        assertEquals(hash2, savedBlockInfos.get(0).getHash());
    }

    @Test
    void removeBlock_byNumber_removeFromIndexIfEmpty() {
        Keccak256 hash1 = new Keccak256(TestUtils.generateBytes("block1", 32));
        List<IndexedBlockStore.BlockInfo> blockInfoList = new ArrayList<>();
        blockInfoList.add(getBlockInfo(hash1));
        mapDBBlocksIndex.putBlocks(1, blockInfoList);
        mapDBBlocksIndex.removeBlock(1L, hash1);
        assertFalse(mapDBBlocksIndex.contains(1L));
    }

    private IndexedBlockStore.BlockInfo getBlockInfo(Keccak256 hash) {
        IndexedBlockStore.BlockInfo blockInfo = new IndexedBlockStore.BlockInfo();
        blockInfo.setMainChain(true);
        blockInfo.setHash(hash.getBytes());
        return blockInfo;
    }
}

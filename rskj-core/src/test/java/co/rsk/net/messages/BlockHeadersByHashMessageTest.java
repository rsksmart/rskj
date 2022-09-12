package co.rsk.net.messages;

import co.rsk.blockchain.utils.BlockGenerator;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by ajlopez on 24/08/2017.
 */
class BlockHeadersByHashMessageTest {
    @Test
    void createMessage() {
        List<BlockHeader> blocks = new ArrayList<>();
        BlockGenerator blockGenerator = new BlockGenerator();

        Block block = blockGenerator.getGenesisBlock();

        for (int k = 1; k <= 4; k++) {
            Block b = blockGenerator.createChildBlock(block);
            blocks.add(b.getHeader());
        }

        BlockHeadersResponseMessage message = new BlockHeadersResponseMessage(1, blocks);

        Assertions.assertEquals(1, message.getId());
        List<BlockHeader> mblocks = message.getBlockHeaders();

        Assertions.assertEquals(mblocks.size(), blocks.size());

        for (int i = 0; i < blocks.size(); i++)
            Assertions.assertEquals(blocks.get(1).getHash(), mblocks.get(1).getHash());
    }
}

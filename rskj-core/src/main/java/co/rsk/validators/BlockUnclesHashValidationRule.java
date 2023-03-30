package co.rsk.validators;

import co.rsk.core.bc.BlockExecutor;
import co.rsk.panic.PanicProcessor;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.ByteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlockUnclesHashValidationRule implements BlockValidationRule {

    private static final Logger logger = LoggerFactory.getLogger("blockvalidator");
    private static final PanicProcessor panicProcessor = new PanicProcessor();

    @Override
    public boolean isValid(Block block, BlockExecutor blockExecutor) {
        byte[] unclesHeader = block.getHeader().getUnclesHash();
        byte[] unclesBlock = HashUtil.keccak256(BlockHeader.getUnclesEncoded(block.getUncleList()));

        if (!ByteUtil.fastEquals(unclesHeader, unclesBlock)) {
            String message = String.format("Block's given Uncle Hash doesn't match: %s != %s",
                    ByteUtil.toHexString(unclesHeader), ByteUtil.toHexString(unclesBlock));
            logger.warn(message);
            panicProcessor.panic("invaliduncle", message);
            return false;
        }
        return true;
    }
}

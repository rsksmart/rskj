package co.rsk.validators;

import co.rsk.panic.PanicProcessor;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.crypto.HashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

public class BlockUnclesHashValidationRule implements BlockValidationRule {

    private static final Logger logger = LoggerFactory.getLogger("blockvalidator");
    private static final PanicProcessor panicProcessor = new PanicProcessor();

    @Override
    public boolean isValid(Block block) {
        BlockHeader header = block.getHeader();
        String unclesHash = Hex.toHexString(header.getUnclesHash());
        String unclesListHash = Hex.toHexString(HashUtil.sha3(header.getUnclesEncoded(block.getUncleList())));

        if (!unclesHash.equals(unclesListHash)) {
            logger.warn("Block's given Uncle Hash doesn't match: {} != {}", unclesHash, unclesListHash);
            panicProcessor.panic("invaliduncle", String.format("Block's given Uncle Hash doesn't match: %s != %s", unclesHash, unclesListHash));
            return false;
        }
        return true;
    }
}

package co.rsk.validators;

import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;

public class DummyBlockValidationRule implements BlockValidationRule, BlockHeaderValidationRule {
    @Override
    public boolean isValid(Block block) {
        return true;
    }

    @Override
    public boolean isValid(BlockHeader header) {
        return true;
    }
}

package co.rsk.validators;

import org.ethereum.core.Block;

public class DummyBlockValidationRule implements BlockValidationRule {
    @Override
    public boolean isValid(Block block) {
        return true;
    }
}

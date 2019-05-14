package co.rsk.pcc.blockheader;

import co.rsk.pcc.ExecutionEnvironment;
import co.rsk.pcc.NativeMethod;
import org.ethereum.core.Block;
import org.ethereum.util.ByteUtil;

import java.math.BigInteger;
import java.util.Optional;

/**
 * Base class of BlockHeader contract methods to provide common functionality to all methods.
 *
 * @author Diego Masini
 */
public abstract class BlockHeaderContractMethod extends NativeMethod {
    private final BlockAccessor blockAccessor;

    public BlockHeaderContractMethod(ExecutionEnvironment executionEnvironment, BlockAccessor blockAccessor) {
       super(executionEnvironment);
        this.blockAccessor = blockAccessor;
    }

    @Override
    public Object execute(Object[] arguments) {
        short blockDepth;
        try {
            blockDepth = ((BigInteger) arguments[0]).shortValueExact();
        } catch (ArithmeticException e) {
            return ByteUtil.EMPTY_BYTE_ARRAY;
        }

        Optional<Block> block = blockAccessor.getBlock(blockDepth, getExecutionEnvironment());
        if (block.isPresent()) {
            return internalExecute(block.get(), arguments);
        }

        return ByteUtil.EMPTY_BYTE_ARRAY;
    }

    protected abstract Object internalExecute(Block block, Object[] arguments);

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean onlyAllowsLocalCalls() {
        return false;
    }

    @Override
    public long getGas(Object[] parsedArguments, byte[] originalData) {
        return 1000L + super.getGas(parsedArguments, originalData);
    }
}

package co.rsk.pcc.blockheader;

import co.rsk.pcc.ExecutionEnvironment;
import co.rsk.pcc.NativeMethod;
import co.rsk.pcc.exception.NativeContractIllegalArgumentException;
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
    public Object execute(Object[] arguments) throws NativeContractIllegalArgumentException {
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

    protected abstract Object internalExecute(Block block, Object[] arguments) throws NativeContractIllegalArgumentException;

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
        return 4000L + super.getGas(parsedArguments, originalData);
    }
}

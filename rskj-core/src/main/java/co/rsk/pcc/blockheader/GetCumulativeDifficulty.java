package co.rsk.pcc.blockheader;

import co.rsk.pcc.ExecutionEnvironment;
import co.rsk.pcc.exception.NativeContractIllegalArgumentException;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.CallTransaction;

public class GetCumulativeDifficulty extends BlockHeaderContractMethod {
    private final CallTransaction.Function function = CallTransaction.Function.fromSignature(
        "getCumulativeDifficulty",
        new String[]{"int256"},
        new String[]{"bytes"}
    );

    public GetCumulativeDifficulty(ExecutionEnvironment executionEnvironment, BlockAccessor blockAccessor) {
        super(executionEnvironment, blockAccessor);
    }

    @Override
    public CallTransaction.Function getFunction() {
        return function;
    }

    @Override
    protected Object internalExecute(Block block, Object[] arguments) throws NativeContractIllegalArgumentException {
        return block.getCumulativeDifficulty().getBytes();
    }

    @Override
    public boolean isEnabled() {
        return getExecutionEnvironment().getActivations().isActive(ConsensusRule.RSKIP529);
    }
}

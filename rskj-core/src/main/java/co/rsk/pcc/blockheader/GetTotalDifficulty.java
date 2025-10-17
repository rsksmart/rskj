package co.rsk.pcc.blockheader;

import co.rsk.core.BlockDifficulty;
import co.rsk.pcc.ExecutionEnvironment;
import co.rsk.pcc.exception.NativeContractIllegalArgumentException;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.CallTransaction;
import org.ethereum.db.BlockStore;

public class GetTotalDifficulty extends BlockHeaderContractMethod {
    private final CallTransaction.Function function = CallTransaction.Function.fromSignature(
        "getTotalDifficulty",
        new String[]{"int256"},
        new String[]{"bytes"}
    );

    public GetTotalDifficulty(ExecutionEnvironment executionEnvironment, BlockAccessor blockAccessor) {
        super(executionEnvironment, blockAccessor);
    }

    @Override
    public CallTransaction.Function getFunction() {
        return function;
    }

    @Override
    protected Object internalExecute(Block block, Object[] arguments) throws NativeContractIllegalArgumentException {
        BlockStore blockStore = getExecutionEnvironment().getBlockStore();
        BlockDifficulty totalDifficulty = blockStore.getTotalDifficultyForHash(block.getHash().getBytes());

        return totalDifficulty.getBytes();
    }

    @Override
    public boolean isEnabled() {
        return getExecutionEnvironment().getActivations().isActive(ConsensusRule.RSKIP536);
    }
}

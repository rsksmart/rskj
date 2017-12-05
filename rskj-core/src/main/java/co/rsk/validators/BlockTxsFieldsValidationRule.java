package co.rsk.validators;


import co.rsk.panic.PanicProcessor;
import org.apache.commons.collections4.CollectionUtils;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;


/**
 * Created by SDL on 12/4/2017.
 */
public class BlockTxsFieldsValidationRule implements BlockParentDependantValidationRule {
    private static final Logger logger = LoggerFactory.getLogger("blockvalidator");
    private static final PanicProcessor panicProcessor = new PanicProcessor();
    @Override
    public boolean isValid(Block block, Block parent) {
        if (block == null) {
            logger.warn("BlockTxsFieldsValidationRule - block is null");
            return false;
        }

        List<Transaction> txs = block.getTransactionsList();
        if (CollectionUtils.isEmpty(txs))
            return true;

        for (Transaction tx : txs) {
            try {
                tx.verify();
            } catch (RuntimeException e) {
                logger.warn("Invalid transaction: {}: {}",
                        e.getMessage(), tx);

                return false;
            }
        }

        return true;

    }
}


package co.rsk.net.handler.txvalidator;

import co.rsk.core.Coin;
import co.rsk.core.ReversibleTransactionExecutor;
import co.rsk.net.TransactionValidationResult;
import co.rsk.util.HexUtils;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.AccountState;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.db.BlockStore;
import org.ethereum.vm.aa.AATransaction;
import org.ethereum.vm.program.ProgramResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;

public class TxAAValidatorCallValidate implements TxValidatorStep {

    private static final Logger logger = LoggerFactory.getLogger("txpendingvalidator");
    private static final byte[] MAX_GAS_LIMIT = BigInteger.valueOf(20000).toByteArray();

    private final Constants constants;
    private final ActivationConfig activationConfig;
    private final ReversibleTransactionExecutor reversibleTransactionExecutor;
    private final BlockStore blockStore;

    public TxAAValidatorCallValidate(Constants constants, ActivationConfig activationConfig,
                                     ReversibleTransactionExecutor reversibleTransactionExecutor, BlockStore blockStore) {
        this.constants = constants;
        this.activationConfig = activationConfig;
        this.reversibleTransactionExecutor = reversibleTransactionExecutor;
        this.blockStore = blockStore;
    }

    @Override
    public TransactionValidationResult validate(Transaction tx, @Nullable AccountState state, BigInteger gasLimit, Coin minimumGasPrice, long bestBlockNumber, boolean isFreeTx) {
        if (tx.getType() != Transaction.AA_TYPE) {
            return TransactionValidationResult.ok();
        }

        final Block latestBlock = blockStore.getBestBlock();
        final ProgramResult result = reversibleTransactionExecutor.executeTransaction(latestBlock, latestBlock.getCoinbase(), tx.getGasPrice().getBytes(), MAX_GAS_LIMIT, tx.getSender().getBytes(), tx.getValue().getBytes(), getTxData(tx), tx.getSender());
        if (result.isRevert()) {
            return TransactionValidationResult.withError("AA Validation call reverted.");
        }
        if (result.getException() != null) {
            return TransactionValidationResult.withError("AA Validation call error:" + result.getException().getMessage());
        }
        String response = HexUtils.toUnformattedJsonHex(result.getHReturn());
        logger.info("TxAAValidatorCallValidate response:", response);
        if ("0x1626ba7e".equals(response)) {
            return TransactionValidationResult.ok();
        }
        return TransactionValidationResult.withError("AA Validation call error:" + response);
    }


    // AA - Depends on the type of tx it transform the data
    private byte[] getTxData(Transaction tx) {
        final AATransaction aaTransaction = new AATransaction(tx.getType(),
                tx.getSender().toHexString(),
                tx.getReceiveAddress().toHexString(),
                tx.getGasLimit(),
                tx.getGasPrice().asBigInteger(),
                tx.getNonce(),
                tx.getValue().asBigInteger(),
                tx.getData(),
                tx.getRawsignature());

        DynamicBytes hash = new DynamicBytes(tx.getRawHash().getBytes());

        final String encoded = FunctionEncoder.encode(
                new Function("validateTransaction",
                        Arrays.<Type>asList(hash, aaTransaction),
                        Collections.emptyList()
                )
        );
        return HexUtils.stringHexToByteArray(encoded);
    }
}

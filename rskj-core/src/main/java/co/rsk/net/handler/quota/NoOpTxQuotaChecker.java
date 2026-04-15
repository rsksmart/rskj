package co.rsk.net.handler.quota;

import org.ethereum.core.Transaction;

import javax.annotation.Nullable;

public class NoOpTxQuotaChecker implements TxQuotaChecker {

    public static final NoOpTxQuotaChecker INSTANCE = new NoOpTxQuotaChecker();

    private NoOpTxQuotaChecker(){

    }

    @Override
    public boolean acceptTx(Transaction newTx, @Nullable Transaction replacedTx, TxQuotaCheckerImpl.CurrentContext currentContext) {
        return false;
    }

    @Override
    public void cleanMaxQuotas() {}
}

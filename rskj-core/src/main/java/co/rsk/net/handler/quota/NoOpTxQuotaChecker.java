package co.rsk.net.handler.quota;

import org.ethereum.core.Transaction;

import javax.annotation.Nullable;

public class NoOpTxQuotaChecker implements TxQuotaChecker {

    public static final NoOpTxQuotaChecker INSTANCE = new NoOpTxQuotaChecker();

    private NoOpTxQuotaChecker(){

    }

    @Override
    public boolean acceptTx(Transaction newTx, @Nullable Transaction replacedTx, TxQuotaCheckerImpl.CurrentContext currentContext) {
        // Quota enforcement is disabled; accept all transactions.
        return true;
    }

    @Override
    public void cleanMaxQuotas() {
        // Account transaction rate limiting is disabled, so there are no quotas to clean.
    }
}

package co.rsk.net.handler.quota;

import org.ethereum.core.Transaction;

import javax.annotation.Nullable;

public interface TxQuotaChecker {

    boolean acceptTx(Transaction newTx, @Nullable Transaction replacedTx, TxQuotaCheckerImpl.CurrentContext currentContext);

    void cleanMaxQuotas();
}

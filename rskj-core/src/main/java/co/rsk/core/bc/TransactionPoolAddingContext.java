package co.rsk.core.bc;

import co.rsk.core.RskAddress;
import co.rsk.db.RepositorySnapshot;
import org.ethereum.core.Block;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;

import java.math.BigInteger;

public record TransactionPoolAddingContext(
        Transaction tx,
        RepositorySnapshot repository,
        PendingState pendingState,
        Block bestBlock,
        RskAddress sender,
        BigInteger nonce
) {
    public TransactionPoolAddingContext(
            Transaction tx,
            RepositorySnapshot repository,
            PendingState pendingState,
            Block bestBlock,
            SignatureCache signatureCache
    ) {
        this(
                tx,
                repository,
                pendingState,
                bestBlock,
                tx.getSender(signatureCache),
                tx.getNonceAsInteger()
        );
    }
}

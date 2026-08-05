package co.rsk.net.handler.quota;

import co.rsk.core.bc.PendingState;
import co.rsk.db.RepositorySnapshot;
import org.ethereum.core.Block;
import org.ethereum.listener.GasPriceTracker;

public record TxQuotaContext(
        Block bestBlock,
        PendingState pendingState,
        RepositorySnapshot repository,
        GasPriceTracker gasPriceTracker
) {}

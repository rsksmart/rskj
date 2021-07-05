package co.rsk.core.bc;

import co.rsk.core.RskAddress;
import co.rsk.db.RepositorySnapshot;
import org.ethereum.core.Repository;
import org.ethereum.core.TransactionSet;
import org.ethereum.vm.DataWord;
import org.junit.Test;

import java.util.Locale;

import static org.mockito.Mockito.*;

public class PendingStateTest {

    public static final RskAddress RSK_ADDRESS = new RskAddress("0xc7A5500A684CC34D62915E3a9c4B8A46334Ac77f".toLowerCase(Locale.ROOT));

    @Test
    public void getAccountProof() {
        Repository repository = spy(Repository.class);

        PendingState pendingState = createPendingState(repository);
        pendingState.getAccountProof(RSK_ADDRESS);

        verify(repository,times(1)).getAccountProof(RSK_ADDRESS);
    }

    @Test
    public void getStorageProof() {
        Repository repository = spy(Repository.class);

        PendingState pendingState = createPendingState(repository);
        DataWord storageKey = DataWord.valueOf(0);
        pendingState.getStorageProof(RSK_ADDRESS, storageKey);

        verify(repository,times(1)).getStorageProof(RSK_ADDRESS, storageKey);
    }

    private PendingState createPendingState(Repository repository) {
        RepositorySnapshot repositorySnapshot = mock(RepositorySnapshot.class);
        when(repositorySnapshot.startTracking()).thenReturn(repository);
        TransactionSet pendingTransactions = mock(TransactionSet.class);
        PendingState.TransactionExecutorFactory transactionExecutorFactory =
                mock(PendingState.TransactionExecutorFactory.class);

        return new PendingState(repositorySnapshot, pendingTransactions, transactionExecutorFactory);
    }
}

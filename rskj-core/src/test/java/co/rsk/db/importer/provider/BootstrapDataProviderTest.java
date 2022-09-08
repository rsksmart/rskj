package co.rsk.db.importer.provider;

import co.rsk.db.importer.BootstrapImportException;
import co.rsk.db.importer.provider.index.BootstrapIndexCandidateSelector;
import co.rsk.db.importer.provider.index.BootstrapIndexRetriever;
import co.rsk.db.importer.provider.index.data.BootstrapDataEntry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BootstrapDataProviderTest {

    @Test
    public void retrieveDataInsufficientsSources() {
        BootstrapDataVerifier bootstrapDataVerifier = mock(BootstrapDataVerifier.class);
        when(bootstrapDataVerifier.verifyEntries(any())).thenReturn(1);

        BootstrapFileHandler bootstrapFileHandler = mock(BootstrapFileHandler.class);
        BootstrapIndexCandidateSelector bootstrapIndexCandidateSelector = mock(BootstrapIndexCandidateSelector.class);
        Map<String, BootstrapDataEntry> entries = new HashMap<>();
        BootstrapIndexCandidateSelector.HeightCandidate mchd = new BootstrapIndexCandidateSelector.HeightCandidate(1L, entries);
        when(bootstrapIndexCandidateSelector.getHeightData(any())).thenReturn(mchd);
        BootstrapDataProvider bootstrapDataProvider = new BootstrapDataProvider(
                bootstrapDataVerifier,
                bootstrapFileHandler,
                bootstrapIndexCandidateSelector,
                mock(BootstrapIndexRetriever.class),
                2
        );

        Assertions.assertThrows(BootstrapImportException.class, () -> bootstrapDataProvider.retrieveData());
    }

    @Test
    public void retrieveData() {
        BootstrapDataVerifier bootstrapDataVerifier = mock(BootstrapDataVerifier.class);
        when(bootstrapDataVerifier.verifyEntries(any())).thenReturn(2);

        BootstrapFileHandler bootstrapFileHandler = mock(BootstrapFileHandler.class);
        BootstrapIndexCandidateSelector bootstrapIndexCandidateSelector = mock(BootstrapIndexCandidateSelector.class);
        Map<String, BootstrapDataEntry> entries = new HashMap<>();
        BootstrapIndexCandidateSelector.HeightCandidate mchd = new BootstrapIndexCandidateSelector.HeightCandidate(1L, entries);
        when(bootstrapIndexCandidateSelector.getHeightData(any())).thenReturn(mchd);
        BootstrapDataProvider bootstrapDataProvider = new BootstrapDataProvider(
                bootstrapDataVerifier,
                bootstrapFileHandler,
                bootstrapIndexCandidateSelector,
                mock(BootstrapIndexRetriever.class),
                2
        );
        bootstrapDataProvider.retrieveData();

    }
}

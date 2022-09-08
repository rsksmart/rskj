package co.rsk.db.importer.provider.index;

import co.rsk.db.importer.BootstrapURLProvider;
import co.rsk.db.importer.provider.index.data.BootstrapDataEntry;
import co.rsk.db.importer.provider.index.data.BootstrapDataIndex;
import co.rsk.db.importer.provider.index.data.BootstrapDataSignature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BootstrapIndexRetrieverTest {

    @Test
    public void retrieveEmpty() {
        BootstrapIndexRetriever bootstrapIndexRetriever = new BootstrapIndexRetriever(
                new ArrayList<>(),
                mock(BootstrapURLProvider.class),
                mock(ObjectMapper.class)
        );
        List<BootstrapDataIndex> indices = bootstrapIndexRetriever.retrieve();
        Assertions.assertTrue(indices.isEmpty());
    }

    @Test
    public void retrievePublicKey() throws IOException {
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        BootstrapDataIndex bdi = new BootstrapDataIndex(Collections.singletonList(
                new BootstrapDataEntry(1, "", "db", "hash",
                                       new BootstrapDataSignature("r", "s"))));

        when(objectMapper.readValue(any(URL.class), eq(BootstrapDataIndex.class))).thenReturn(bdi);

        BootstrapURLProvider bootstrapUrlProvider = mock(BootstrapURLProvider.class);
        when(bootstrapUrlProvider.getFullURL(any())).thenReturn(new URL("http://localhost"));

        BootstrapIndexRetriever bootstrapIndexRetriever = new BootstrapIndexRetriever(
                Collections.singletonList("key1"),
                bootstrapUrlProvider,
                objectMapper
        );
        List<BootstrapDataIndex> indices = bootstrapIndexRetriever.retrieve();
        Assertions.assertTrue(indices.contains(bdi));
    }
}

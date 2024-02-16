/*
 * This file is part of RskJ
 * Copyright (C) 2024 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package co.rsk.net.discovery;

import co.rsk.util.SimpleFileWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class KnownPeersHandlerTest {

    @Mock
    private SimpleFileWriter mockFileDataSaver;

    @TempDir
    Path tempDir;

    @Test
    void testSavePeersSuccessfully() throws IOException {
        Map<String, String> knownPeers = new HashMap<>();
        knownPeers.put("peer1", "192.168.1.1");
        knownPeers.put("peer2", "192.168.1.2");
        ArgumentCaptor<Properties> propertiesCaptor = ArgumentCaptor.forClass(Properties.class);

        KnownPeersHandler knownPeersHandler  = new KnownPeersHandler(tempDir, mockFileDataSaver);
        knownPeersHandler.savePeers(knownPeers);

        verify(mockFileDataSaver).savePropertiesIntoFile(propertiesCaptor.capture(), eq(tempDir));
        Properties properties = propertiesCaptor.getValue();
        assertEquals(2,properties.size());
        for (Map.Entry<String, String> entry : knownPeers.entrySet()) {
            assertEquals(entry.getValue(), properties.getProperty(entry.getKey()));
        }
    }

    @Test
    void testReadPeersSuccessfully() throws Exception {
        Path filePath = tempDir.resolve("peers.list");
        Properties props = new Properties();
        props.setProperty("peer1", "192.168.1.1");
        props.setProperty("peer2", "192.168.1.2");
        try (FileWriter writer = new FileWriter(filePath.toFile())) {
            props.store(writer, null);
        }

        KnownPeersHandler knownPeersHandler = new KnownPeersHandler(filePath, mockFileDataSaver);
        // Since readPeers() reads from the file system directly, we don't use the mocked SimpleFileWriter here.
        List<String> peers = knownPeersHandler.readPeers();

        assertEquals(2, peers.size());
        assertTrue(peers.contains("192.168.1.1"));
        assertTrue(peers.contains("192.168.1.2"));
    }

}

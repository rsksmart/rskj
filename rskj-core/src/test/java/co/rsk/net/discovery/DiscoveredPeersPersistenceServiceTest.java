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

import org.ethereum.util.SimpleFileWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.*;

class DiscoveredPeersPersistenceServiceTest {
    private KnownPeersSaver service;
    private Path filePath;
    private SimpleFileWriter fileDataSaver;

    @BeforeEach
    void setup() throws IOException {
        filePath = Files.createTempFile("test", ".txt");
        fileDataSaver = mock(SimpleFileWriter.class);
        service = new KnownPeersSaver(filePath,fileDataSaver);
    }

    @Test
    void testStop() throws IOException {
        List<String> knownPeers = Arrays.asList("peer1", "peer2", "peer3");
        service.savePeers(knownPeers);
        verify(fileDataSaver, times(1)).saveDataIntoFile(eq("peer1\npeer2\npeer3\n"), eq(filePath));

    }

}
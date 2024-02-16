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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

public class KnownPeersHandler {
    private static final Logger logger = LoggerFactory.getLogger(KnownPeersHandler.class);
    private final Path peerListFileDir;
    private final SimpleFileWriter fileDataSaver;

    public KnownPeersHandler(Path peerListFileDir) {
        this(peerListFileDir, SimpleFileWriter.getInstance());
    }

    public KnownPeersHandler(Path peerListFileDir, SimpleFileWriter fileDataSaver) {
        this.peerListFileDir = peerListFileDir;
        this.fileDataSaver = fileDataSaver;
    }
    public void savePeers(Map<String,String> knownPeers) {
        logger.debug("Saving peers {} to file in {}", knownPeers, peerListFileDir);
        Properties props = new Properties();
        props.putAll(knownPeers);
        try {
            fileDataSaver.savePropertiesIntoFile(props, peerListFileDir);
        } catch (IOException e) {
            logger.error("Error saving active peers to file: {}", e.getMessage());
        }
    }

    public List<String> readPeers(){
        File file = peerListFileDir.toFile();
        Properties props = new Properties();
        if (file.canRead()) {
            try (FileReader reader = new FileReader(file)) {
                props.load(reader);
            } catch (IOException e) {
                logger.error("Error reading active peers from file: {}", e.getMessage());
                return Collections.emptyList();
            }
        }
        return props.values().stream().map(Object::toString).collect(Collectors.toList());
    }
}

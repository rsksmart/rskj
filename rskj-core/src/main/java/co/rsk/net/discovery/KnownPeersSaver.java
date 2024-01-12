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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class KnownPeersSaver {
    private static final Logger logger = LoggerFactory.getLogger(KnownPeersSaver.class);
    private final Path peerListFileDir;
    private final SimpleFileWriter fileDataSaver;

    public KnownPeersSaver(Path peerListFileDir) {
        this(peerListFileDir, SimpleFileWriter.getInstance());
    }

    public KnownPeersSaver(Path peerListFileDir, SimpleFileWriter fileDataSaver) {
        this.peerListFileDir = peerListFileDir;
        this.fileDataSaver = fileDataSaver;
    }

    public void savePeers(List<String> knownPeers) {
        logger.debug("Stop in progress.. Saving known peers list to file");
        if (knownPeers != null && !knownPeers.isEmpty()) {

            StringBuilder sb = new StringBuilder();
            for (String peerAddress : knownPeers) {
                logger.debug("Saving knew peer: {}", peerAddress);
                sb.append(peerAddress).append("\n");
            }
            try {
                fileDataSaver.saveDataIntoFile(sb.toString(), peerListFileDir);
            } catch (IOException e) {
                logger.error("Error saving active peers to file: {}", e.getMessage());
            }
        }
    }
}

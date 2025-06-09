/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

package co.rsk.db;

import co.rsk.config.RskSystemProperties;
import co.rsk.crypto.Keccak256;
import co.rsk.net.Peer;
import co.rsk.net.messages.SnapStateChunkResponseMessage;
import co.rsk.trie.Trie;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.BlockHeader;

import javax.annotation.Nonnull;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class SnapStateChunkResponseMessageStore {

    private final String rootPath;
    private final ObjectMapper objectMapper;

    public SnapStateChunkResponseMessageStore(RskSystemProperties rskSystemProperties) {
        this.rootPath = rskSystemProperties.snapStateChunkResponseMessageDir() + rskSystemProperties.snapStateChunkResponseMessageName();
        this.objectMapper = new ObjectMapper();
    }
    
    private String buildFilePath(Peer peer) {
        return this.rootPath + "/" + peer.getPeerNodeID().toString() + ".txt";
    }

    public SnapStateChunkResponseMessageStoreWriter startWriting(Peer peer) throws IOException {
        final var file  = new File(buildFilePath(peer));
        return new SnapStateChunkResponseMessageStoreWriter(file, objectMapper);
    }

    public SnapStateChunkResponseMessageStoreReader startReading(Peer peer, Consumer<SnapStateChunkResponseMessage> lineReaderFn) throws IOException {
        final var file  = new File(buildFilePath(peer));
        return new SnapStateChunkResponseMessageStoreReader(file, objectMapper, lineReaderFn);
    }

    public static class SnapStateChunkResponseMessageStoreWriter implements Closeable {
        private final BufferedWriter bufferedWriter;
        private final ObjectMapper objectMapper;

        public SnapStateChunkResponseMessageStoreWriter(File file, ObjectMapper objectMapper) throws IOException {
            this.bufferedWriter = new BufferedWriter(new FileWriter(file));
            this.objectMapper = objectMapper;
        }

        public void write(SnapStateChunkResponseMessage snapStateChunkResponseMessage) throws IOException {
            bufferedWriter.write(objectMapper.writeValueAsString(snapStateChunkResponseMessage) + "\n");
        }

        @Override
        public void close() throws IOException {
            bufferedWriter.close();
        }
    }

    public static class SnapStateChunkResponseMessageStoreReader implements Closeable {
        private final BufferedReader bufferedReader;
        private final ObjectMapper objectMapper;
        private final Consumer<SnapStateChunkResponseMessage> lineReaderFn;

        public SnapStateChunkResponseMessageStoreReader(File file, ObjectMapper objectMapper, Consumer<SnapStateChunkResponseMessage> lineReaderFn) throws IOException {
            this.bufferedReader = new BufferedReader(new FileReader(file));
            this.objectMapper = objectMapper;
            this.lineReaderFn = lineReaderFn;
        }

        public void read(long from) throws IOException {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                if(line.isEmpty()) {
                    continue;
                }

                final var msg = objectMapper.readValue(line, SnapStateChunkResponseMessage.class);

                if(msg.getFrom() < from) {
                    continue;
                }

                lineReaderFn.accept(msg);
            }
        }

        @Override
        public void close() throws IOException {
            bufferedReader.close();
        }
    }
}

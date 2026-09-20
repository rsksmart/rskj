/*
 * This file is part of RskJ
 * Copyright (C) 2026 RSK Labs Ltd.
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
package co.rsk.trie.segbuild;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The set of segbuild chunks under a root directory, indexed by the block ranges they cover.
 *
 * <p>Chunks tile the chain with no gaps and no overlaps, so at most one chunk covers any block.
 * Directories whose name is not exactly a chunk name are ignored, which is how a retained but
 * superseded chunk stays out of the set.
 */
public final class SegbuildChunkIndex {

    private final List<SegbuildChunk> chunks;

    private SegbuildChunkIndex(List<SegbuildChunk> chunks) {
        this.chunks = chunks;
    }

    /**
     * Scans {@code root} for chunk directories. Does not open any of them.
     *
     * @throws IllegalArgumentException if the root is not a directory, holds no chunks, or holds
     *                                  chunks whose ranges overlap
     */
    public static SegbuildChunkIndex scan(@Nonnull Path root) {
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Segbuild root is not a directory: " + root);
        }

        List<SegbuildChunk> found = new ArrayList<>();
        try (Stream<Path> entries = Files.list(root)) {
            entries.filter(Files::isDirectory)
                    .map(SegbuildChunk::parse)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .forEach(found::add);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list segbuild root: " + root, e);
        }

        if (found.isEmpty()) {
            throw new IllegalArgumentException("No segbuild chunks found under: " + root);
        }

        Collections.sort(found);

        for (int i = 1; i < found.size(); i++) {
            SegbuildChunk previous = found.get(i - 1);
            SegbuildChunk current = found.get(i);
            if (current.getFirstBlock() <= previous.getLastBlock()) {
                throw new IllegalArgumentException(String.format(
                        "Segbuild chunks overlap: %s and %s", previous, current));
            }
        }

        return new SegbuildChunkIndex(Collections.unmodifiableList(found));
    }

    /**
     * The chunk covering {@code blockNumber}, or empty if no chunk does.
     */
    public Optional<SegbuildChunk> chunkFor(long blockNumber) {
        int low = 0;
        int high = chunks.size() - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            SegbuildChunk chunk = chunks.get(mid);

            if (chunk.covers(blockNumber)) {
                return Optional.of(chunk);
            }

            if (blockNumber < chunk.getFirstBlock()) {
                high = mid - 1;
            } else {
                low = mid + 1;
            }
        }

        return Optional.empty();
    }

    /**
     * The chunk covering {@code blockNumber}.
     *
     * @throws IllegalArgumentException if no chunk covers it
     */
    public SegbuildChunk requireChunkFor(long blockNumber) {
        return chunkFor(blockNumber).orElseThrow(() -> new IllegalArgumentException(String.format(
                "No segbuild chunk covers block %d (the set covers [%d..%d])",
                blockNumber, getFirstBlock(), getLastBlock())));
    }

    public List<SegbuildChunk> getChunks() {
        return chunks;
    }

    public int size() {
        return chunks.size();
    }

    public long getFirstBlock() {
        return chunks.get(0).getFirstBlock();
    }

    public long getLastBlock() {
        return chunks.get(chunks.size() - 1).getLastBlock();
    }
}

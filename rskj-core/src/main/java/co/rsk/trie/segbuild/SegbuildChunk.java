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
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One segbuild chunk: a trie store that is self-contained for a range of blocks.
 *
 * <p>The directory name is the authoritative statement of the range -- nothing inside the chunk
 * repeats it -- and has the form {@code <index>-<first_block>-<last_block>} with the index
 * zero-padded to three digits.
 *
 * <p>The pattern is matched strictly, in full. That is what excludes directories that are
 * deliberately not part of the set, such as a truncated chunk retained alongside its replacement
 * under a {@code .truncated-superseded-*} suffix, as well as unrelated files sitting in the same
 * root. Anything that does not match exactly is not a chunk.
 */
public final class SegbuildChunk implements Comparable<SegbuildChunk> {

    private static final Pattern DIRECTORY_NAME = Pattern.compile("^(\\d{3})-(\\d+)-(\\d+)$");

    /** The trie store lives in this subdirectory of the chunk. */
    public static final String SEALED_DIRECTORY = "sealed";

    private final int index;
    private final long firstBlock;
    private final long lastBlock;
    private final Path directory;

    private SegbuildChunk(int index, long firstBlock, long lastBlock, Path directory) {
        this.index = index;
        this.firstBlock = firstBlock;
        this.lastBlock = lastBlock;
        this.directory = directory;
    }

    /**
     * Parses a chunk directory, or returns empty if the name is not exactly a chunk name.
     */
    public static Optional<SegbuildChunk> parse(@Nonnull Path directory) {
        Path fileName = directory.getFileName();
        if (fileName == null) {
            return Optional.empty();
        }

        Matcher matcher = DIRECTORY_NAME.matcher(fileName.toString());
        if (!matcher.matches()) {
            return Optional.empty();
        }

        long firstBlock;
        long lastBlock;
        int index;
        try {
            index = Integer.parseInt(matcher.group(1));
            firstBlock = Long.parseLong(matcher.group(2));
            lastBlock = Long.parseLong(matcher.group(3));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }

        if (firstBlock > lastBlock) {
            return Optional.empty();
        }

        return Optional.of(new SegbuildChunk(index, firstBlock, lastBlock, directory));
    }

    public boolean covers(long blockNumber) {
        return blockNumber >= firstBlock && blockNumber <= lastBlock;
    }

    public int getIndex() {
        return index;
    }

    public long getFirstBlock() {
        return firstBlock;
    }

    public long getLastBlock() {
        return lastBlock;
    }

    public Path getDirectory() {
        return directory;
    }

    public Path getSealedDirectory() {
        return directory.resolve(SEALED_DIRECTORY);
    }

    @Override
    public int compareTo(@Nonnull SegbuildChunk other) {
        return Long.compare(firstBlock, other.firstBlock);
    }

    @Override
    public String toString() {
        return String.format("chunk %03d [%d..%d]", index, firstBlock, lastBlock);
    }
}

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

package co.rsk.db.importer;

import java.nio.charset.StandardCharsets;

/**
 * On-disk contract for the {@code bootstrap-data.bin} <b>v2</b> format: a self-describing, chunked,
 * streamable layout with no total-size ceiling (it replaces the legacy single-nested-RLP-list format,
 * whose {@code int} length fields capped the payload at {@code Integer.MAX_VALUE} ~ 2 GiB).
 *
 * <pre>
 * HEADER
 *   magic   = "RSKBOOT\n"   (8 bytes)
 *   version = 0x02          (1 byte)
 *
 * SECTION blocks   (tag 0x01)
 * SECTION nodes    (tag 0x02)
 * SECTION values   (tag 0x03)
 * SECTION end      (tag 0x00)   &lt;- terminator
 *
 * SECTION =
 *   [1 byte  section tag]
 *   zero or more chunks:
 *       [8-byte big-endian chunk length L]   (0 &lt; L &le; CHUNK_MAX, plus the rare single oversized element)
 *       [L bytes: a concatenation of whole canonical RLP elements]
 *   [8-byte big-endian 0]                    &lt;- end-of-section sentinel
 * </pre>
 *
 * <p>A chunk always holds a whole number of self-delimiting RLP elements, so the reader decodes each
 * chunk with the existing {@code RLP} element machinery on a bounded {@code byte[]} and then discards it.
 *
 * <p><b>v1/v2 auto-detection:</b> a legacy v1 file always starts with an RLP list prefix byte
 * ({@code 0xc0}+), which never collides with the ASCII {@code 'R'} ({@code 0x52}) that opens the v2
 * magic. The importer dispatches on the first byte.
 *
 * <p>The leaf encoding is identical to v1 (each node {@code RLP.encodeElement(trie.toMessage())}, each
 * long value {@code RLP.encodeElement(value)}, each block {@code LIST[ELEMENT(block), ELEMENT(td)]});
 * only the container framing changes. This holder is shared by the rskj-core importer and is mirrored
 * by the bootstrap-exporter writer (separate repo, so the constants are restated there).
 */
public final class BootstrapV2Format {

    /** First bytes of a v2 {@code bootstrap-data.bin}; the leading {@code 'R'} disambiguates from v1. */
    public static final byte[] MAGIC = "RSKBOOT\n".getBytes(StandardCharsets.US_ASCII);

    public static final byte VERSION = 0x02;

    public static final int TAG_END = 0x00;
    public static final int TAG_BLOCKS = 0x01;
    public static final int TAG_NODES = 0x02;
    public static final int TAG_VALUES = 0x03;

    /**
     * Soft cap on a chunk's payload. The exporter flushes a chunk once its buffer crosses this on an
     * element boundary; a single element larger than this still becomes its own (oversized) chunk. Kept
     * well under {@code Integer.MAX_VALUE} so a chunk always fits a bounded {@code byte[]}.
     */
    public static final long CHUNK_MAX = 256L * 1024 * 1024;

    private BootstrapV2Format() {
    }

    /** Whether {@code firstByte} (the first byte of {@code bootstrap-data.bin}) marks a v2 file. */
    public static boolean isV2(int firstByte) {
        return firstByte == (MAGIC[0] & 0xFF);
    }
}

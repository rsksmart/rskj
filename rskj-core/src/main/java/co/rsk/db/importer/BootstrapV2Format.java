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
 * SECTION values   (tag 0x03)   &lt;- co-located before nodes: values precede the nodes that reference
 * SECTION nodes    (tag 0x02)      them, so the importer applies them in a single streaming pass
 * SECTION end      (tag 0x00)   &lt;- terminator
 *
 * SECTION =
 *   [1 byte  section tag]
 *   zero or more chunks:
 *       [8-byte big-endian chunk length L]   (0 &lt; L &le; MAX_CHUNK_LEN; a reader rejects any larger chunk)
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
 *
 * <p>See {@code bootstrap-data-format.md} in this package for the full narrative specification (v1 + v2).
 */
public final class BootstrapV2Format {

    // First bytes of a v2 bootstrap-data.bin; the leading 'R' disambiguates from v1. Kept private (a
    // mutable array must not be exposed as a public constant); callers use magic() for a fresh copy.
    private static final byte[] MAGIC = "RSKBOOT\n".getBytes(StandardCharsets.US_ASCII);

    /** The v2 file magic bytes (a fresh copy each call, so the internal array cannot be mutated). */
    public static byte[] magic() {
        return MAGIC.clone();
    }

    public static final byte VERSION = 0x02;

    public static final int TAG_END = 0x00;
    public static final int TAG_BLOCKS = 0x01;
    public static final int TAG_NODES = 0x02;
    public static final int TAG_VALUES = 0x03;
    /**
     * Reserved for a future optional metadata/manifest section. Not written or read yet; the reader
     * already skips unknown/unwanted section tags, so adding it later is a backward-compatible change.
     */
    public static final int TAG_MANIFEST = 0x04;

    /**
     * Soft cap on a chunk's payload. The exporter flushes a chunk once its buffer crosses this on an
     * element boundary; a single element larger than this still becomes its own (oversized) chunk. This is
     * an exporter-side <b>tuning knob</b>, not part of the on-disk contract — a different exporter may pick a
     * different value. Kept well under {@code Integer.MAX_VALUE} so a chunk always fits a bounded {@code byte[]}.
     */
    public static final long CHUNK_MAX = 256L * 1024 * 1024;

    /**
     * Format-contract ceiling on a single chunk's declared on-disk length: a reader MUST reject any chunk
     * whose length exceeds this. Unlike {@link #CHUNK_MAX} (an exporter tuning knob), this is a fixed part
     * of the contract, so every reader accepts exactly the same chunk sizes regardless of the exporter's
     * flush threshold — changing {@code CHUNK_MAX} can never silently make deployed readers reject otherwise
     * valid snapshots. An exporter must keep every emitted chunk within this bound (its {@code CHUNK_MAX}
     * plus the largest single element it may emit must not exceed it). Kept well under {@code Integer.MAX_VALUE}
     * so a validated length always fits a bounded {@code byte[]}.
     */
    public static final long MAX_CHUNK_LEN = 512L * 1024 * 1024;

    private BootstrapV2Format() {
    }

    /** Whether {@code firstByte} (the first byte of {@code bootstrap-data.bin}) marks a v2 file. */
    public static boolean isV2(int firstByte) {
        return firstByte == (MAGIC[0] & 0xFF);
    }

    /** Whether {@code firstByte} marks a legacy v1 file (a single top-level RLP list, prefix {@code 0xc0}+). */
    public static boolean isV1(int firstByte) {
        return firstByte >= 0xc0 && firstByte <= 0xff;
    }
}

/*
 * This file is part of RskJ
 * Copyright (C) 2021 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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
package org.ethereum.util;

import org.ethereum.crypto.HashUtil;
import org.ethereum.db.ByteArrayWrapper;

import javax.annotation.Nonnull;
import java.io.*;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

/**
 * {@link MapSnapshot} is a generic class for writing to and reading from a byte stream map snapshots of type {@code Map<ByteArrayWrapper, byte[]>}.
 *
 * There are two appropriate implementations:
 * - {@link MapSnapshot.In} for reading from a stream
 * - {@link MapSnapshot.Out} for writing to a stream
 *
 * This class implements {@link Closeable} interface, so it should be closed after use to dispose internal resources.
 */
public abstract class MapSnapshot<T extends Closeable> implements Closeable {

    private final Closeable stream;

    protected MapSnapshot(@Nonnull T stream) {
        this.stream = Objects.requireNonNull(stream);
    }

    @Override
    public void close() throws IOException {
        stream.close();
    }

    public interface Factory {

        default MapSnapshot.In makeInputSnapshot(@Nonnull InputStream inputStream) {
            return new MapSnapshot.In(inputStream);
        }

        default MapSnapshot.Out makeOutputSnapshot(@Nonnull OutputStream outputStream) {
            return new MapSnapshot.Out(outputStream);
        }

    }

    public static class Out extends MapSnapshot<OutputStream> {

        private final DigestOutputStream digestOutput;
        private final DataOutputStream dataOutput;

        protected Out(@Nonnull OutputStream output) {
            super(output);
            digestOutput = new DigestOutputStream(output, HashUtil.makeMessageDigest());
            dataOutput = new DataOutputStream(digestOutput);
        }

        public void write(@Nonnull Map<ByteArrayWrapper, byte[]> map) throws IOException {
            Objects.requireNonNull(map);

            int count = map.size();
            dataOutput.writeInt(count);

            for (Map.Entry<ByteArrayWrapper, byte[]> entry : map.entrySet()) {
                byte[] key = entry.getKey().getData();
                dataOutput.writeInt(key.length);
                dataOutput.write(key);

                byte[] value = entry.getValue();
                if (value == null) {
                    dataOutput.writeInt(-1);
                } else {
                    dataOutput.writeInt(value.length);
                    if (value.length > 0) {
                        dataOutput.write(value);
                    }
                }
            }

            byte[] digest = cloneArray(digestOutput.getMessageDigest().digest());
            if (digest == null) {
                dataOutput.writeInt(-1);
            } else {
                dataOutput.writeInt(digest.length);
                if (digest.length > 0) {
                    dataOutput.write(digest);
                }
            }
        }
    }

    public static class In extends MapSnapshot<InputStream> {

        private final DigestInputStream digestInput;
        private final DataInputStream dataInput;

        protected In(@Nonnull InputStream input) {
            super(input);
            digestInput = new DigestInputStream(input, HashUtil.makeMessageDigest());
            dataInput = new DataInputStream(digestInput);
        }

        public void read(@Nonnull Map<ByteArrayWrapper, byte[]> map) throws IOException {
            Objects.requireNonNull(map);

            int entryCount = dataInput.readInt();
            if (entryCount < 1) {
                throw new IOException("Invalid data: number of entries");
            }
            for (int i = 0; i < entryCount; i++) {
                int keySize = dataInput.readInt();
                if (keySize < 0) {
                    throw new IOException("Invalid data: key size");
                }
                byte[] key = new byte[keySize];
                if (keySize > 0) {
                    dataInput.readFully(key);
                }

                int valueSize = dataInput.readInt();
                if (valueSize < -1) {
                    throw new IOException("Invalid data: value size");
                }
                byte[] value = null;
                if (valueSize > 0) {
                    value = new byte[valueSize];
                    dataInput.readFully(value);
                } else if (valueSize == 0) {
                    value = new byte[0];
                }

                map.put(ByteUtil.wrap(key), value);
            }

            byte[] digest = cloneArray(digestInput.getMessageDigest().digest());

            byte[] snapshotDigest = null;
            int digestSize = dataInput.readInt();
            if (digestSize >= 0) {
                snapshotDigest = new byte[digestSize];
                if (digestSize > 0) {
                    dataInput.readFully(snapshotDigest);
                }
            }

            if (!MessageDigest.isEqual(digest, snapshotDigest)) {
                throw new IOException("Invalid digest of snapshot");
            }
        }
    }

    private static byte[] cloneArray(byte[] array) {
        return array == null ? null : Arrays.copyOf(array, array.length);
    }
}

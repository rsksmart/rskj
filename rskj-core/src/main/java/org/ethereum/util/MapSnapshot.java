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

import org.ethereum.db.ByteArrayWrapper;

import javax.annotation.Nonnull;
import java.io.*;
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

    protected final T stream;

    protected MapSnapshot(@Nonnull T stream) {
        this.stream = Objects.requireNonNull(stream);
    }

    @Override
    public void close() throws IOException {
        stream.close();
    }

    public interface Factory {

        default MapSnapshot.In makeInputSnapshot(@Nonnull InputStream inputStream) {
            return new MapSnapshot.In(new DataInputStream(inputStream));
        }

        default MapSnapshot.Out makeOutputSnapshot(@Nonnull OutputStream outputStream) {
            return new MapSnapshot.Out(new DataOutputStream(outputStream));
        }

    }

    public static class Out extends MapSnapshot<DataOutputStream> {

        protected Out(@Nonnull OutputStream output) {
            super(new DataOutputStream(output));
        }

        public void write(@Nonnull Map<ByteArrayWrapper, byte[]> map) throws IOException {
            Objects.requireNonNull(map);

            int count = map.size();
            stream.writeInt(count);

            for (Map.Entry<ByteArrayWrapper, byte[]> entry : map.entrySet()) {
                byte[] key = entry.getKey().getData();
                stream.writeInt(key.length);
                stream.write(key);

                byte[] value = entry.getValue();
                if (value == null) {
                    stream.writeInt(-1);
                } else {
                    stream.writeInt(value.length);
                    if (value.length > 0) {
                        stream.write(value);
                    }
                }
            }
        }
    }

    public static class In extends MapSnapshot<DataInputStream> {

        protected In(@Nonnull InputStream input) {
            super(new DataInputStream(input));
        }

        public void read(@Nonnull Map<ByteArrayWrapper, byte[]> map) throws IOException {
            Objects.requireNonNull(map);


            int entryCount = stream.readInt();
            if (entryCount < 1) {
                throw new IOException("Invalid data: number of entries");
            }
            for (int i = 0; i < entryCount; i++) {
                int keySize = stream.readInt();
                if (keySize < 1) {
                    throw new IOException("Invalid data: key size");
                }
                byte[] key = new byte[keySize];
                stream.readFully(key);

                int valueSize = stream.readInt();
                if (valueSize < -1) {
                    throw new IOException("Invalid data: value size");
                }
                byte[] value = null;
                if (valueSize > 0) {
                    value = new byte[valueSize];
                    stream.readFully(value);
                } else if (valueSize == 0) {
                    value = new byte[0];
                }

                map.put(ByteUtil.wrap(key), value);
            }
        }
    }
}

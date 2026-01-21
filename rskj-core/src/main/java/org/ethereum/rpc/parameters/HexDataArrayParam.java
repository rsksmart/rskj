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
package org.ethereum.rpc.parameters;

import co.rsk.util.HexUtils;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.vm.DataWord;

import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Parameter class for deserializing an array of hex-encoded data strings.
 * Used for storage keys in eth_getProof.
 */
@JsonDeserialize(using = HexDataArrayParam.Deserializer.class)
public class HexDataArrayParam implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final List<byte[]> dataList;

    public HexDataArrayParam(List<String> hexStrings) {
        if (hexStrings == null) {
            this.dataList = Collections.emptyList();
            return;
        }

        List<byte[]> result = new ArrayList<>(hexStrings.size());
        for (int i = 0; i < hexStrings.size(); i++) {
            String hexString = hexStrings.get(i);
            try {
                byte[] data = HexUtils.stringHexToByteArray(hexString);
                result.add(data);
            } catch (Exception e) {
                throw RskJsonRpcRequestException.invalidParamError(
                        String.format("Invalid hex data at index %d: %s", i, hexString), e);
            }
        }
        this.dataList = Collections.unmodifiableList(result);
    }

    /**
     * @return The list of raw byte arrays
     */
    public List<byte[]> getDataList() {
        return dataList;
    }

    /**
     * @return The storage keys as DataWord objects (padded to 32 bytes)
     */
    public List<DataWord> getAsDataWords() {
        List<DataWord> result = new ArrayList<>(dataList.size());
        for (byte[] data : dataList) {
            result.add(DataWord.valueOf(data));
        }
        return result;
    }

    /**
     * @return The number of elements in the array
     */
    public int size() {
        return dataList.size();
    }

    /**
     * @return true if the array is empty
     */
    public boolean isEmpty() {
        return dataList.isEmpty();
    }

    public static class Deserializer extends StdDeserializer<HexDataArrayParam> {
        @Serial
        private static final long serialVersionUID = 1L;

        public Deserializer() {
            this(null);
        }

        public Deserializer(Class<?> vc) {
            super(vc);
        }

        @Override
        public HexDataArrayParam deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
            if (jp.currentToken() != JsonToken.START_ARRAY) {
                throw RskJsonRpcRequestException.invalidParamError("Expected array of hex strings for storage keys");
            }

            List<String> hexStrings = new ArrayList<>();
            while (jp.nextToken() != JsonToken.END_ARRAY) {
                if (jp.currentToken() == JsonToken.VALUE_STRING) {
                    hexStrings.add(jp.getText());
                } else {
                    throw RskJsonRpcRequestException.invalidParamError(
                            "Expected hex string in storage keys array, got: " + jp.currentToken());
                }
            }

            return new HexDataArrayParam(hexStrings);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HexDataArrayParam that = (HexDataArrayParam) o;
        if (dataList.size() != that.dataList.size()) return false;
        for (int i = 0; i < dataList.size(); i++) {
            if (!Arrays.equals(dataList.get(i), that.dataList.get(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = 1;
        for (byte[] data : dataList) {
            result = 31 * result + Arrays.hashCode(data);
        }
        return result;
    }
}

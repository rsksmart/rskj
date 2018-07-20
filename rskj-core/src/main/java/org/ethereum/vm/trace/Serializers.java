/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package org.ethereum.vm.trace;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.introspect.VisibilityChecker;
import co.rsk.panic.PanicProcessor;
import org.ethereum.vm.DataWord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.bouncycastle.util.encoders.Hex;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class Serializers {

    private static final Logger LOGGER = LoggerFactory.getLogger("vmtrace");
    private static final PanicProcessor panicProcessor = new PanicProcessor();

    public static class DataWordSerializer extends JsonSerializer<DataWord> {

        @Override
        public void serialize(DataWord gas, JsonGenerator jgen, SerializerProvider provider) throws IOException, JsonProcessingException {
            jgen.writeString(gas.value().toString());
        }
    }

    public static class ByteArraySerializer extends JsonSerializer<byte[]> {

        @Override
        public void serialize(byte[] memory, JsonGenerator jgen, SerializerProvider provider) throws IOException, JsonProcessingException {
            jgen.writeString(Hex.toHexString(memory));
        }
    }

    public static class OpCodeSerializer extends JsonSerializer<Byte> {

        @Override
        public void serialize(Byte op, JsonGenerator jgen, SerializerProvider provider) throws IOException, JsonProcessingException {
            jgen.writeString(org.ethereum.vm.OpCode.code(op).name());
        }
    }


    public static String serializeFieldsOnly(Object value, boolean pretty) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            serializeFieldsOnly(value, pretty, baos);
            return baos.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            LOGGER.error("JSON serialization error: ", e);
            panicProcessor.panic("serialization", "JSON serialization error: " + e.toString());
            return "{}";
        }
    }

    public static void serializeFieldsOnly(Object value, boolean pretty, OutputStream out) throws IOException {
        ObjectMapper mapper = createMapper(pretty);
        mapper.setVisibility(fieldsOnlyVisibilityChecker(mapper));
        mapper.writeValue(out, value);
    }

    private static VisibilityChecker<?> fieldsOnlyVisibilityChecker(ObjectMapper mapper) {
        return mapper.getSerializationConfig().getDefaultVisibilityChecker()
                .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withIsGetterVisibility(JsonAutoDetect.Visibility.NONE);
    }

    public static ObjectMapper createMapper(boolean pretty) {
        ObjectMapper mapper = new ObjectMapper();
        if (pretty) {
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
        }
        return mapper;
    }
}

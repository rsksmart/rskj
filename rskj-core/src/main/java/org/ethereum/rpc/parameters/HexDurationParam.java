package org.ethereum.rpc.parameters;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.io.Serializable;

@JsonDeserialize(using = HexDurationParam.Deserializer.class)
public class HexDurationParam extends HexStringParam implements Serializable {
    private static final long serialVersionUID = 1L;

    private final Long duration;

    public HexDurationParam(String hexDurationStr) {
        super(hexDurationStr);

        this.duration = Long.parseLong(hexDurationStr.substring(2), 16);
    }

    public Long getDuration() {
        return duration;
    }

    public static class Deserializer extends StdDeserializer<HexDurationParam> {
        private static final long serialVersionUID = 1L;

        public Deserializer() { this(null); }

        public Deserializer(Class<?> vc) { super(vc); }

        @Override
        public HexDurationParam deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
            String hexDurationStr = jp.getText();
            return new HexDurationParam(hexDurationStr);
        }
    }
}

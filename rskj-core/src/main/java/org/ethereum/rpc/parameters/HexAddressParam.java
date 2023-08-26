package org.ethereum.rpc.parameters;

import co.rsk.core.RskAddress;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;

import java.io.IOException;
import java.io.Serializable;

@JsonDeserialize(using = HexAddressParam.Deserializer.class)
public class HexAddressParam implements Serializable {
    private static final long serialVersionUID = 1L;

    private final RskAddress address;

    public HexAddressParam(String hexAddress) {
        if (hexAddress == null || hexAddress.isEmpty()) {
            throw RskJsonRpcRequestException.invalidParamError("Invalid address: empty or null.");
        }

        try {
            this.address = new RskAddress(hexAddress);
        } catch (Exception e) {
            throw RskJsonRpcRequestException.invalidParamError("Invalid address format. " + e.getMessage());
        }
    }

    public RskAddress getAddress() {
        return address;
    }

    public static class Deserializer extends StdDeserializer<HexAddressParam> {
        private static final long serialVersionUID = 1L;

        public Deserializer() { this(null); }

        public Deserializer(Class<?> vc) { super(vc); }

        @Override
        public HexAddressParam deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
            String hexAddress = jp.getText();
            return new HexAddressParam(hexAddress);
        }
    }
}

package org.ethereum.rpc;

import java.util.Map;

public class BlockRef {
    private String identifier;
    private Map<String, String> inputs;

    public BlockRef(String identifier) {
        this.identifier = identifier;
    }

    public BlockRef(Map<String, String> inputs) {
        this.inputs = inputs;
    }

    public String getIdentifier() {
        return identifier;
    }

    public Map<String, String> getInputs() {
        return inputs;
    }
}

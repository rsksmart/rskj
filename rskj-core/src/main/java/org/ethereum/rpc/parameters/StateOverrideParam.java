package org.ethereum.rpc.parameters;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.HashMap;
import java.util.Map;

public class StateOverrideParam {
    private final Map<String, AccountOverrideParam> overrides;

    @JsonCreator
    public StateOverrideParam(Map<String, AccountOverrideParam> overrides) {
        this.overrides = new HashMap<>();
        overrides.forEach((addr, override) -> {
            this.overrides.put(addr, override);
        });
    }

    public Map<String, AccountOverrideParam> getOverrides() {
        return overrides;
    }
}

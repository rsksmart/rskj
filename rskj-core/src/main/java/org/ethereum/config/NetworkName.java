package org.ethereum.config;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public enum NetworkName {
    MAINNET("main"), TESTNET("testnet"), TESTNET2("testnet2"), DEVNET("devnet"), REGTEST("regtest");

    private final String name;
    private static final Map<String, NetworkName> NETWORK_NAME_MAP = buildNetworkNameMap();

    NetworkName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    private static Map<String, NetworkName> buildNetworkNameMap() {
        return Arrays.stream(NetworkName.values()).collect(Collectors.toMap(NetworkName::getName, n -> n));
    }

    public static NetworkName getByName(String name) {
        return NETWORK_NAME_MAP.get(name);
    }
}

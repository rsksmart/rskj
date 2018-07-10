package org.ethereum.config.blockchain;

import com.typesafe.config.Config;

public class HardForkActivationConfig {
    private final int firstFork;

    // TODO: define a proper name for this config setting
    private static final String PROPERTY_FIRST_FORK_NAME = "firstFork";

    public HardForkActivationConfig(Config config) {
        // If I don't have any config for firstFork I will set it to 0
        this.firstFork = config.hasPath(PROPERTY_FIRST_FORK_NAME) ? config.getInt(PROPERTY_FIRST_FORK_NAME) : 0;
    }

    public int getFirstForkActivationHeight() {
        return firstFork;
    }

}

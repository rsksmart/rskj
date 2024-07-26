package co.rsk.peg.lockingcap;

import co.rsk.core.RskAddress;

public enum LockingCapCaller {
    AUTHORIZED("a02db0ed94a5894bc6f9079bb9a2d93ada1917f3"),
    UNAUTHORIZED("e2a5070b4e2cb77fe22dff05d9dcdc4d3eaa6ead");

    private final String rskAddress;

    LockingCapCaller(String rskAddress) {
        this.rskAddress = rskAddress;
    }

    public RskAddress getRskAddress() {
        return new RskAddress(rskAddress);
    }
}

package co.rsk.peg.feeperkb;

import co.rsk.core.RskAddress;

/**
 * Created to makes easier the way to get RskAddress of 3 authorized and 1 unauthorized Callers. It
 * contains hex encoded string associated to each caller and return the RskAddress of each one.
 */
public enum FeePerKbVoteCaller {
    FIRST_AUTHORIZED("a02db0ed94a5894bc6f9079bb9a2d93ada1917f3"),
    SECOND_AUTHORIZED("180a7edda4e640ea5a3e495e17a1efad260c39e9"),
    THIRD_AUTHORIZED("8418edc8fea47183116b4c8cd6a12e51a7e169c1"),
    UNAUTHORIZED("e2a5070b4e2cb77fe22dff05d9dcdc4d3eaa6ead");

    private final String rskAddress;

    FeePerKbVoteCaller(String rskAddress) {
        this.rskAddress = rskAddress;
    }

    public RskAddress getRskAddress() {
        return new RskAddress(rskAddress);
    }
}

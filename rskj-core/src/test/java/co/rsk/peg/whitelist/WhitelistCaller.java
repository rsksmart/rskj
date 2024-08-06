package co.rsk.peg.whitelist;

import co.rsk.core.RskAddress;

public enum WhitelistCaller {
    AUTHORIZED("6ba9d41b07da470fe340cbd439a42538795eb75b"),
    UNAUTHORIZED("e2a5070b4e2cb77fe22dff05d9dcdc4d3eaa6ead");

    private final String rskAddress;

    WhitelistCaller(String rskAddress) {
        this.rskAddress = rskAddress;
    }

    public RskAddress getRskAddress() {
        return new RskAddress(rskAddress);
    }
}

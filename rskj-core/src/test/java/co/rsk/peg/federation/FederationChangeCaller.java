package co.rsk.peg.federation;

import co.rsk.core.RskAddress;

public enum FederationChangeCaller {

    FIRST_AUTHORIZED("56bc5087ac97bc85a877bd20dfef910b78b1dc5a"),
    SECOND_AUTHORIZED("6fe1cd06a80fa52eb34d7e4d1845881c2b5de89a"),
    THIRD_AUTHORIZED("d41f4d4c7fc8d5fa9468d0c466c9a726397f91b6"),
    UNAUTHORIZED("f49060c32d922fd7e3533aa434e4576ba411db0a");

    private final String rskAddress;

    FederationChangeCaller(String rskAddress) {
        this.rskAddress = rskAddress;
    }

    public RskAddress getRskAddress() {
        return new RskAddress(rskAddress);
    }

}

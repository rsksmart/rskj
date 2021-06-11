package co.rsk.pcc.altBN128.impls;

import co.rsk.altbn128.cloudflare.Utils;

public abstract class AbstractAltBN128 {
    public static final int PAIR_SIZE = 192;

    protected byte[] output;

    public static AbstractAltBN128 init() {
        if (Utils.isLinux()) {
            return new GoAltBN128();
        }
        return new JavaAltBN128();
    }

    protected AbstractAltBN128() {
    }

    public abstract int add(byte[] data, int length);

    public abstract int mul(byte[] data, int length);

    public abstract int pairing(byte[] data, int length);

    public byte[] getOutput() {
        return output.clone();
    }
}

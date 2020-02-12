package co.rsk.pcc.altBN128.impls;

import co.rsk.altbn128.cloudflare.JniBn128;

public class GoAltBN128 extends AbstractAltBN128 {
    @Override
    public int add(byte[] data, int length) {
        byte[] jniOutput = new byte[64];
        int res = new JniBn128().add(data, length, jniOutput);
        output = jniOutput;
        return res;
    }

    @Override
    public int mul(byte[] data, int length) {
        byte[] jniOutput = new byte[64];
        int res = new JniBn128().mul(data, length, jniOutput);
        output = jniOutput;
        return res;
    }

    @Override
    public int pairing(byte[] data, int length) {
        byte[] jniOutput = new byte[32];
        int res = new JniBn128().pairing(data, length, jniOutput);
        output = jniOutput;
        return res;
    }
}

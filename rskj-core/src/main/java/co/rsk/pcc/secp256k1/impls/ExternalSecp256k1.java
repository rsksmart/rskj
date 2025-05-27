package co.rsk.pcc.secp256k1.impls;

import co.rsk.altbn128.cloudflare.JniBn128;

public class ExternalSecp256k1 extends AbstractSecp256k1 {

    public static Throwable getLoadError() {
        return null;
    }

    @Override
    public int add(byte[] data, int length) {
        byte[] jniOutput = new byte[64];
        int res = 0;
        output = jniOutput;
        return res;
    }

    @Override
    public int mul(byte[] data, int length) {
        byte[] jniOutput = new byte[64];
        int res = 0;
        output = jniOutput;
        return res;
    }

}

package co.rsk.pcc.altBN128.impls;

public abstract class AbstractAltBN128 {
    protected byte[] output;

    public abstract int add(byte[] data, int length);

    public abstract int mul(byte[] data, int length);

    public abstract int pairing(byte[] data, int length);

    public byte[] getOutput() {
        return output.clone();
    }
}

package co.rsk.pcc.altBN128;

import co.rsk.altbn128.cloudflare.Utils;
import co.rsk.pcc.altBN128.impls.AbstractAltBN128;
import co.rsk.pcc.altBN128.impls.GoAltBN128;
import co.rsk.pcc.altBN128.impls.JavaAltBN128;
import co.rsk.pcc.altBN128.impls.JavaRSKIP197AltBN128;

public class AltBN128 extends AbstractAltBN128 {
    private AbstractAltBN128 implementation;

    public AltBN128(boolean rskip197active) {
        if (Utils.isLinux()) {
            this.implementation = new GoAltBN128();
        } else {
            if(rskip197active) {
                this.implementation = new JavaRSKIP197AltBN128();
            } else {
                this.implementation = new JavaAltBN128();
            }
        }
    }
    @Override
    public int add(byte[] data, int length) {
        return implementation.add(data,length);
    }

    @Override
    public int mul(byte[] data, int length) {
        return implementation.mul(data,length);
    }

    @Override
    public int pairing(byte[] data, int length) {
        return implementation.pairing(data,length);
    }

    @Override
    public byte[] getOutput() {
        return implementation.getOutput();
    }
}

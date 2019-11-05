package com.mraof.rsk.bench;

import org.ethereum.crypto.altbn128.java.*;
import org.openjdk.jmh.annotations.Benchmark;

import java.math.BigInteger;

/**
 * Created by mraof on 2019 October 28 at 4:48 PM.
 */
public class BenchmarkJavaBn128 {
    private static final BigInteger ai = new BigInteger("12345678900987654321");
    private static final BigInteger di = new BigInteger("88888888");
    private static final BigInteger ci = new BigInteger("17");
    private static final BigInteger bi = new BigInteger("3274729875019571785607814704813545620783586");

    @Benchmark
    public void javaFp() {
        Fp a = new Fp(ai);
        Fp b = new Fp(bi);
        a.add(b).mul(b).dbl().sub(b).negate().add(b).negate().mul(a).dbl().inverse().squared();
        b.add(a).mul(a).dbl().sub(a).negate().add(a).negate().mul(b).dbl().inverse().squared();
        a.squared().inverse().dbl().mul(a).negate().add(b).negate().sub(b).dbl().mul(b).add(b);
        b.squared().inverse().dbl().mul(b).negate().add(a).negate().sub(a).dbl().mul(a).add(a);
    }

    @Benchmark
    public void javaFp2() {
        Fp2 a = new Fp2(ai, bi);
        Fp2 b = new Fp2(ci, di);
        a.add(b).mul(b).dbl().sub(b).negate().add(b).negate().mul(a).dbl().inverse().squared();
        b.add(a).mul(a).dbl().sub(a).negate().add(a).negate().mul(b).dbl().inverse().squared();
        a.squared().inverse().dbl().mul(a).negate().add(b).negate().sub(b).dbl().mul(b).add(b);
        b.squared().inverse().dbl().mul(b).negate().add(a).negate().sub(a).dbl().mul(a).add(a);
    }

    @Benchmark
    public void javaG1() {
        BN128 a = BN128G1.create(new byte[]{1}, new byte[]{2});
        BN128 b = BN128G1.create(new byte[]{1}, new byte[]{2});
        BN128 c = BN128G1.create(new byte[]{1}, new byte[]{2});
        a.mul(ci).add(a).mul(ai).add(c).mul(di).add(b).toAffine().mul(ci).add(c.toAffine());
        b.mul(di).add(b).mul(bi).add(a).mul(ci).add(c).toAffine().mul(ai).add(a.toAffine());
        c.mul(ai).add(c).mul(ci).add(b).mul(bi).add(a).toAffine().mul(di).add(b.toAffine());
    }

    @Benchmark
    public void javaG2() {
        BN128 a = new BN128G2(Fp2._1, Fp2._1.dbl(), Fp2._1);
        BN128 b = new BN128G2(Fp2._1, Fp2._1.dbl(), Fp2._1);
        BN128 c = new BN128G2(Fp2._1, Fp2._1.dbl(), Fp2._1);
        a.mul(ci).add(a).mul(ai).add(c).mul(di).add(b).toAffine().mul(ci).add(c.toAffine());
        b.mul(di).add(b).mul(bi).add(a).mul(ci).add(c).toAffine().mul(ai).add(a.toAffine());
        c.mul(ai).add(c).mul(ci).add(b).mul(bi).add(a).toAffine().mul(di).add(b.toAffine());
    }

    @Benchmark
    public void javaPairing() {
        BN128G1 g1 = BN128G1.create(new byte[]{1}, new byte[]{2});
        BN128G2 g2 = new BN128G2(Fp2._1, Fp2._1.dbl(), Fp2._1);
        PairingCheck check = PairingCheck.create();
        check.addPair(g1, g2);
        check.addPair(g1, g2);
        check.addPair(g1, g2);
        check.addPair(g1, g2);
        check.addPair(g1, g2);
        check.addPair(g1, g2);
        check.run();
        check.result();
    }
}

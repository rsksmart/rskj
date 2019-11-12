use jni::objects::{JClass, JObject};
use jni::sys::jlong;
use jni::JNIEnv;
use parity_bn::arith::U256;
use parity_bn::{Fq, Fq2};

#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_org_ethereum_crypto_altbn128_Fp2_nadd(
    env: JNIEnv,
    _class: JClass,
    la0: jlong,
    la1: jlong,
    la2: jlong,
    la3: jlong,
    lb0: jlong,
    lb1: jlong,
    lb2: jlong,
    lb3: jlong,
    ra0: jlong,
    ra1: jlong,
    ra2: jlong,
    ra3: jlong,
    rb0: jlong,
    rb1: jlong,
    rb2: jlong,
    rb3: jlong,
    ret: JObject,
) {
    let left = fq2_from_jlongs([la0, la1, la2, la3, lb0, lb1, lb2, lb3]);
    let right = fq2_from_jlongs([ra0, ra1, ra2, ra3, rb0, rb1, rb2, rb3]);
    let fq2 = left + right;
    fq2_return(env, fq2, ret)
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_org_ethereum_crypto_altbn128_Fp2_nmul(
    env: JNIEnv,
    _class: JClass,
    la0: jlong,
    la1: jlong,
    la2: jlong,
    la3: jlong,
    lb0: jlong,
    lb1: jlong,
    lb2: jlong,
    lb3: jlong,
    ra0: jlong,
    ra1: jlong,
    ra2: jlong,
    ra3: jlong,
    rb0: jlong,
    rb1: jlong,
    rb2: jlong,
    rb3: jlong,
    ret: JObject,
) {
    let left = fq2_from_jlongs([la0, la1, la2, la3, lb0, lb1, lb2, lb3]);
    let right = fq2_from_jlongs([ra0, ra1, ra2, ra3, rb0, rb1, rb2, rb3]);
    let fq2 = left * right;
    fq2_return(env, fq2, ret)
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_org_ethereum_crypto_altbn128_Fp2_nsub(
    env: JNIEnv,
    _class: JClass,
    la0: jlong,
    la1: jlong,
    la2: jlong,
    la3: jlong,
    lb0: jlong,
    lb1: jlong,
    lb2: jlong,
    lb3: jlong,
    ra0: jlong,
    ra1: jlong,
    ra2: jlong,
    ra3: jlong,
    rb0: jlong,
    rb1: jlong,
    rb2: jlong,
    rb3: jlong,
    ret: JObject,
) {
    let left = fq2_from_jlongs([la0, la1, la2, la3, lb0, lb1, lb2, lb3]);
    let right = fq2_from_jlongs([ra0, ra1, ra2, ra3, rb0, rb1, rb2, rb3]);
    let fq2 = left - right;
    fq2_return(env, fq2, ret)
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_org_ethereum_crypto_altbn128_Fp2_nsquared(
    env: JNIEnv,
    _class: JClass,
    a0: jlong,
    a1: jlong,
    a2: jlong,
    a3: jlong,
    b0: jlong,
    b1: jlong,
    b2: jlong,
    b3: jlong,
    ret: JObject,
) {
    let fq2 = fq2_from_jlongs([a0, a1, a2, a3, b0, b1, b2, b3]);
    fq2_return(env, fq2_squared(fq2), ret)
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_org_ethereum_crypto_altbn128_Fp2_ndbl(
    env: JNIEnv,
    _class: JClass,
    a0: jlong,
    a1: jlong,
    a2: jlong,
    a3: jlong,
    b0: jlong,
    b1: jlong,
    b2: jlong,
    b3: jlong,
    ret: JObject,
) {
    let fq2 = fq2_from_jlongs([a0, a1, a2, a3, b0, b1, b2, b3]);
    fq2_return(env, fq2 + fq2, ret)
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_org_ethereum_crypto_altbn128_Fp2_ninverse(
    env: JNIEnv,
    _class: JClass,
    a0: jlong,
    a1: jlong,
    a2: jlong,
    a3: jlong,
    b0: jlong,
    b1: jlong,
    b2: jlong,
    b3: jlong,
    ret: JObject,
) {
    let fq2 = fq2_from_jlongs([a0, a1, a2, a3, b0, b1, b2, b3]);
    if let Some(inverse_fq2) = fq2_inverse(fq2) {
        fq2_return(env, inverse_fq2, ret)
    }
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_org_ethereum_crypto_altbn128_Fp2_nnegate(
    env: JNIEnv,
    _class: JClass,
    a0: jlong,
    a1: jlong,
    a2: jlong,
    a3: jlong,
    b0: jlong,
    b1: jlong,
    b2: jlong,
    b3: jlong,
    ret: JObject,
) {
    let fq2 = fq2_from_jlongs([a0, a1, a2, a3, b0, b1, b2, b3]);
    fq2_return(env, -fq2, ret)
}

#[inline]
pub(crate) fn fq2_from_jlongs(buf: [jlong; 8]) -> Fq2 {
    unsafe { std::ptr::read(buf.as_ptr() as *const _) }
}

#[inline]
fn fq2_return(env: JNIEnv, fq2: Fq2, ret: JObject) {
    let fq_bytes: [jlong; 8] = unsafe { std::mem::transmute(fq2) };
    env.set_long_array_region(ret.into_inner(), 0, &fq_bytes)
        .expect("Unable to set return array for fq2");
}

// Stuff to work around certain functions of Fq2 not being public
#[inline]
pub fn const_fq(i: [u64; 4]) -> Fq {
    let n = U256::from(i);
    unsafe { std::mem::transmute(n) }
}

#[inline]
fn fq_non_residue() -> Fq {
    // (q - 1) is a quadratic nonresidue in Fq
    // 21888242871839275222246405745257275088696311157297823662689037894645226208582
    const_fq([
        0x68c3488912edefaa,
        0x8d087f6872aabf4f,
        0x51e1a24709081231,
        0x2259d6b14729c0fa,
    ])
}

fn fq2_squared(fq2: Fq2) -> Fq2 {
    let (c0, c1): (Fq, Fq) = unsafe { std::mem::transmute(fq2) };
    let ab = c0 * c1;
    Fq2::new(
        (c1 * fq_non_residue() + c0) * (c0 + c1) - ab - (ab * fq_non_residue()),
        ab + ab,
    )
}

fn fq2_inverse(fq2: Fq2) -> Option<Fq2> {
    let (c0, c1): (Fq, Fq) = unsafe { std::mem::transmute(fq2) };
    match ((c0 * c0) - (c1 * c1 * fq_non_residue())).inverse() {
        Some(t) => Some(Fq2::new(c0 * t, -(c1 * t))),
        None => None,
    }
}

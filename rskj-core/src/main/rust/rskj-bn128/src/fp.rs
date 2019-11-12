use jni::objects::{JClass, JObject};
use jni::JNIEnv;
use jni::sys::{jboolean, jlong, jbyte};
use parity_bn::Fq;

#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_org_ethereum_crypto_altbn128_Fp_newFq(
    env: JNIEnv,
    _class: JClass,
    bytes: JObject,
    ret: JObject,
) -> jboolean {
    let vec: Vec<u8> = env
        .convert_byte_array(bytes.into_inner())
        .expect("Unable to read byte array");
    let mut byte_array = [0; 32];
    byte_array[(32 - vec.len())..].copy_from_slice(&vec);
    if let Ok(fq) = Fq::from_slice(&byte_array) {
        fq_return(env, fq, ret);
        true as jboolean
    } else {
        false as jboolean
    }
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_org_ethereum_crypto_altbn128_Fp_nadd(
    env: JNIEnv,
    _class: JClass,
    la: jlong,
    lb: jlong,
    lc: jlong,
    ld: jlong,
    ra: jlong,
    rb: jlong,
    rc: jlong,
    rd: jlong,
    ret: JObject,
) {
    let left = fq_from_jlongs([la, lb, lc, ld]);
    let right = fq_from_jlongs([ra, rb, rc, rd]);
    let fq = left + right;
    fq_return(env, fq, ret)
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_org_ethereum_crypto_altbn128_Fp_nmul(
    env: JNIEnv,
    _class: JClass,
    la: jlong,
    lb: jlong,
    lc: jlong,
    ld: jlong,
    ra: jlong,
    rb: jlong,
    rc: jlong,
    rd: jlong,
    ret: JObject,
) {
    let left = fq_from_jlongs([la, lb, lc, ld]);
    let right = fq_from_jlongs([ra, rb, rc, rd]);
    let fq = left * right;
    fq_return(env, fq, ret)
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_org_ethereum_crypto_altbn128_Fp_nsub(
    env: JNIEnv,
    _class: JClass,
    la: jlong,
    lb: jlong,
    lc: jlong,
    ld: jlong,
    ra: jlong,
    rb: jlong,
    rc: jlong,
    rd: jlong,
    ret: JObject,
) {
    let left = fq_from_jlongs([la, lb, lc, ld]);
    let right = fq_from_jlongs([ra, rb, rc, rd]);
    let fq = left - right;
    fq_return(env, fq, ret)
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_org_ethereum_crypto_altbn128_Fp_nsquared(
    env: JNIEnv,
    _class: JClass,
    a: jlong,
    b: jlong,
    c: jlong,
    d: jlong,
    ret: JObject,
) {
    let fq = fq_from_jlongs([a, b, c, d]);
    fq_return(env, fq * fq, ret)
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_org_ethereum_crypto_altbn128_Fp_ndbl(
    env: JNIEnv,
    _class: JClass,
    a: jlong,
    b: jlong,
    c: jlong,
    d: jlong,
    ret: JObject,
) {
    let fq = fq_from_jlongs([a, b, c, d]);
    fq_return(env, fq + fq, ret)
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_org_ethereum_crypto_altbn128_Fp_ninverse(
    env: JNIEnv,
    _class: JClass,
    a: jlong,
    b: jlong,
    c: jlong,
    d: jlong,
    ret: JObject,
) {
    if let Some(fq) = fq_from_jlongs([a, b, c, d]).inverse() {
        fq_return(env, fq, ret)
    }
}


#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_org_ethereum_crypto_altbn128_Fp_nnegate(
    env: JNIEnv,
    _class: JClass,
    a: jlong,
    b: jlong,
    c: jlong,
    d: jlong,
    ret: JObject,
) {
    let fq = -fq_from_jlongs([a, b, c, d]);
    fq_return(env, fq, ret)
}


#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_org_ethereum_crypto_altbn128_Fp_nbytes(
    env: JNIEnv,
    _class: JClass,
    a: jlong,
    b: jlong,
    c: jlong,
    d: jlong,
    ret: JObject,
) {
    let fq = fq_from_jlongs([a, b, c, d]);
    let mut bytes: [u8; 32] = [0; 32];
    fq.to_big_endian(&mut bytes).unwrap();
    let fq_bytes: [jbyte; 32] = unsafe { std::mem::transmute(bytes) };
    env.set_byte_array_region(ret.into_inner(), 0, &fq_bytes)
        .expect("Unable to set return array for fq");
}


#[inline]
pub(crate) fn fq_from_jlongs(buf: [jlong; 4]) -> Fq {
        unsafe { std::ptr::read(buf.as_ptr() as *const _) }
}

#[inline]
fn fq_return(env: JNIEnv, fq: Fq, ret: JObject) {
    let fq_bytes: [jlong; 4] = unsafe { std::mem::transmute(fq) };
    env.set_long_array_region(ret.into_inner(), 0, &fq_bytes)
        .expect("Unable to set return array for fq");
}

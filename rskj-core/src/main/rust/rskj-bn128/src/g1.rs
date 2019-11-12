use jni::objects::{JClass, JObject};
use jni::sys::{jlong, jboolean};
use jni::JNIEnv;
use parity_bn::{AffineG1, Fr, G1, Group};
use crate::fp::fq_from_jlongs;


#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_org_ethereum_crypto_altbn128_BN128G1_onCurve(
    _env: JNIEnv,
    _class: JClass,
    x0: jlong,
    x1: jlong,
    x2: jlong,
    x3: jlong,
    y0: jlong,
    y1: jlong,
    y2: jlong,
    y3: jlong,
) -> jboolean {
    let x = fq_from_jlongs([x0, x1, x2, x3]);
    let y = fq_from_jlongs([y0, y1, y2, y3]);
    AffineG1::new(x, y).is_ok() as jboolean
}


#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_org_ethereum_crypto_altbn128_BN128G1_affine(
    env: JNIEnv,
    _class: JClass,
    x0: jlong,
    x1: jlong,
    x2: jlong,
    x3: jlong,
    y0: jlong,
    y1: jlong,
    y2: jlong,
    y3: jlong,
    z0: jlong,
    z1: jlong,
    z2: jlong,
    z3: jlong,
    ret: JObject,
) {
    let g1 = g1_from_jlongs([x0, x1, x2, x3, y0, y1, y2, y3, z0, z1, z2, z3]);
    if let Some(affine) = AffineG1::from_jacobian(g1) {
        affine_g1_return(env, affine, ret)
    }
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_org_ethereum_crypto_altbn128_BN128G1_nadd(
    env: JNIEnv,
    _class: JClass,
    lx0: jlong,
    lx1: jlong,
    lx2: jlong,
    lx3: jlong,
    ly0: jlong,
    ly1: jlong,
    ly2: jlong,
    ly3: jlong,
    lz0: jlong,
    lz1: jlong,
    lz2: jlong,
    lz3: jlong,
    rx0: jlong,
    rx1: jlong,
    rx2: jlong,
    rx3: jlong,
    ry0: jlong,
    ry1: jlong,
    ry2: jlong,
    ry3: jlong,
    rz0: jlong,
    rz1: jlong,
    rz2: jlong,
    rz3: jlong,
    ret: JObject,
) {
    let left_g1 = g1_from_jlongs([lx0, lx1, lx2, lx3, ly0, ly1, ly2, ly3, lz0, lz1, lz2, lz3]);
    let right_g1 = g1_from_jlongs([rx0, rx1, rx2, rx3, ry0, ry1, ry2, ry3, rz0, rz1, rz2, rz3]);
    g1_return(env, left_g1 + right_g1, ret)
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_org_ethereum_crypto_altbn128_BN128G1_nmul(
    env: JNIEnv,
    _class: JClass,
    lx0: jlong,
    lx1: jlong,
    lx2: jlong,
    lx3: jlong,
    ly0: jlong,
    ly1: jlong,
    ly2: jlong,
    ly3: jlong,
    lz0: jlong,
    lz1: jlong,
    lz2: jlong,
    lz3: jlong,
    bytes: JObject,
    ret: JObject,
) {
    let vec: Vec<u8> = env
        .convert_byte_array(bytes.into_inner())
        .expect("Unable to read byte array");
    let mut byte_array = [0; 32];
    //BigInteger can add a leading 0 for 256 bit numbers sometimes
    if vec.len() == 33 && vec[0] == 0 {
        byte_array[..].copy_from_slice(&vec[1..]);
    } else {
        byte_array[(32 - vec.len())..].copy_from_slice(&vec);
    }
    let left_g1 = g1_from_jlongs([lx0, lx1, lx2, lx3, ly0, ly1, ly2, ly3, lz0, lz1, lz2, lz3]);
    let right = Fr::from_slice(&byte_array).expect("Unable to create Fr from slice");
    g1_return(env, left_g1 * right, ret)
}

#[inline]
pub(crate) fn g1_from_jlongs(buf: [jlong; 12]) -> G1 {
    unsafe { std::ptr::read(buf.as_ptr() as *const _) }
}

#[inline]
fn g1_return(env: JNIEnv, g1: G1, ret: JObject) {
    let g1_bytes: [jlong; 12] = unsafe { std::mem::transmute(g1) };
    env.set_long_array_region(ret.into_inner(), 0, &g1_bytes)
        .expect("Unable to set return array for g1");
}

#[inline]
fn affine_g1_return(env: JNIEnv, affine: AffineG1, ret: JObject) {
    let g1_bytes: [jlong; 8] = unsafe { std::mem::transmute(affine) };
    env.set_long_array_region(ret.into_inner(), 0, &g1_bytes)
        .expect("Unable to set return array for affine g1");
}

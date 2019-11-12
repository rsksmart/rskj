use jni::objects::{JClass, JObject};
use jni::sys::{jlong, jboolean};
use jni::JNIEnv;
use parity_bn::{AffineG2, Fr, G2};
use crate::fp2::fq2_from_jlongs;


#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_org_ethereum_crypto_altbn128_BN128G2_onCurve(
    _env: JNIEnv,
    _class: JClass,
    xa0: jlong,
    xa1: jlong,
    xa2: jlong,
    xa3: jlong,
    xb0: jlong,
    xb1: jlong,
    xb2: jlong,
    xb3: jlong,
    ya0: jlong,
    ya1: jlong,
    ya2: jlong,
    ya3: jlong,
    yb0: jlong,
    yb1: jlong,
    yb2: jlong,
    yb3: jlong,
) -> jboolean {
    let x = fq2_from_jlongs([xa0, xa1, xa2, xa3, xb0, xb1, xb2, xb3]);
    let y = fq2_from_jlongs([ya0, ya1, ya2, ya3, yb0, yb1, yb2, yb3]);
    AffineG2::new(x, y).is_ok() as jboolean
}


#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_org_ethereum_crypto_altbn128_BN128G2_affine(
    env: JNIEnv,
    _class: JClass,
    xa0: jlong,
    xa1: jlong,
    xa2: jlong,
    xa3: jlong,
    xb0: jlong,
    xb1: jlong,
    xb2: jlong,
    xb3: jlong,
    ya0: jlong,
    ya1: jlong,
    ya2: jlong,
    ya3: jlong,
    yb0: jlong,
    yb1: jlong,
    yb2: jlong,
    yb3: jlong,
    za0: jlong,
    za1: jlong,
    za2: jlong,
    za3: jlong,
    zb0: jlong,
    zb1: jlong,
    zb2: jlong,
    zb3: jlong,
    ret: JObject,
) {
    let g2 = g2_from_jlongs([xa0, xa1, xa2, xa3, xb0, xb1, xb2, xb3, ya0, ya1, ya2, ya3, yb0, yb1, yb2, yb3, za0, za1, za2, za3, zb0, zb1, zb2, zb3]);
    if let Some(affine) = AffineG2::from_jacobian(g2) {
        affine_g2_return(env, affine, ret)
    }
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_org_ethereum_crypto_altbn128_BN128G2_nadd(
    env: JNIEnv,
    _class: JClass,
    lxa0: jlong,
    lxa1: jlong,
    lxa2: jlong,
    lxa3: jlong,
    lxb0: jlong,
    lxb1: jlong,
    lxb2: jlong,
    lxb3: jlong,
    lya0: jlong,
    lya1: jlong,
    lya2: jlong,
    lya3: jlong,
    lyb0: jlong,
    lyb1: jlong,
    lyb2: jlong,
    lyb3: jlong,
    lza0: jlong,
    lza1: jlong,
    lza2: jlong,
    lza3: jlong,
    lzb0: jlong,
    lzb1: jlong,
    lzb2: jlong,
    lzb3: jlong,
    rxa0: jlong,
    rxa1: jlong,
    rxa2: jlong,
    rxa3: jlong,
    rxb0: jlong,
    rxb1: jlong,
    rxb2: jlong,
    rxb3: jlong,
    rya0: jlong,
    rya1: jlong,
    rya2: jlong,
    rya3: jlong,
    ryb0: jlong,
    ryb1: jlong,
    ryb2: jlong,
    ryb3: jlong,
    rza0: jlong,
    rza1: jlong,
    rza2: jlong,
    rza3: jlong,
    rzb0: jlong,
    rzb1: jlong,
    rzb2: jlong,
    rzb3: jlong,
    ret: JObject,
) {
    let left_g2 = g2_from_jlongs([lxa0, lxa1, lxa2, lxa3, lxb0, lxb1, lxb2, lxb3, lya0, lya1, lya2, lya3, lyb0, lyb1, lyb2, lyb3, lza0, lza1, lza2, lza3, lzb0, lzb1, lzb2, lzb3]);
    let right_g2 = g2_from_jlongs([rxa0, rxa1, rxa2, rxa3, rxb0, rxb1, rxb2, rxb3, rya0, rya1, rya2, rya3, ryb0, ryb1, ryb2, ryb3, rza0, rza1, rza2, rza3, rzb0, rzb1, rzb2, rzb3]);
    g2_return(env, left_g2 + right_g2, ret)
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_org_ethereum_crypto_altbn128_BN128G2_nmul(
    env: JNIEnv,
    _class: JClass,
    lxa0: jlong,
    lxa1: jlong,
    lxa2: jlong,
    lxa3: jlong,
    lxb0: jlong,
    lxb1: jlong,
    lxb2: jlong,
    lxb3: jlong,
    lya0: jlong,
    lya1: jlong,
    lya2: jlong,
    lya3: jlong,
    lyb0: jlong,
    lyb1: jlong,
    lyb2: jlong,
    lyb3: jlong,
    lza0: jlong,
    lza1: jlong,
    lza2: jlong,
    lza3: jlong,
    lzb0: jlong,
    lzb1: jlong,
    lzb2: jlong,
    lzb3: jlong,
    bytes: JObject,
    ret: JObject,
) {
    let vec: Vec<u8> = env
        .convert_byte_array(bytes.into_inner())
        .expect("Unable to read byte array");
    let mut byte_array = [0; 32];
    byte_array[(32 - vec.len())..].copy_from_slice(&vec);
    let left_g2 = g2_from_jlongs([lxa0, lxa1, lxa2, lxa3, lxb0, lxb1, lxb2, lxb3, lya0, lya1, lya2, lya3, lyb0, lyb1, lyb2, lyb3, lza0, lza1, lza2, lza3, lzb0, lzb1, lzb2, lzb3]);
    let right = Fr::from_slice(&byte_array).expect("Unable to create Fr from slice");
    g2_return(env, left_g2 * right, ret)
}

#[inline]
pub(crate) fn g2_from_jlongs(buf: [jlong; 24]) -> G2 {
    unsafe { std::ptr::read(buf.as_ptr() as *const _) }
}

#[inline]
fn g2_return(env: JNIEnv, g2: G2, ret: JObject) {
    let g2_bytes: [jlong; 24] = unsafe { std::mem::transmute(g2) };
    env.set_long_array_region(ret.into_inner(), 0, &g2_bytes)
        .expect("Unable to set return array for g2");
}

#[inline]
fn affine_g2_return(env: JNIEnv, affine: AffineG2, ret: JObject) {
    let g2_bytes: [jlong; 16] = unsafe { std::mem::transmute(affine) };
    env.set_long_array_region(ret.into_inner(), 0, &g2_bytes)
        .expect("Unable to set return array for affine g1");
}

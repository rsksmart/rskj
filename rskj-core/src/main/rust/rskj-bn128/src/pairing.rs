use jni::JNIEnv;
use jni::objects::{JClass, JObject};
use jni::sys::jboolean;
use parity_bn::{Gt, pairing};

use crate::g1::g1_from_jlongs;
use crate::g2::g2_from_jlongs;

#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_org_ethereum_crypto_altbn128_PairingCheck_nrun(
    env: JNIEnv,
    _class: JClass,
    data: JObject,
) -> jboolean {
    let len = env.get_array_length(data.into_inner()).expect("Unable to get array length") as usize;
    let mut longs = vec![0; len];
    env.get_long_array_region(data.into_inner(), 0, &mut longs).expect("Failed to get array");

    let one = Gt::one();
    let mut product = Gt::one();

    for i in 0..(len / 36) {
        let o = i * 36;
        let mut g1_data = [0; 12];
        let mut g2_data = [0; 24];
        g1_data.copy_from_slice(&longs[o..(o + 12)]);
        g2_data.copy_from_slice(&longs[(o + 12)..(o + 36)]);
        let g1 = g1_from_jlongs(g1_data);
        let g2 = g2_from_jlongs(g2_data);

        let gt = pairing(g1, g2);

        if gt != one {
            product = product * gt;
        }
    }

    (product == one) as jboolean
}

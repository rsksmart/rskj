#include <jni.h>
#include <stdio.h>
#include "keccak/sph_keccak.h"

JNIEXPORT void JNICALL
Java_org_ethereum_crypto_cryptohash_KeccakNative_keccak256(JNIEnv *env, jobject obj, jbyteArray input, jint start, jint len, jbyteArray output)
{
    jbyte *cData = (*env)->GetByteArrayElements(env, input, NULL);
    char dest[32];
    sph_keccak256_context keccak_context;
    sph_keccak256_init(&keccak_context);
    sph_keccak256(&keccak_context, cData + start, (size_t)len);
    sph_keccak256_close(&keccak_context, dest);
    (*env)->SetByteArrayRegion(env, output, 0, 32, dest);
    (*env)->ReleaseByteArrayElements(env, input, cData, JNI_ABORT);
    return;
}
#include <jni.h>       // JNI header provided by JDK
#include "org_tron_common_zksnark_LibrustzcashJni.h"
#include "librustzcash.h"

using namespace std;


JNIEXPORT jboolean JNICALL Java_org_tron_common_zksnark_LibrustzcashJni_librustzcashCheckDiversifier
(JNIEnv * env, jobject, jbyteArray arr) {
    jbyte* b = (env)->GetByteArrayElements(arr, nullptr);
    if (librustzcash_check_diversifier((const unsigned char *) b)) {
        return JNI_TRUE;
    } else {
        return JNI_FALSE;
    }
}


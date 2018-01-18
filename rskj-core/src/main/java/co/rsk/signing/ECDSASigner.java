package co.rsk.signing;

import java.io.FileNotFoundException;

/**
 * Implementors of this know how to sign
 * chunks of data with a certain ECDSA key given a
 * specific key identification and a valid
 * authorization.
 *
 * @author Ariel Mendelzon
 */
public interface ECDSASigner {
    boolean canSignWith(KeyId keyId);

    ECDSASignature sign(KeyId keyId, Message message, SignAuthorization signAuthorization) throws FileNotFoundException;
}

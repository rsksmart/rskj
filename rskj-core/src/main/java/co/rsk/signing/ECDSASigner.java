package co.rsk.signing;

import java.io.IOException;
import java.util.List;

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

    ECDSASignerCheckResult check();

    ECDSASignature sign(KeyId keyId, Message message, SignAuthorization signAuthorization) throws IOException;

    class ECDSASignerCheckResult {
        private boolean success;
        private List<String> messages;

        public ECDSASignerCheckResult(List<String> messages) {
            this.success = messages.isEmpty();
            this.messages = messages;
        }

        public boolean wasSuccessful() {
            return success;
        }

        public List<String> getMessages() {
            return messages;
        }
    }
}

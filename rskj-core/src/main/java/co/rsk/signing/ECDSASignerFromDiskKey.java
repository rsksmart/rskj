package co.rsk.signing;

import org.ethereum.crypto.ECKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.util.List;

/**
 * Signer that reads key from disk.
 *
 * Key is identified by a key id.
 *
 * Has certain requirements on filesystem permissions
 * of the file.
 *
 * No special authorization is required to sign a message.
 *
 * @author Ariel Mendelzon
 */
public class ECDSASignerFromDiskKey implements ECDSASigner {
    private static final Logger logger = LoggerFactory.getLogger("ECDSASignerFromDiskKey");

    private KeyId keyId;
    private String keyPath;

    public ECDSASignerFromDiskKey(KeyId keyId, String keyPath) {
        this.keyId = keyId;
        this.keyPath = keyPath;
    }

    @Override
    public boolean canSignWith(KeyId keyId) {
        return this.keyId.equals(keyId);
    }

    @Override
    public ECDSASignerCheckResult check() {
        KeyFileChecker checker = new KeyFileChecker(keyPath);

        List<String> messages = checker.check();

        return new ECDSASignerCheckResult(messages);
    }

    @Override
    public ECDSASignature sign(KeyId keyId, Message message, SignAuthorization signAuthorization) throws FileNotFoundException {
        if (!canSignWith(keyId)) {
            logger.error("Can't sign with that key id. Requested {}", keyId);
            throw new IllegalArgumentException(String.format("Can't sign with the requested signing key: %s", keyId));
        }

        // Read the key from disk
        // We use Ethereum's ECKey for this and subsequent signing
        // since it's good enough and there's no loss of generality
        ECKey key = ECKey.fromPrivate(new KeyFileHandler(keyPath).privateKey());

        // Sign (no authorization is needed) and return the wrapped signature.
        return ECDSASignature.fromEthSignature(key.sign(message.getBytes()));
    }
}

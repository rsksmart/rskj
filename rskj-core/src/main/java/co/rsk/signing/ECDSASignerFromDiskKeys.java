package co.rsk.signing;

import org.apache.commons.lang3.StringUtils;
import org.ethereum.crypto.ECKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Signer that reads key(s) from disk.
 *
 * Key(s) are identified by a plain string.
 *
 * Has certain requirements on filesystem permissions
 * of the file(s).
 *
 * No special authorization is required to sign a message.
 *
 * @author Ariel Mendelzon
 */
public class ECDSASignerFromDiskKeys implements ECDSASigner {
    private static final Logger logger = LoggerFactory.getLogger("ECDSASignerFromDiskKeys");

    private Map<String, String> keyMappings;

    public ECDSASignerFromDiskKeys() {
        keyMappings = new HashMap<>();
    }

    public void addMapping(String id, String keyPath) {
        keyMappings.put(id, keyPath);
    }

    @Override
    public boolean canSignWith(KeyId keyId) {
        if (!keyId.isPlainId()) {
            return false;
        }

        return keyMappings.containsKey(keyId.getPlainId());
    }

    @Override
    public ECDSASignature sign(KeyId keyId, Message message, SignAuthorization signAuthorization) throws FileNotFoundException {
        if (!keyId.isPlainId()) {
            logger.error("Illegal key identifier given. Expected a PlainKeyId but got a {}", keyId.getClass());
            throw new IllegalArgumentException("Must provide a plain key identifier");
        }

        String id = keyId.getPlainId();
        if (!keyMappings.containsKey(id)) {
            logger.error("Signing key not found. Requested {}", id);
            throw new IllegalArgumentException(String.format("Requested signing key not found: %s", id));
        }

        // Read the key from disk
        // We use Ethereum's ECKey for this and subsequent signing
        // since it's good enough and there's no loss of generality
        ECKey key = getKeyBytes(keyMappings.get(id));

        // Sign (no authorization is needed) and return the wrapped signature.
        return ECDSASignature.fromEthSignature(key.sign(message.getBytes()));
    }

    private ECKey getKeyBytes(String filePath) throws FileNotFoundException {
        if (StringUtils.isNotBlank(filePath) && Paths.get(filePath).toFile().exists()) {
            // TODO: check file permissions here too. See FederatorKeyChecker.
            try {
                FileReader fr = new FileReader(filePath);
                BufferedReader br = new BufferedReader(fr);
                byte[] keyBytes = Hex.decode(StringUtils.trim(br.readLine()).getBytes(StandardCharsets.UTF_8));
                return ECKey.fromPrivate(keyBytes);
            } catch (Exception ex) {
                logger.error("Error while reading key file", ex);
                throw new RuntimeException("Error while reading key file", ex);
            }
        } else {
            logger.error("Key file not found: ", filePath);
            throw new FileNotFoundException(String.format("Error accessing key file %s", filePath));
        }
    }
}

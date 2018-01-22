package co.rsk.signing;

import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Signer that signs using one of many signers.
 * It basically iterates its signers in their
 * definition order and signs with the first
 * that can sign with the given key id.
 *
 * Has the sum of requirements of its components.
 *
 * @author Ariel Mendelzon
 */
public class ECDSACompositeSigner implements ECDSASigner {
    private List<ECDSASigner> signers;

    public ECDSACompositeSigner() {
        signers = new ArrayList<>();
    }

    public ECDSACompositeSigner addSigner(ECDSASigner signer) {
        signers.add(signer);
        return this;
    }

    @Override
    public boolean canSignWith(KeyId keyId) {
        return signers.stream().anyMatch(signer -> signer.canSignWith(keyId));
    }

    @Override
    public ECDSASignerCheckResult check() {
        List<String> messages = new ArrayList<>();

        signers.stream().forEach(signer -> messages.addAll(signer.check().getMessages()));

        return new ECDSASignerCheckResult(messages);
    }

    @Override
    public ECDSASignature sign(KeyId keyId, Message message, SignAuthorization signAuthorization) throws IOException {
        Optional<ECDSASigner> signer = signers.stream().filter(sig -> sig.canSignWith(keyId)).findFirst();

        if (!signer.isPresent()) {
            throw new IllegalArgumentException(String.format("No suitable signer found for the requested signing key: %s", keyId));
        }

        return signer.get().sign(keyId, message, signAuthorization);
    }
}

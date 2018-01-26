/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.signing;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
        List<String> messages = signers.stream()
                .flatMap(s -> s.check().getMessages().stream())
                .collect(Collectors.toList());

        return new ECDSASignerCheckResult(messages);
    }

    @Override
    public PublicKey getPublicKey(KeyId keyId) throws SignerException {
        return findSignerFor(keyId).getPublicKey(keyId);
    }

    @Override
    public ECDSASignature sign(KeyId keyId, Message message, SignAuthorization signAuthorization) throws SignerException {
        return findSignerFor(keyId).sign(keyId, message, signAuthorization);
    }

    private ECDSASigner findSignerFor(KeyId keyId) throws SignerException {
        Optional<ECDSASigner> signer = signers.stream().filter(sig -> sig.canSignWith(keyId)).findFirst();

        if (!signer.isPresent()) {
            throw new SignerException(String.format("No suitable signer found for the requested signing key: %s", keyId));
        }

        return signer.get();
    }
}

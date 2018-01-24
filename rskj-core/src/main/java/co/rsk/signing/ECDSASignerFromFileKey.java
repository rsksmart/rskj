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
public class ECDSASignerFromFileKey implements ECDSASigner {
    private static final Logger logger = LoggerFactory.getLogger("ECDSASignerFromFileKey");

    private KeyId keyId;
    private String keyPath;

    public ECDSASignerFromFileKey(KeyId keyId, String keyPath) {
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

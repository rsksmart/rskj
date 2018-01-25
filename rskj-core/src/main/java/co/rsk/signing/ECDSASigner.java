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

    PublicKey getPublicKey(KeyId keyId) throws SignerException;

    ECDSASignature sign(KeyId keyId, Message message, SignAuthorization signAuthorization) throws SignerException;

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

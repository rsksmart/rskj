/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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



/*
 * Copyright 2013 Jim Burton.
 *
 * Licensed under the MIT license (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://opensource.org/licenses/mit-license.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package co.rsk.crypto;

import org.spongycastle.crypto.params.KeyParameter;

import java.io.Serializable;

/**
 * <p>A KeyCrypter can be used to encrypt and decrypt a message. The sequence of events to encrypt and then decrypt
 * a message are as follows:</p>
 *
 * <p>(1) Ask the user for a password. deriveKey() is then called to create an KeyParameter. This contains the AES
 * key that will be used for encryption.</p>
 * <p>(2) Encrypt the message using encrypt(), providing the message bytes and the KeyParameter from (1). This returns
 * an EncryptedData which contains the encryptedPrivateKey bytes and an initialisation vector.</p>
 * <p>(3) To decrypt an EncryptedData, repeat step (1) to get a KeyParameter, then call decrypt().</p>
 *
 * <p>There can be different algorithms used for encryption/ decryption so the getUnderstoodEncryptionType is used
 * to determine whether any given KeyCrypter can understand the type of encrypted data you have.</p>
 */
public interface KeyCrypter extends Serializable {

    /**
     * Decrypt the provided encrypted bytes, converting them into unencrypted bytes.
     *
     * @throws KeyCrypterException if decryption was unsuccessful.
     */
    byte[] decrypt(EncryptedData encryptedBytesToDecode, KeyParameter aesKey);

    /**
     * Encrypt the supplied bytes, converting them into ciphertext.
     *
     * @return encryptedPrivateKey An encryptedPrivateKey containing the encrypted bytes and an initialisation vector.
     * @throws KeyCrypterException if encryption was unsuccessful
     */
    EncryptedData encrypt(byte[] plainBytes, KeyParameter aesKey);
}

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

import com.google.common.base.Objects;
import java.util.Arrays;

/**
 * An instance of EncryptedData is a holder for an initialization vector and encrypted bytes. It is
 * typically used to hold encrypted private key bytes.
 *
 * <p>The initialisation vector is random data that is used to initialise the AES block cipher when
 * the private key bytes were encrypted. You need these for decryption.
 */
public final class EncryptedData {
    public final byte[] initialisationVector;
    public final byte[] encryptedBytes;

    public EncryptedData(byte[] initialisationVector, byte[] encryptedBytes) {
        this.initialisationVector =
                Arrays.copyOf(initialisationVector, initialisationVector.length);
        this.encryptedBytes = Arrays.copyOf(encryptedBytes, encryptedBytes.length);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        EncryptedData other = (EncryptedData) o;
        return Arrays.equals(encryptedBytes, other.encryptedBytes)
                && Arrays.equals(initialisationVector, other.initialisationVector);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(
                Arrays.hashCode(encryptedBytes), Arrays.hashCode(initialisationVector));
    }

    @Override
    public String toString() {
        return "EncryptedData [initialisationVector="
                + Arrays.toString(initialisationVector)
                + ", encryptedPrivateKey="
                + Arrays.toString(encryptedBytes)
                + "]";
    }
}

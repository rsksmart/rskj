/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

package co.rsk.db.importer.provider;

import co.rsk.db.importer.BootstrapImportException;
import co.rsk.db.importer.provider.index.data.BootstrapDataEntry;
import co.rsk.db.importer.provider.index.data.BootstrapDataSignature;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.signature.ECDSASignature;
import org.ethereum.crypto.signature.Secp256k1;

import java.math.BigInteger;
import java.util.Map;

public class BootstrapDataVerifier {

    public int verifyEntries(Map<String, BootstrapDataEntry> selectedEntries) {
        int verifications = 0;
        if (selectedEntries.isEmpty()) {
            return 0;
        }
        String hashToVerify = selectedEntries.values().iterator().next().getHash();
        byte[] dbHash = Hex.decode(hashToVerify);

        for (Map.Entry<String, BootstrapDataEntry> entry : selectedEntries.entrySet()) {
            BootstrapDataEntry bde = entry.getValue();
            String currentHash = bde.getHash();
            if (!hashToVerify.equals(currentHash)){
                throw new BootstrapImportException(String.format(
                        "Error trying to verify different hashes: %s vs %s", hashToVerify, currentHash));
            }

            BootstrapDataSignature bds = bde.getSig();

            // to use the public key we need to have an extra byte according to x9.62 declaring
            // which format is using. The current format from signer is uncompressed
            byte[] publicKey = Hex.decode(entry.getKey());

            // 1 is for forcing to interpret the values as unsigned integers
            BigInteger r = new BigInteger(1, Hex.decode(bds.getR()));
            BigInteger s = new BigInteger(1, Hex.decode(bds.getS()));

            ECDSASignature signature = new ECDSASignature(r, s);
            if (Secp256k1.getInstance().verify(dbHash, signature, publicKey)) {
                verifications++;
            }
        }

        return verifications;
    }
}
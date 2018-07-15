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

package co.rsk;

import org.spongycastle.jcajce.provider.digest.SHA3;
import org.spongycastle.util.encoders.Hex;

/*
 * Params:
    * 0: chain ID defined on EIP-155
    * 1: 20 bytes add without '0x'
 * Returns:
    * Checksummed address if input is lower case
    * Checksum verification if input has any upper case
 * Reference: RSKIP-60
 */

public class ChecksumAddress {
    public static void main(String[] args) {
        String address = args[1].toLowerCase();
        String prefixedAddress = args[0] + "0x" + address;

        SHA3.DigestSHA3 digestSHA3 = new SHA3.Digest256();
        digestSHA3.update(prefixedAddress.getBytes());
        String keccak = Hex.toHexString(digestSHA3.digest());

        String output = "";

        for (int i = 0; i < address.length(); i++)
            output += Integer.parseInt(Character.toString(keccak.charAt(i)),16) >= 8 ?
                    Character.toUpperCase(address.charAt(i)) :
                    address.charAt(i);

        if(args[1].equals(address))
            System.out.print(output);
        else
            System.out.print(args[1].equals(output));
    }
}


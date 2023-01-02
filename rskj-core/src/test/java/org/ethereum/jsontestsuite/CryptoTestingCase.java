/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

package org.ethereum.jsontestsuite;

import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.ECIESCoder;
import org.ethereum.crypto.ECKey;
import org.ethereum.util.ByteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;

/**
 * @author Roman Mandeleil
 * @since 08.02.2015
 */
public class CryptoTestingCase {

    private static Logger logger = LoggerFactory.getLogger("TCK-Test");

    private String decryption_type = "";
    private String key = "";
    private String cipher = "";
    private String payload = "";


    public CryptoTestingCase(){
    }


    public void execute(){

        byte[] key = Hex.decode(this.key);
        byte[] cipher = Hex.decode(this.cipher);

        ECKey ecKey = ECKey.fromPrivate(key);

        byte[] resultPayload = new byte[0];
        if (decryption_type.equals("aes_ctr"))
            resultPayload = ecKey.decryptAES(cipher);

        if (decryption_type.equals("ecies_sec1_altered"))
            try {
                resultPayload = ECIESCoder.decrypt(new BigInteger(ByteUtil.toHexString(key), 16), cipher);
            } catch (Throwable e) {e.printStackTrace();}

        if (!ByteUtil.toHexString(resultPayload).equals(payload)){
            String error = String.format("payload should be: %s, but got that result: %s  ",
                    payload, ByteUtil.toHexString(resultPayload));
            logger.info(error);

            System.exit(-1);
        }
    }


    public String getDecryption_type() {
        return decryption_type;
    }

    public void setDecryption_type(String decryption_type) {
        this.decryption_type = decryption_type;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getCipher() {
        return cipher;
    }

    public void setCipher(String cipher) {
        this.cipher = cipher;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    @Override
    public String toString() {
        return "CryptoTestCase{" +
                "decryption_type='" + decryption_type + '\'' +
                ", key='" + key + '\'' +
                ", cipher='" + cipher + '\'' +
                ", payload='" + payload + '\'' +
                '}';
    }
}

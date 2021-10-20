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

package co.rsk.rpc.modules.eth;

import co.rsk.core.RskAddress;
import co.rsk.core.Wallet;
import org.ethereum.core.Account;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.crypto.signature.ECDSASignature;
import org.ethereum.rpc.TypeConverter;
import org.ethereum.util.ByteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.ethereum.rpc.exception.RskJsonRpcRequestException.invalidParamError;

public class EthModuleWalletEnabled implements EthModuleWallet {

    private static final Logger LOGGER = LoggerFactory.getLogger("web3");

    private final Wallet wallet;

    public EthModuleWalletEnabled(Wallet wallet) {
        this.wallet = wallet;
    }

    @Override
    public String sign(String addr, String data) {
        String s = null;
        try {
            Account account = this.wallet.getAccount(new RskAddress(addr));
            if (account == null) {
                throw invalidParamError("Account not found");
            }

            return s = this.sign(data, account.getEcKey());
        } finally {
            LOGGER.debug("eth_sign({}, {}): {}", addr, data, s);
        }
    }

    @Override
    public String[] accounts() {
        String[] s = null;
        try {
            return s = wallet.getAccountAddressesAsHex();
        } finally {
            LOGGER.debug("eth_accounts(): {}", Arrays.toString(s));
        }
    }

    private String sign(String data, ECKey ecKey) {
        byte[] dataHash = TypeConverter.stringHexToByteArray(data);
        // 0x19 = 25, length should be an ascii decimals, message - original
        String prefix = (char) 25 + "Ethereum Signed Message:\n" + dataHash.length;

        byte[] messageHash = HashUtil.keccak256(ByteUtil.merge(
                prefix.getBytes(StandardCharsets.UTF_8),
                dataHash
        ));
        ECDSASignature signature = ECDSASignature.fromSignature(ecKey.sign(messageHash));

        return TypeConverter.toJsonHex(ByteUtil.merge(
                ByteUtil.bigIntegerToBytes(signature.getR(), 32),
                ByteUtil.bigIntegerToBytes(signature.getS(), 32),
                new byte[] {signature.getV()}
        ));
    }
}
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

import static org.ethereum.rpc.exception.RskJsonRpcRequestException.invalidParamError;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.bouncycastle.util.BigIntegers;
import org.ethereum.core.Account;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.crypto.signature.ECDSASignature;
import org.ethereum.rpc.parameters.HexAddressParam;
import org.ethereum.rpc.parameters.HexDataParam;
import org.ethereum.util.ByteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.rsk.core.Wallet;
import co.rsk.util.HexUtils;

public class EthModuleWalletEnabled implements EthModuleWallet {

    private static final Logger LOGGER = LoggerFactory.getLogger("web3");

    private final Wallet wallet;

    public EthModuleWalletEnabled(Wallet wallet) {
        this.wallet = wallet;
    }

    @Override
    public String sign(HexAddressParam addr, HexDataParam data) {
        String s = null;
        try {
            Account account = this.wallet.getAccount(addr.getAddress());
            if (account == null) {
                throw invalidParamError("Account not found");
            }

            return s = this.sign(data.getRawDataBytes(), account.getEcKey());
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

    private String sign(byte[] dataHash, ECKey ecKey) {
        // 0x19 = 25, length should be an ascii decimals, message - original
        String prefix = (char) 25 + "Ethereum Signed Message:\n" + dataHash.length;

        byte[] messageHash = HashUtil.keccak256(ByteUtil.merge(
                prefix.getBytes(StandardCharsets.UTF_8),
                dataHash
        ));
        ECDSASignature signature = ECDSASignature.fromSignature(ecKey.sign(messageHash));

        return HexUtils.toJsonHex(ByteUtil.merge(
                BigIntegers.asUnsignedByteArray(32, signature.getR()),
                BigIntegers.asUnsignedByteArray(32, signature.getS()),
                new byte[]{signature.getV()}
        ));
    }
}
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

package co.rsk.util;

import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

import static co.rsk.config.RskSystemProperties.CONFIG;

/**
 * Created by adrian.eidelman on 3/16/2016.
 */
@Component
public class AccountUtilsImpl implements AccountUtils {

    public byte[] getCoinbaseAddress()
    {
        String secret = CONFIG.coinbaseSecret();
        byte[] privKey = HashUtil.sha3(secret.getBytes(StandardCharsets.UTF_8));
        return ECKey.fromPrivate(privKey).getAddress();
    }

}

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


package co.rsk.net.discovery.message;

import co.rsk.net.discovery.PeerDiscoveryException;
import org.ethereum.util.FastByteComparisons;

import static org.ethereum.crypto.HashUtil.keccak256;

/**
 * Created by mario on 13/02/17.
 */
public class MessageDecoder {

    public static final String MDC_CHECK_FAILED = "MDC check failed";
    public static final String BAD_MESSAGE = "Bad message";

    private MessageDecoder() {}

    public static PeerDiscoveryMessage decode(byte[] wire) {
        if (wire.length < 98) {
            throw new PeerDiscoveryException(BAD_MESSAGE);
        }

        byte[] mdc = new byte[32];
        System.arraycopy(wire, 0, mdc, 0, 32);

        byte[] signature = new byte[65];
        System.arraycopy(wire, 32, signature, 0, 65);

        byte[] type = new byte[1];
        type[0] = wire[97];

        byte[] data = new byte[wire.length - 98];
        System.arraycopy(wire, 98, data, 0, data.length);

        int check = check(wire, mdc);

        if (check != 0) {
            throw new PeerDiscoveryException(MDC_CHECK_FAILED);
        }

        return PeerDiscoveryMessageFactory.createMessage(wire, mdc, signature, type, data);
  }

  public static int check(byte[] wire, byte[] mdc) {
      byte[] mdcCheck = keccak256(wire, 32, wire.length - 32);

      return FastByteComparisons.compareTo(
              mdc,
              0,
              mdc.length,
              mdcCheck,
              0,
              mdcCheck.length);
  }
}

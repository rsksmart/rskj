/*
 * This file is part of RskJ
 * Copyright (C) 2022 RSK Labs Ltd.
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

package co.rsk.net.messages;

import org.ethereum.util.RLP;

public abstract class MessageVersionAware extends Message {

    public abstract int getVersion();

    protected abstract byte[] encodeWithoutVersion();

    @Override
    public byte[] getEncodedMessage() {
        byte[] encodedMessage = encodeWithoutVersion();

        // include version if versioning enabled
        boolean isVersioningEnabled = MessageVersionValidator.isVersioningEnabledFor(getVersion());
        if (isVersioningEnabled) {
            byte[] versionEncoded = RLP.encodeInt(this.getVersion());
            return RLP.encodeList(versionEncoded, encodedMessage);
        }

        // keep previous behavior if versioning is disabled
        return encodedMessage;
    }

}

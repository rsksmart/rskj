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

package co.rsk.net.eth;

import co.rsk.net.messages.Message;
import co.rsk.net.messages.LocalMessageVersionValidator;
import org.ethereum.net.eth.message.EthMessage;
import org.ethereum.net.eth.message.EthMessageCodes;
import org.ethereum.util.RLP;

/**
 * Created by ajlopez on 5/14/2016.
 */
public class RskMessage extends EthMessage {

    private final int version;
    private Message message;

    public RskMessage(int version, Message message) {
        this.version = version;
        this.message = message;
        this.parsed = true;
    }

    @Override
    public EthMessageCodes getCommand() {
        return EthMessageCodes.RSK_MESSAGE;
    }

    public int getVersion() {
        return this.version;
    }

    public Message getMessage() {
        return this.message;
    }

    @Override
    public byte[] getEncoded() {
        if (encoded == null) {
            boolean isVersioningEnabled = LocalMessageVersionValidator.isVersioningEnabledFor(this.version);
            if (isVersioningEnabled) {
                encodeVersioned();
            } else {
                encode();
            }
        }

        return encoded;
    }

    @Override
    public Class<?> getAnswerMessage() {
        return null;
    }

    protected void encode() {
        byte[] msg = this.message.getEncoded();

        this.encoded = RLP.encodeList(msg);
    }

    private void encodeVersioned() {
        byte[] versionEncoded = RLP.encodeInt(this.version);
        byte[] msg = this.message.getEncoded();
        this.encoded = RLP.encodeList(versionEncoded, msg);
    }

    @Override
    public String toString() {
        return "[" + this.getCommand().name() +
                " message=" + this.message +
                "]";
    }
}

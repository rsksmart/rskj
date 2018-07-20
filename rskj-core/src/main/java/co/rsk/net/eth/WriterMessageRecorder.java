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

import co.rsk.net.NodeID;
import org.ethereum.net.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.bouncycastle.util.encoders.Hex;

import java.io.BufferedWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by ajlopez on 26/04/2017.
 */
public class WriterMessageRecorder implements MessageRecorder {
    private static final Logger logger = LoggerFactory.getLogger("messagerecorder");
    private BufferedWriter writer;
    private MessageFilter filter;
    private SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public WriterMessageRecorder(BufferedWriter writer, MessageFilter filter) {
        this.writer = writer;
        this.filter = filter;
    }

    @Override
    public synchronized void recordMessage(NodeID sender, Message message) {
        if (this.filter != null && !this.filter.acceptMessage(message)) {
            return;
        }

        try {
            writer.write(formatter.format(new Date()));

            writer.write(",");
            writer.write(String.valueOf(message.getCode()));
            writer.write(",");
            writer.write(String.valueOf(message.getCommand()));
            writer.write(",");

            if (message instanceof RskMessage) {
                writer.write(String.valueOf(((RskMessage) message).getMessage().getMessageType()));
            }

            writer.write(",");

            writer.write(Hex.toHexString(message.getEncoded()));

            writer.write(",");

            if (sender != null) {
                writer.write(Hex.toHexString(sender.getID()));
            }

            writer.newLine();
            writer.flush();
        }
        catch (IOException ex) {
            logger.error("Exception recording message: ", ex);
        }
    }
}

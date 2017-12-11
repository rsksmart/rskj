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

import org.ethereum.net.message.Message;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by ajlopez on 26/04/2017.
 */
public class MessageFilter {
    private Set<String> commands;

    public MessageFilter(List<String> commands) {
        if (commands != null) {
            this.commands = new HashSet<>(commands);
        }
        else {
            this.commands = new HashSet<>();
        }
    }

    public boolean acceptMessage(Message message) {
        if (commands.isEmpty()) {
            return true;
        }

        String command = String.valueOf(message.getCommand());

        if (commands.contains(command)) {
            return true;
        }

        if (!(message instanceof RskMessage)) {
            return false;
        }

        String messageType = String.valueOf(((RskMessage)message).getMessage().getMessageType());

        for (String cmd : commands) {
            if (!cmd.contains(":")) {
                continue;
            }

            String[] parts = cmd.split("\\:");

            if (parts.length != 2) {
                continue;
            }

            if (!parts[0].equals(command)) {
                continue;
            }

            if (parts[1].equals(messageType)) {
                return true;
            }
        }

        return false;
    }
}

/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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

package co.rsk.net.notifications.processing;

import co.rsk.net.notifications.FederationNotification;
import org.ethereum.net.server.Channel;

import javax.naming.ConfigurationException;
import java.util.Collection;

public interface FederationNotificationProcessor {
    FederationNotificationProcessingResult processFederationNotification(Collection<Channel> activePeers, FederationNotification notification) throws ConfigurationException;

    // TODO: find a better name for this
    // TODO: maybe something like "tick" to indicate this is a recurringly called method?
    void checkIfNodeWasEclipsed();
}
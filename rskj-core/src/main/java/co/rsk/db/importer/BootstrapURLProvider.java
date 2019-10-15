/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

package co.rsk.db.importer;

import java.net.MalformedURLException;
import java.net.URL;

public class BootstrapURLProvider {
    private final String bootstrapBaseURL;

    public BootstrapURLProvider(String bootstrapBaseURL) {
        this.bootstrapBaseURL = bootstrapBaseURL;
    }

    public URL getFullURL(String uriSuffix) {
        try {
            return new URL(bootstrapBaseURL + uriSuffix);
        } catch (MalformedURLException e) {
            throw new BootstrapImportException(String.format(
                    "The defined url for database.import.url %s is not valid",
                    bootstrapBaseURL
            ), e);
        }
    }
}
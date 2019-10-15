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

package co.rsk.db.importer.provider.index;

import co.rsk.db.importer.BootstrapImportException;
import co.rsk.db.importer.BootstrapURLProvider;
import co.rsk.db.importer.provider.index.data.BootstrapDataIndex;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class BootstrapIndexRetriever {

    private static final String INDEX_NAME = "index.json";

    private final List<String> publicKeys;
    private final BootstrapURLProvider bootstrapUrlProvider;
    private final ObjectMapper objectMapper;

    public BootstrapIndexRetriever(
            List<String> publicKeys,
            BootstrapURLProvider bootstrapUrlProvider, ObjectMapper objectMapper) {
        this.publicKeys = new ArrayList<>(publicKeys);
        this.bootstrapUrlProvider = bootstrapUrlProvider;
        this.objectMapper = objectMapper;
    }

    public List<BootstrapDataIndex> retrieve() {
        List<BootstrapDataIndex> indices = new ArrayList<>();

        for (String pk : publicKeys) {
            String indexSuffix = pk +"/" + INDEX_NAME;
            URL indexURL = bootstrapUrlProvider.getFullURL(indexSuffix);

            indices.add(readJson(indexURL));
        }
        return indices;
    }

    private BootstrapDataIndex readJson(URL indexURL) {
        try {
            return objectMapper.readValue(indexURL, BootstrapDataIndex.class);
        } catch (IOException e) {
            throw new BootstrapImportException(String.format("Failed to download and parse index from %s", indexURL), e);
        }
    }

}

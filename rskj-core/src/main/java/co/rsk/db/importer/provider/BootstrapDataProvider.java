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

package co.rsk.db.importer.provider;

import co.rsk.db.importer.BootstrapImportException;
import co.rsk.db.importer.provider.index.BootstrapIndexCandidateSelector;
import co.rsk.db.importer.provider.index.BootstrapIndexRetriever;
import co.rsk.db.importer.provider.index.data.BootstrapDataEntry;
import co.rsk.db.importer.provider.index.data.BootstrapDataIndex;

import java.util.List;
import java.util.Map;

public class BootstrapDataProvider {

    private final BootstrapIndexCandidateSelector bootstrapIndexCandidateSelector;
    private final BootstrapDataVerifier bootstrapDataVerifier;
    private final BootstrapFileHandler bootstrapFileHandler;
    private BootstrapIndexRetriever bootstrapIndexRetriever;
    private int minimumRequiredVerifications;
    private long height;

    public BootstrapDataProvider(
            BootstrapDataVerifier bootstrapDataVerifier,
            BootstrapFileHandler bootstrapFileHandler,
            BootstrapIndexCandidateSelector bootstrapIndexCandidateSelector,
            BootstrapIndexRetriever bootstrapIndexRetriever,
            int minimumRequiredVerifications) {

        this.bootstrapIndexCandidateSelector = bootstrapIndexCandidateSelector;
        this.bootstrapDataVerifier = bootstrapDataVerifier;
        this.bootstrapFileHandler = bootstrapFileHandler;
        this.bootstrapIndexRetriever = bootstrapIndexRetriever;
        this.minimumRequiredVerifications = minimumRequiredVerifications;
    }

    public void retrieveData() {
        BootstrapIndexCandidateSelector.HeightCandidate heightCandidate =
                bootstrapIndexCandidateSelector.getHeightData(getIndices());

        Map<String, BootstrapDataEntry> selectedEntries = heightCandidate.getEntries();
        verify(heightCandidate);
        height = heightCandidate.getHeight();

        bootstrapFileHandler.setTempDirectory();
        bootstrapFileHandler.retrieveAndUnpack(selectedEntries);
    }

    private void verify(BootstrapIndexCandidateSelector.HeightCandidate mchd) {
        Map<String, BootstrapDataEntry> selectedEntries = mchd.getEntries();
        int verifications = bootstrapDataVerifier.verifyEntries(selectedEntries);
        if (verifications < minimumRequiredVerifications) {
            throw new BootstrapImportException(String.format(
                    "Not enough valid signatures: selected height %d doesn't have enough trustworthy sources: %d of %d",
                    mchd.getHeight(),
                    verifications,
                    minimumRequiredVerifications
            ));
        }
    }

    private List<BootstrapDataIndex> getIndices() {
        return bootstrapIndexRetriever.retrieve();
    }

    public byte[] getBootstrapData() {
        return bootstrapFileHandler.getBootstrapData();
    }

    public long getSelectedHeight() {
        return height;
    }
}
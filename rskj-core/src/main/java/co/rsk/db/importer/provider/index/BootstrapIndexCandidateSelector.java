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
import co.rsk.db.importer.provider.index.data.BootstrapDataEntry;
import co.rsk.db.importer.provider.index.data.BootstrapDataIndex;

import java.util.*;
import java.util.stream.Collectors;

public class BootstrapIndexCandidateSelector {

    private final List<String> publicKeys;
    private final int minimumRequiredSources;

    public BootstrapIndexCandidateSelector(List<String> publicKeys, int minimumRequiredSources) {
        this.publicKeys = new ArrayList<>(publicKeys);
        this.minimumRequiredSources = minimumRequiredSources;
    }

    public HeightCandidate getHeightData(List<BootstrapDataIndex> indexes) {
        Map<Long, Map<String, BootstrapDataEntry>> entriesPerHeight = getEntriesPerHeight(indexes);
        return getHeightCandidate(entriesPerHeight);
    }

    private HeightCandidate getHeightCandidate(Map<Long, Map<String, BootstrapDataEntry>> entriesPerHeight) {
        HeightCandidate candidate = null;
        for (Map.Entry<Long, Map<String, BootstrapDataEntry>> entry : entriesPerHeight.entrySet()) {
            Long height = entry.getKey();
            boolean isPossibleCandidate = candidate == null || candidate.getHeight() < height;
            if (!isPossibleCandidate) {
                continue;
            }

            Map<String, BootstrapDataEntry> entries = entry.getValue();
            Map<String, Long> hashGroups = entries.values().stream()
                    .collect(Collectors.groupingBy(BootstrapDataEntry::getHash, Collectors.counting()));
            Optional<Map.Entry<String, Long>> bestHash = hashGroups.entrySet().stream()
                    .max(Comparator.comparing(Map.Entry::getValue));

            if (bestHash.isPresent() && bestHash.get().getValue() >= minimumRequiredSources) {
                Map<String, BootstrapDataEntry> filteredEntries = entries.entrySet().stream()
                        .filter(e -> e.getValue().getHash().equals(bestHash.get().getKey()))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

                candidate = new HeightCandidate(height, filteredEntries);
            }
        }

        if (candidate == null) {
            throw new BootstrapImportException("Downloaded files doesn't contain enough entries for a common height");
        }

        return candidate;
    }

    private Map<Long, Map<String, BootstrapDataEntry>> getEntriesPerHeight(List<BootstrapDataIndex> indexes) {
        // the outer map represents the tuples (height, heightEntries)
        // the inner map represents the tuples (source, dbEntry)
        Map<Long, Map<String, BootstrapDataEntry>> entriesPerHeight = new HashMap<>();

        // the algorithm assumes that indexes and public keys are ordered in the same way
        // each iteration is an index from a different source, each index contains many entries
        for (int i = 0; i < indexes.size(); i++) {
            BootstrapDataIndex bdi = indexes.get(i);
            String publicKey = publicKeys.get(i);

            // each iteration is an entry from an index, each entry has a different height
            // all the items for this index belongs to the same source
            for (BootstrapDataEntry bde : bdi.getDbs()) {
                Map<String, BootstrapDataEntry> entries = entriesPerHeight.computeIfAbsent(
                        bde.getHeight(), k -> new HashMap<>()
                );

                // if any height is duplicated on a single file the process is stopped
                if (entries.get(publicKey) != null){
                    throw new BootstrapImportException(String.format(
                            "There is an invalid file from %s: it has 2 entries from same height %d", publicKey, bde.getHeight()));
                }
                entries.put(publicKey, bde);
            }
        }

        if (entriesPerHeight.isEmpty()) {
            throw new BootstrapImportException("Downloaded files contain no height entries");
        }

        return entriesPerHeight;
    }

    public static class HeightCandidate {
        private Long height;
        private Map<String, BootstrapDataEntry> entries;

        public HeightCandidate(long height, Map<String, BootstrapDataEntry> entries) {
            this.height = height;
            this.entries = entries;
        }

        public long getHeight() {
            return height;
        }

        public Map<String, BootstrapDataEntry> getEntries() {
            return entries;
        }
    }
}
/*
 * This file is part of RskJ
 * Copyright (C) 2024 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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
package co.rsk.util;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static co.rsk.util.FilesHelper.readBytesFromFile;
import static co.rsk.util.FilesHelper.writeBytesToFile;

public class RskjConfigurationFileFixture {
    public static void substituteTagsOnRskjConfFile(String rskjFilePathToSubstituteTags, List<Pair<String, String>> tagValuesList) throws IOException {
        byte[] fileBytes = readBytesFromFile(rskjFilePathToSubstituteTags);
        String fileContent = new String(fileBytes, StandardCharsets.UTF_8);

        for (Pair<String, String> pair : tagValuesList) {
            fileContent = StringUtils.replace(fileContent, pair.getKey(), pair.getValue());
        }

        writeBytesToFile(fileContent.getBytes(StandardCharsets.UTF_8), rskjFilePathToSubstituteTags);
    }
}

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

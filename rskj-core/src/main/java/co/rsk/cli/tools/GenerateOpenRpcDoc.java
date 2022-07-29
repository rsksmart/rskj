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
package co.rsk.cli.tools;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.type.MapType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generates OpenRPC json doc file by merging a static template with several json files under <i>{workdir}/doc/rpc</i> dir
 * <br>
 * <br>
 * The <b>template</b> file contains basic and static OpenRPC info.
 * <p>The individual json files contain the different <b>methods</b>, <b>schemas</b> and
 * <b>contentDescriptors</b> (as per OpenRPC definition) which are maintained by devs to document implemented RPC methods
 * <br>
 * <br>
 * Required cli args:
 * <ol>
 *     <li><b>args[0]</b>: work directory where the json template and individual json files are present</li>
 *     <li><b>args[1]</b>: destination file containing the final OpenRPC json doc</li>
 * </ol>
 */
public class GenerateOpenRpcDoc {

    public static final JavaType TEMPLATE_DOC_TYPE = TypeFactory.defaultInstance().constructType(TemplateDoc.class);
    private static final JavaType OBJECT_TYPE = TypeFactory.defaultInstance().constructType(Object.class);
    private static final MapType MAP_TYPE = TypeFactory.defaultInstance().constructMapType(HashMap.class, String.class, Object.class);

    private static final Logger logger = LoggerFactory.getLogger(GenerateOpenRpcDoc.class);

    protected static final ObjectMapper JSON_MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY);

    public static void main(String[] args) {
        new GenerateOpenRpcDoc().execute(args);
    }

    @VisibleForTesting
    void execute(@Nonnull String[] args) {
        String version = args[0];
        String workDirPath = args[1];
        String destPath = args[2];

        TemplateDoc templateDoc = loadTemplate(workDirPath);
        templateDoc.info.version = version;
        templateDoc.methods.addAll(loadMethods(workDirPath));
        templateDoc.components.schemas.putAll(loadSchemas(workDirPath));
        templateDoc.components.contentDescriptors.putAll(loadContentDescriptors(workDirPath));

        writeToFile(destPath, templateDoc);
    }

    private TemplateDoc loadTemplate(String workDirPath) {
        logger.info("Loading template...");
        return (TemplateDoc) loadFileAsJson(workDirPath, "template.json", TEMPLATE_DOC_TYPE);
    }

    private List<Object> loadMethods(String workDirPath) {
        logger.info("Loading methods...");
        return loadFilesInPathAsJson(workDirPath, "methods", OBJECT_TYPE);
    }

    private Map<String, Object> loadSchemas(String workDirPath) {
        logger.info("Loading schemas...");
        return loadComponentsUnder(workDirPath, "components/schemas");
    }

    private Map<String, Object> loadContentDescriptors(String workDirPath) {
        logger.info("Loading contentDescriptors...");
        return loadComponentsUnder(workDirPath, "components/contentDescriptors");
    }

    private Object loadFileAsJson(String basePath, String fileName, JavaType toType) {
        try {
            String path = buildFullPath(basePath, fileName);
            logger.info("Loading file {}", path);
            return JSON_MAPPER.readValue(Files.newInputStream(Paths.get(path)), toType);
        } catch (IOException e) {
            logger.error("Error loading {} as json", basePath);
            throw new GenerateOpenRpcException(e);
        }
    }

    private List<Object> loadFilesInPathAsJson(String basePath, String dirName, JavaType toType) {
        String parentPath = buildFullPath(basePath, dirName);

        try (Stream<Path> stream = Files.walk(Paths.get(parentPath), 1)) {
            return stream
                    .filter(file -> !Files.isDirectory(file))
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .map(fileName -> this.loadFileAsJson(parentPath, fileName, toType))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            logger.error("Error loading files under {} as json", parentPath);
            throw new GenerateOpenRpcException(e);
        }
    }

    private static String buildFullPath(String basePath, String fileName) {
        return String.format("%s/%s", basePath, fileName);
    }

    private Map<String, Object> loadComponentsUnder(String basePath, String dirName) {
        Map<String, Object> allComponents = new HashMap<>();

        List<Object> componentList = loadFilesInPathAsJson(basePath, dirName, MAP_TYPE);
        for (Object component : componentList) {
            // just one entry in the map (1 component per file), but we need this Map format
            Map<String, Object> map = JSON_MAPPER.convertValue(component, Map.class);
            allComponents.putAll(map);
        }

        return allComponents;
    }

    private void writeToFile(String destPath, TemplateDoc templateDoc) {
        try {
            JSON_MAPPER.writeValue(new File(destPath), templateDoc);
        } catch (IOException e) {
            logger.error("Error writing result to {}", destPath);
            throw new GenerateOpenRpcException(e);
        }
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @JsonPropertyOrder({"openrpc", "info", "methods", "components"})
    @SuppressWarnings("java:S1068") // actually used for json
    private static class TemplateDoc {
        private String openrpc;
        private Info info;
        private List<Object> methods;
        private Components components;

        private TemplateDoc() {
        }
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @JsonPropertyOrder({"version", "title", "description", "license"})
    @SuppressWarnings("java:S1068") // actually used for json
    private static class Info {
        private String version;
        private String title;
        private String description;
        private Object license;

        private Info() {
        }
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @JsonPropertyOrder({"schemas", "contentDescriptors"})
    @SuppressWarnings("java:S1068") // actually used for json
    private static class Components {
        private Map<String, Object> schemas;
        private Map<String, Object> contentDescriptors;

        private Components() {

        }
    }

    private static class GenerateOpenRpcException extends RuntimeException {
        private GenerateOpenRpcException(IOException e) {
            super(e);
        }
    }

}

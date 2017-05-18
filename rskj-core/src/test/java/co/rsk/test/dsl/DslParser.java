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

package co.rsk.test.dsl;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by ajlopez on 8/6/2016.
 */
public class DslParser {
    private BufferedReader reader;

    public static DslParser fromResource(String resourceName) throws FileNotFoundException {
        ClassLoader classLoader = DslParser.class.getClassLoader();
        File file = new File(classLoader.getResource(resourceName).getFile());
        Reader reader = new FileReader(file);
        DslParser parser = new DslParser(reader);

        return parser;
    }

    public DslParser(Reader reader) {
        this.reader = new BufferedReader(reader);
    }

    public DslParser(String text) {
        this(new StringReader(text));
    }

    public DslCommand nextCommand() {
        String[] words;

        for (words = nextWords(); words != null && words.length == 0; words = nextWords())
            ;

        if (words == null)
            return null;

        String verb = words[0];
        List<String> arguments = new ArrayList<>();

        for (int k = 1; k < words.length; k++)
            arguments.add(words[k]);

        return new DslCommand(verb, arguments);
    }

    private String[] nextWords() {
        String line = null;

        try {
            line = reader.readLine();
            line = normalizeLine(line);
        } catch (IOException e) {
            return null;
        }

        if (line == null)
            return null;

        String[] words = line.split("\\s+");

        return normalizeWords(words).toArray(new String[]{});
    }

    private List<String> normalizeWords(String[] words) {
        List<String> result = new ArrayList<String>();

        for (int k = 0; k < words.length; k++)
            if (words[k].length() > 0)
                result.add(words[k]);

        return result;
    }

    private static String normalizeLine(String line) {
        if (line == null)
            return null;

        int position = line.indexOf('#');

        if (position < 0)
            return line;

        return line.substring(0, position);
    }
}

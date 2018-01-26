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

package co.rsk.signing;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by ajlopez on 29/12/2016.
 */
public class KeyFileChecker {
    public static final int KEY_LENGTH = 32;

    private String filePath;

    public KeyFileChecker(String filePath) {
        this.filePath = filePath;
    }

    public List<String> check() {
        List<String> messages = new ArrayList<>();

        String messageFileName = this.checkKeyFile();
        if (StringUtils.isNotEmpty(messageFileName)) {
            messages.add(messageFileName);
        }

        String messagePermissions = this.checkFilePermissions();
        if (StringUtils.isNotEmpty(messagePermissions)) {
            messages.add(messagePermissions);
        }

        return messages;
    }

    public String checkKeyFile() {
        if (StringUtils.isBlank(this.filePath)) {
            return "Invalid Key File Name";
        }

        if (!Paths.get(this.filePath).toFile().exists()) {
            return "Key File '" + this.filePath + "' does not exist";
        }
        try {
            byte[] var;
            KeyFileHandler keyHandler = new KeyFileHandler(this.filePath);
            var = keyHandler.privateKey();
            boolean sizeOk = this.validateKeyLength(var);
            var = null;
            if (!sizeOk) {
                return "Invalid Key Size";
            }
        } catch (Exception ex) {
            return "Error Reading Key File '" + this.filePath + "'";
        }
        return "";
    }

    public String checkFilePermissions() {
        final String errorMessage = "Invalid key file permissions";
        try {
            List<PosixFilePermission> permissions = new ArrayList<>(Files.getPosixFilePermissions(Paths.get(this.filePath)));
            if (CollectionUtils.size(permissions) == 1 && permissions.get(0).equals(PosixFilePermission.OWNER_READ)) {
                return "";
            } else {
                return errorMessage;
            }
        } catch (IOException e) {
            return errorMessage;
        }
    }

    private boolean validateKeyLength(byte[] var) {
        return var != null && var.length == KEY_LENGTH;
    }
}

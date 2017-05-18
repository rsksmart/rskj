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

package org.ethereum.rpc.dto;

import org.ethereum.core.CallTransaction;

/**
 * Created by martin.medina on 9/7/2016.
 */
public class CompilationInfoDTO {

    public String source;
    public String language;
    public String languageVersion;
    public String compilerVersion;
    public CallTransaction.Contract abiDefinition;
    public String userDoc;
    public String developerDoc;

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getLanguageVersion() {
        return languageVersion;
    }

    public void setLanguageVersion(String languageVersion) {
        this.languageVersion = languageVersion;
    }

    public String getCompilerVersion() {
        return compilerVersion;
    }

    public void setCompilerVersion(String compilerVersion) {
        this.compilerVersion = compilerVersion;
    }

    public CallTransaction.Contract getAbiDefinition() {
        return abiDefinition;
    }

    public void setAbiDefinition(CallTransaction.Contract abiDefinition) {
        this.abiDefinition = abiDefinition;
    }

    public String getUserDoc() {
        return userDoc;
    }

    public void setUserDoc(String userDoc) {
        this.userDoc = userDoc;
    }

    public String getDeveloperDoc() {
        return developerDoc;
    }

    public void setDeveloperDoc(String developerDoc) {
        this.developerDoc = developerDoc;
    }

    @Override
    public String toString() {
        return "CompilationInfo{" +
                "source='" + source + '\'' +
                ", language='" + language + '\'' +
                ", languageVersion='" + languageVersion + '\'' +
                ", compilerVersion='" + compilerVersion + '\'' +
                ", abiDefinition=" + abiDefinition +
                ", userDoc='" + userDoc + '\'' +
                ", developerDoc='" + developerDoc + '\'' +
                '}';
    }
}
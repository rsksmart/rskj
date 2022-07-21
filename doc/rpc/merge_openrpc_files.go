/*
 * This file is part of RskJ
 * Copyright (C) 2022 RSK Labs Ltd.
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

package main

import (
	"encoding/json"
	"fmt"
	"io/fs"
	"io/ioutil"
	"log"
	"os"
)

const basePath = "/Users/iagolluque/workspace/rskj/doc/rpc"                                // TODO:I param?
const destPath = "/Users/iagolluque/workspace/other/openrpc_playgrond/public/openrpc.json" // TODO:I param?

type TemplateDoc struct {
	Version    string        `json:"openrpc"`
	Info       interface{}   `json:"info"`
	Methods    []interface{} `json:"methods"`
	Components Components    `json:"components"`
}

type Components struct {
	Schemas            map[string]interface{} `json:"schemas"`
	ContentDescriptors map[string]interface{} `json:"contentDescriptors"`
}

func main() {
	templateDoc := getTemplateDoc()

	methodsFilesPath := fmt.Sprintf("%s/%s", basePath, "methods")
	methodsFiles := getFilesInPath(methodsFilesPath)
	for _, file := range methodsFiles {
		log.Println("Injecting method file: ", file.Name())
		appendElementInFile(methodsFilesPath, file, &templateDoc.Methods)
	}

	componentsFilesPath := fmt.Sprintf("%s/%s", basePath, "components")
	schemasFilesPath := fmt.Sprintf("%s/%s", componentsFilesPath, "schemas")
	schemasFiles := getFilesInPath(schemasFilesPath)
	for _, file := range schemasFiles {
		log.Println("Injecting schema file: ", file.Name())
		putElementInFile(schemasFilesPath, file, &templateDoc.Components.Schemas)
	}

	contentDescriptorsFilesPath := fmt.Sprintf("%s/%s", componentsFilesPath, "contentDescriptors")
	contentDescriptorsFiles := getFilesInPath(contentDescriptorsFilesPath)
	for _, file := range contentDescriptorsFiles {
		log.Println("Injecting contentDescriptor file: ", file.Name())
		putElementInFile(contentDescriptorsFilesPath, file, &templateDoc.Components.ContentDescriptors)
	}

	result, errMarshalDestFile := json.MarshalIndent(templateDoc, "", "  ")
	if errMarshalDestFile != nil {
		log.Fatal(errMarshalDestFile)
	}

	errWriteDestFile := ioutil.WriteFile(destPath, result, 0644)
	if errWriteDestFile != nil {
		log.Fatal(errWriteDestFile)
	}
}

func getFilesInPath(path string) []fs.FileInfo {
	files, errReadFile := ioutil.ReadDir(path)
	if errReadFile != nil {
		log.Fatal(errReadFile)
	}
	return files
}

func getTemplateDoc() TemplateDoc {
	templateDocFilePath := fmt.Sprintf("%s/%s", basePath, "template.json")
	templateDocFile, errReadDir := os.Open(templateDocFilePath)
	if errReadDir != nil {
		log.Fatal(errReadDir)
	}
	defer templateDocFile.Close()

	templateDocBytes, errReadTemplateFile := ioutil.ReadAll(templateDocFile)
	if errReadTemplateFile != nil {
		log.Fatal(errReadTemplateFile)
	}

	var templateDoc TemplateDoc

	errUnmarshalToDoc := json.Unmarshal(templateDocBytes, &templateDoc)
	if errUnmarshalToDoc != nil {
		log.Fatal(errUnmarshalToDoc)
	}
	return templateDoc
}

func appendElementInFile(filesParentPath string, fileInfo fs.FileInfo, listToModify *[]interface{}) {
	file, errOpenFile := os.Open(fmt.Sprintf("%s/%s", filesParentPath, fileInfo.Name()))
	if errOpenFile != nil {
		log.Fatal(errOpenFile)
	}
	defer file.Close()

	fileBytes, errReadFile := ioutil.ReadAll(file)
	if errReadFile != nil {
		log.Fatal(errReadFile)
	}

	var jsonDoc interface{}
	errUnmarshal := json.Unmarshal(fileBytes, &jsonDoc)
	if errUnmarshal != nil {
		log.Fatal(errUnmarshal)
	}

	*listToModify = append(*listToModify, jsonDoc)
}

func putElementInFile(filesParentPath string, fileInfo fs.FileInfo, objectToModify *map[string]interface{}) {
	file, errOpenFile := os.Open(fmt.Sprintf("%s/%s", filesParentPath, fileInfo.Name()))
	if errOpenFile != nil {
		log.Fatal(errOpenFile)
	}
	defer file.Close()

	fileBytes, errReadFile := ioutil.ReadAll(file)
	if errReadFile != nil {
		log.Fatal(errReadFile)
	}

	var jsonDoc map[string]interface{}
	errUnmarshal := json.Unmarshal(fileBytes, &jsonDoc)
	if errUnmarshal != nil {
		log.Fatal(errUnmarshal)
	}

	// just one element, still we need the loop
	for key, value := range jsonDoc {
		(*objectToModify)[key] = value
	}
}

// TODO:I pronar con oneOf para net_version

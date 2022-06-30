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

import org.ethereum.solidity.compiler.CompilationResult;

import co.rsk.util.HexUtils;

/**
 * Created by martin.medina on 9/7/2016.
 */
public class CompilationResultDTO {

    public String code;
    public CompilationInfoDTO info;

    public CompilationResultDTO(CompilationResult.ContractMetadata contractMetadata, CompilationInfoDTO compilationInfo) {
        code = HexUtils.toJsonHex(contractMetadata.bin);
        info = compilationInfo;
    }

    @Override
    public String toString() {
        return "CompilationResult{" +
                "code='" + code + '\'' +
                ", info=" + info +
                '}';
    }
}
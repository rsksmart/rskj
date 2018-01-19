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

package co.rsk.peg;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import org.ethereum.core.Block;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.spongycastle.util.encoders.Hex;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;


public class SamplePrecompiledContract extends PrecompiledContracts.PrecompiledContract {

    private Block currentBlock;
    private Transaction rskTx;
    private Repository repository;
    private BlockStore blockStore;
    private ReceiptStore receiptStore;
    private List<LogInfo> logs;
    private static final String METHOD1_SIG = "42b9358e";
    private static final String ADDBALANCE_SIG = "bc150b90";
    private static final String GETBALANCE_SIG = "f8f8a912";
    private static final String INCREMENTRESULT_SIG = "541d9c93";
    private static final String GETRESULT_SIG = "9a7d9af1";

    public SamplePrecompiledContract(RskAddress contractAddress) {
        this.contractAddress = contractAddress;
    }

    @Override
    public long getGasForData(byte[] data) {
        return 0;
    }

    @Override
    public void init(Transaction rskTx, Block currentBlock, Repository repository, BlockStore blockStore, ReceiptStore receiptStore, List<LogInfo> logs) {
        this.rskTx = rskTx;
        this.currentBlock = currentBlock;
        this.repository = repository;
        this.blockStore = blockStore;
        this.receiptStore = receiptStore;
        this.logs = logs;
    }

    @Override
    public byte[] execute(byte[] data) {
        try
        {
            byte[] signature = Arrays.copyOfRange(data, 0, 4);
            CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(this.getFuncJson(signature));

            if(function == null)
            {
                return null;
            }

            Object[] args = function.decode(data);

            Method m = this.getClass().getMethod(function.name, Object[].class);

            Object returnValue = m.invoke(this, (Object)args);

            byte[] result = null;

            if (returnValue != null) {
               result = function.encodeOutputs(returnValue);
            }

            return result;
        }
        catch(Exception ex) {
            return null;
        }
    }

    public int Method1(Object... args)
    {
        RskAddress addr = new RskAddress("cd2a3d9f938e13cd947ec05abc7fe734df8dd826");

        Coin balance = Coin.valueOf(50000);
        repository.addBalance(addr, balance);

        DataWord keyWord = new DataWord("result".getBytes(StandardCharsets.UTF_8));
        DataWord storedValue = repository.getStorageValue(contractAddress, keyWord);
        int result = (storedValue != null ? storedValue.intValue() : 0) + 1;
        DataWord valWord = new DataWord(result);
        repository.addStorageRow(contractAddress, keyWord, valWord);

        logs.add(new LogInfo(contractAddress.getBytes(), null, null));

        return result;
    }

    public void AddBalance(Object... args)
    {
        RskAddress addr = new RskAddress("cd2a3d9f938e13cd947ec05abc7fe734df8dd826");

        Coin balance = Coin.valueOf(50000);
        repository.addBalance(addr, balance);
    }

    public int GetBalance(Object... args)
    {
        RskAddress addr = new RskAddress("cd2a3d9f938e13cd947ec05abc7fe734df8dd826");

        return repository.getBalance(addr).asBigInteger().intValue();
    }

    public void IncrementResult(Object... args)
    {
        DataWord keyWord = new DataWord("result".getBytes(StandardCharsets.UTF_8));
        DataWord storedValue = repository.getStorageValue(contractAddress, keyWord);
        int result = (storedValue != null ? storedValue.intValue() : 0) + 1;
        DataWord valWord = new DataWord(result);
        repository.addStorageRow(contractAddress, keyWord, valWord);
    }

    public int GetResult(Object... args)
    {
        DataWord keyWord = new DataWord("result".getBytes(StandardCharsets.UTF_8));
        DataWord storedValue = repository.getStorageValue(contractAddress, keyWord);
        int result = (storedValue != null ? storedValue.intValue() : 0);

        return result;
    }

    private String getFuncJson(byte[] signature)
    {
        String funcJson = null;
        String sig = Hex.toHexString(signature);

        switch(sig)
        {
            case "5c60dcfd": //this is temporary for testing purposes only
            case METHOD1_SIG:
                funcJson = "{\n" +
                        "   'constant':false, \n" +
                        "   'inputs':[{'name':'param0','type':'int'}, \n" +
                        "               {'name':'param1','type':'bytes'}, \n" +
                        "               {'name':'param2','type':'int'}], \n" +
                        "    'name':'Method1', \n" +
                        "   'outputs':[{'name':'output0','type':'int'}], \n" +
                        "    'type':'function' \n" +
                        "}\n";
                funcJson = funcJson.replaceAll("'", "\"");
                break;
            case "3325fac3": //this is temporary for testing purposes only
                funcJson = "{\n" +
                        "   'constant':false, \n" +
                        "   'inputs':[{'name':'param0','type':'int'}, \n" +
                        "               {'name':'param1','type':'string'}], \n" +
                        "    'name':'Method1', \n" +
                        "   'outputs':[{'name':'output0','type':'int'}], \n" +
                        "    'type':'function' \n" +
                        "}\n";
                funcJson = funcJson.replaceAll("'", "\"");
                break;
            case ADDBALANCE_SIG:
                funcJson = "{\n" +
                        "   'constant':false, \n" +
                        "    'name':'AddBalance', \n" +
                        "   'inputs':[], \n" +
                        "   'outputs':[], \n" +
                        "    'type':'function' \n" +
                        "}\n";
                funcJson = funcJson.replaceAll("'", "\"");
                break;
            case INCREMENTRESULT_SIG:
                funcJson = "{\n" +
                        "   'constant':false, \n" +
                        "    'name':'IncrementResult', \n" +
                        "   'inputs':[], \n" +
                        "   'outputs':[], \n" +
                        "    'type':'function' \n" +
                        "}\n";
                funcJson = funcJson.replaceAll("'", "\"");
                break;
            case GETBALANCE_SIG:
                funcJson = "{\n" +
                        "   'constant':true, \n" +
                        "    'name':'GetBalance', \n" +
                        "   'inputs':[], \n" +
                        "   'outputs':[{'name':'balance','type':'int'}], \n" +
                        "    'type':'function' \n" +
                        "}\n";
                funcJson = funcJson.replaceAll("'", "\"");
                break;
            case GETRESULT_SIG:
                funcJson = "{\n" +
                        "   'constant':true, \n" +
                        "    'name':'GetResult', \n" +
                        "   'inputs':[], \n" +
                        "   'outputs':[{'name':'result','type':'int'}], \n" +
                        "    'type':'function' \n" +
                        "}\n";
                funcJson = funcJson.replaceAll("'", "\"");
                break;
        }

        return funcJson;
    }
}
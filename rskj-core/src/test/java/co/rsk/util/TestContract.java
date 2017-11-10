package co.rsk.util;

import org.ethereum.core.CallTransaction;

import java.util.HashMap;
import java.util.Map;

public class TestContract {
    public final String data;
    public final Map<String, CallTransaction.Function> functions;

    private TestContract(String data, Map<String, CallTransaction.Function> functions) {
        this.data = data;
        this.functions = functions;
    }

    public static TestContract greeter() {
        /*
        contract greeter {

            address owner;
            modifier onlyOwner { if (msg.sender != owner) throw; _ ; }

            function greeter() public {
                owner = msg.sender;
            }
            function greet(string param) onlyOwner constant returns (string) {
                return param;
            }
        }
        */

        String code = "606060405260043610610041576000357c0100000000000000000000000000000000000000000000000000000000900463ffffffff168063ead710c414610046575b600080fd5b341561005157600080fd5b6100a1600480803590602001908201803590602001908080601f0160208091040260200160405190810160405280939291908181526020018383808284378201915050505050509190505061011c565b6040518080602001828103825283818151815260200191508051906020019080838360005b838110156100e15780820151818401526020810190506100c6565b50505050905090810190601f16801561010e5780820380516001836020036101000a031916815260200191505b509250505060405180910390f35b610124610187565b6000809054906101000a900473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff163373ffffffffffffffffffffffffffffffffffffffff1614151561017f57600080fd5b819050919050565b6020604051908101604052806000815250905600a165627a7a723058202aac827affd20fd4d87d358b218eab474248ca8382a5270a7178dfbf15d64ace0029";
        String abi = "[{\"constant\":true,\"inputs\":[{\"name\":\"param\",\"type\":\"string\"}],\"name\":\"greet\",\"outputs\":[{\"name\":\"\",\"type\":\"string\"}],\"payable\":false,\"type\":\"function\"},{\"inputs\":[],\"payable\":false,\"type\":\"constructor\"}]";

        CallTransaction.Contract contract = new CallTransaction.Contract(abi);

        Map<String, CallTransaction.Function> functions = new HashMap<>();
        functions.put("greet", contract.getByName("greet"));
        return new TestContract(code, functions);
    }

    public static TestContract hello() {
        /*
        contract helloworld {
            function hello() constant returns (string) {
                return "chinchilla";
            }
        }
        */

        String code = "606060405260043610610041576000357c0100000000000000000000000000000000000000000000000000000000900463ffffffff16806319ff1d2114610046575b600080fd5b341561005157600080fd5b6100596100d4565b6040518080602001828103825283818151815260200191508051906020019080838360005b8381101561009957808201518184015260208101905061007e565b50505050905090810190601f1680156100c65780820380516001836020036101000a031916815260200191505b509250505060405180910390f35b6100dc610117565b6040805190810160405280600a81526020017f6368696e6368696c6c6100000000000000000000000000000000000000000000815250905090565b6020604051908101604052806000815250905600a165627a7a7230582043fd5aff0f077e6da5e30c15fdba5e724b7e4aa2c190c5ffe86f87f7bf18a47a0029";
        String abi = "[{\"constant\":true,\"inputs\":[],\"name\":\"hello\",\"outputs\":[{\"name\":\"\",\"type\":\"string\"}],\"payable\":false,\"type\":\"function\"}]";

        CallTransaction.Contract contract = new CallTransaction.Contract(abi);

        Map<String, CallTransaction.Function> functions = new HashMap<>();
        functions.put("hello", contract.getByName("hello"));
        return new TestContract(code, functions);
    }

    public static TestContract countcalls() {
        /*
        contract countcalls {
            uint numberOfCalls = 0;

            function calls() public returns (string) {
                return appendUintToString("calls: ", ++numberOfCalls);
            }

            function appendUintToString(string inStr, uint v) constant returns (string) {
                uint maxlength = 100;
                bytes memory reversed = new bytes(maxlength);
                uint i = 0;
                while (v != 0) {
                    uint remainder = v % 10;
                    v = v / 10;
                    reversed[i++] = byte(48 + remainder);
                }
                bytes memory inStrb = bytes(inStr);
                bytes memory s = new bytes(inStrb.length + i);
                uint j;
                for (j = 0; j < inStrb.length; j++) {
                    s[j] = inStrb[j];
                }
                for (j = 0; j < i; j++) {
                    s[j + inStrb.length] = reversed[i - 1 - j];
                }
                return string(s);
            }
        }
        */

        String code = "60606040526004361061004c576000357c0100000000000000000000000000000000000000000000000000000000900463ffffffff168063305f72b714610051578063c80667e3146100df575b600080fd5b341561005c57600080fd5b6100646101be565b6040518080602001828103825283818151815260200191508051906020019080838360005b838110156100a4578082015181840152602081019050610089565b50505050905090810190601f1680156100d15780820380516001836020036101000a031916815260200191505b509250505060405180910390f35b34156100ea57600080fd5b610143600480803590602001908201803590602001908080601f01602080910402602001604051908101604052809392919081815260200183838082843782019150505050505091908035906020019091905050610217565b6040518080602001828103825283818151815260200191508051906020019080838360005b83811015610183578082015181840152602081019050610168565b50505050905090810190601f1680156101b05780820380516001836020036101000a031916815260200191505b509250505060405180910390f35b6101c66104a9565b6102126040805190810160405280600781526020017f63616c6c733a20000000000000000000000000000000000000000000000000008152506000808154600101919050819055610217565b905090565b61021f6104a9565b60006102296104bd565b6000806102346104bd565b61023c6104bd565b600060649650866040518059106102505750595b9080825280601f01601f19166020018201604052509550600094505b60008914151561030357600a8981151561028257fe5b069350600a8981151561029157fe5b049850836030017f01000000000000000000000000000000000000000000000000000000000000000286868060010197508151811015156102ce57fe5b9060200101907effffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff1916908160001a90535061026c565b899250848351016040518059106103175750595b9080825280601f01601f19166020018201604052509150600090505b82518110156103e057828181518110151561034a57fe5b9060200101517f010000000000000000000000000000000000000000000000000000000000000090047f01000000000000000000000000000000000000000000000000000000000000000282828151811015156103a357fe5b9060200101907effffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff1916908160001a9053508080600101915050610333565b600090505b84811015610499578581600187030381518110151561040057fe5b9060200101517f010000000000000000000000000000000000000000000000000000000000000090047f010000000000000000000000000000000000000000000000000000000000000002828451830181518110151561045c57fe5b9060200101907effffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff1916908160001a90535080806001019150506103e5565b8197505050505050505092915050565b602060405190810160405280600081525090565b6020604051908101604052806000815250905600a165627a7a72305820264c1b1eb867072724b16b278bfebb3817f1f2e5ba8c43e1b8a75bf10cb416cb0029";
        String abi = "[{\"constant\":true,\"inputs\":[],\"name\":\"calls\",\"outputs\":[{\"name\":\"\",\"type\":\"string\"}],\"payable\":false,\"type\":\"function\"},{\"constant\":true,\"inputs\":[{\"name\":\"inStr\",\"type\":\"string\"},{\"name\":\"v\",\"type\":\"uint256\"}],\"name\":\"appendUintToString\",\"outputs\":[{\"name\":\"str\",\"type\":\"string\"}],\"payable\":false,\"type\":\"function\"},{\"constant\":true,\"inputs\":[{\"name\":\"v\",\"type\":\"uint256\"}],\"name\":\"uintToString\",\"outputs\":[{\"name\":\"str\",\"type\":\"string\"}],\"payable\":false,\"type\":\"function\"}]";

        CallTransaction.Contract contract = new CallTransaction.Contract(abi);

        Map<String, CallTransaction.Function> functions = new HashMap<>();
        functions.put("calls", contract.getByName("calls"));
        return new TestContract(code, functions);
    }
}

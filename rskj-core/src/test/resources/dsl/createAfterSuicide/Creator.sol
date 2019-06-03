pragma solidity ^0.5.9;
import "./TestChild.sol";

contract Creator {
    function run() public{
        bytes memory code = type(TestChild).creationCode;
        uint256 salt = 0;
        address addr;
        assembly { addr := create2(0, add(code,0x20), mload(code), salt)}
    }   
}
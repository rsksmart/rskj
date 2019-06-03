pragma solidity ^0.5.9;

contract TestChild {
    function destroy() public{
        selfdestruct(address(0x0000000000000000000000000000000000000000));
    }
}
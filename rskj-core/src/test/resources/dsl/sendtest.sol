pragma solidity ^0.4.2;

contract SendTest {
  event Message(string msg);

  mapping (address => uint) public balances;

  function deposit() public payable {
    balances[msg.sender] += msg.value;
  }

  function withdrawBalance(address from, address to, uint amount) public {
    balances[from] -= amount;
	Message('amount subtracted from balance');
    require(to.call.value(amount)());
	Message('require call done');
  }

}


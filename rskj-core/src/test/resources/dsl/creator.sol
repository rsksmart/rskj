contract Counter {
    event Incremented(bool indexed odd, uint x);
	event Created(uint x);
	event Valued(uint x);

    function Counter() {
        x = 70;
		Created(x);
    }

    function increment() {
        ++x;
        Incremented(x % 2 == 1, x);
    }

    function getValue() constant returns (uint) {
		Valued(x);
        return x;
    }

    uint x;
}

contract Creator {
	Counter counter;
	event CounterCreated(uint);

	function Creator() {
		counter = new Counter();
		CounterCreated(counter.getValue());
	}
}

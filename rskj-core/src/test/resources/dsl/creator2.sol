contract Counter {
    event Incremented(bool indexed odd, uint x);
    event Created(uint x);
    event Valued(uint x);

    constructor(uint value) {
        x = value;
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
    Counter public counter;
    event CounterCreated(uint);

    constructor() {
        counter = new Counter(block.number);
        CounterCreated(counter.getValue());
    }
}


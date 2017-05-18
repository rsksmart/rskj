# Block Gas Limit
as of 18/05/2016

## Block Gas Limit Rational

- One block sets the max gas limit of the following block
- GasLimit in one block must always be between 1023/1024 and 1025/1024 of the previous block gas limit 
- GasLimit can never go below a specific constant (otherwise it is not possible to enter new transactions)

## Current Eth implementation (homestead)


Quoting the Ethereum Homestead Yellow Paper:

> The canonical gas limit Hl of a block of header H must fulfil the relation:
> (44) Hl < P(H)Hl + Abs(P(H)Hl / 1024) ∧
> (45) Hl > P(H)Hl − Abs(P(H)Hl / 1024) ∧
> (46) Hl > 125000

i.e., the current alghoritm according to the yellow enables the block limit to be increased / decreased by a 1/1024 of the previous block gas limit. It can be decreased to a minimum of 125.000.

The algorithm in the go implementation is the following:


> // CalcGasLimit computes the gas limit of the next block after parent.
> // The result may be modified by the caller.
> // This is miner strategy, not consensus protocol.
> func CalcGasLimit(parent *types.Block) *big.Int {
> 	// contrib = (parentGasUsed * 3 / 2) / 1024
> 	contrib := new(big.Int).Mul(parent.GasUsed(), big.NewInt(3))
> 	contrib = contrib.Div(contrib, big.NewInt(2))
> 	contrib = contrib.Div(contrib, params.GasLimitBoundDivisor)
> 
> 	// decay = parentGasLimit / 1024 -1
> 	decay := new(big.Int).Div(parent.GasLimit(), params.GasLimitBoundDivisor)
> 	decay.Sub(decay, big.NewInt(1))
> 
> 	/*
> 		strategy: gasLimit of block-to-mine is set based on parent's
> 		gasUsed value.  if parentGasUsed > parentGasLimit * (2/3) then we
> 		increase it, otherwise lower it (or leave it unchanged if it's right
> 		at that usage) the amount increased/decreased depends on how far away
> 		from parentGasLimit * (2/3) parentGasUsed is.
> 	*/
> 	gl := new(big.Int).Sub(parent.GasLimit(), decay)
> 	gl = gl.Add(gl, contrib)
> 	gl.Set(common.BigMax(gl, params.MinGasLimit))
> 
> 	// however, if we're now below the target (TargetGasLimit) we increase the
> 	// limit as much as we can (parentGasLimit / 1024 -1)
> 	if gl.Cmp(params.TargetGasLimit) < 0 {
> 		gl.Add(parent.GasLimit(), decay)
> 		gl.Set(common.BigMin(gl, params.TargetGasLimit))
> 	}
> 	return gl
> }

Therefore:
- GasLmit is increased by a 1/2048 of parentGasUsed (current value of GasLimitBoundDivisor is 1024)
- GasLimit is decreased by 1024/latestGasLimit -1 
- It is not allowed to decreased GasLimit lower than MinGasLimit 
- Is gas limit is still below the TargetGasLimit (genesis block limit, about 500000) then the decay value (step 2) is added back 


## RskJ Implementation

- We follow the Eth implementation, but setting the minimum limit to 1.000.000, so that complex contracts can be executed from the beginning
- Allowed gas limit can never go above a target
- Implementation is written in the GasLimitCalculator class, under the co.rsk.mine package.

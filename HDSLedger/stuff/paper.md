# How the fuck does Narwhal-Tusk work?

## What is proposed on the paper
- A mempool protocol for separating transaction dissemination from ordering.
- The protocol aims to increase transaction throughput by leaving consensus only the job of
ordering small fixed-sized references, therefore removing the bottleneck from consensus throughput.


## Concepts

### What theoretical message complexity fails to capture
- Leader-based partially synchronous protocols fail to capture that the leader becomes a bottleneck
- Metadata messages are signficantly smaller than transaction data

### What happens with classic consensus algorithms (monolithic)
- Clients send transactions to a validator that shares them using a mempool protoco, therefore removing the bottleneck from consensus throughput.

### What other similar protocols do
- They focus on separating the transmission from ordering but they do not guarantee reliability,
leading to great performance when no faults occur, but the opposite also happens

### What is Tusk
- Tusk is a fully-asynchronous wait-free consensus algorithm based on Narwhal and a random coin
- Tusk is based on DAG-Rider

## Overview of the system

### Composition
- n machines, at most f byzantine nodes, n > 3f
- Network is asynchronous but eventually reliable (no bound on delays, but finite number of losses)
- Key-Value store
- A block `b` is composed by:
  - A list of tx
  - A list of references to other blocks
  - A digest of its contents
  - References imply a causal relation between the blocks (`b` references `b'` implies that `b'` happened
  before `b`)

### Functionality
- `write(d, b)` - stores block `b` with key `d`
- `c(d)` - represents an unforgeable certificate of availability on digest `d`
- `valid(d, c(d))` - true if `c(d)` is a valid certificate of `d`, false otherwise
- `read(d)` - returns block `b` if a `write(d, b)` has succeeded
- `read_causal(d)` - returns the set of blocks that are causally ordered before `b`

### Properties
- Integrity - if two honest nodes call `read(d)`, the block returned is the same (where `d` is a certificate)
- Block-Availability - if an honest node invokes `read` after a `write`, it eventually completes
and block `b` is returned
- Containment - The causal history of a block returned by `read_causal(d)` is contained in `read_causal(d)`
- 2/3 Causality - At least 2/3 of the causal history of in writing block `b` has been written
  - Spoiler: A block needs 2f + 1 references to blocks in the previous round to be valid
- 1/2 Chain Quality - At least 1/2 of the blocks in the causal history of `b` were written by correct nodes
  - Spoiler: According to the aforementioned condition, if f are byzantine: f + 1 / 2f + 1 are correct
  - This property ensures resistance to censorship

## Design

### The mempool
- Each validator maintains the current round, `r`
- Validators receive tx from clients and accumulate them in a list
- Validators also receive certificates of availability for blocks at `r` and store them in a list
- A validator moves from round `r - 1` to `r` when it has accumulated 2f + 1 distinct certificates
(distinct validators) **This is what ensures censorship resistance**
- Once a validator moves to round `r`, it creates and broadcasts a new block
- This block includes the list of transactions accumulated during `r - 1`
- This block is also signed by its creator
- Pull is used instead of Push (I do not understand how this helps)
- A valid block is composed by:
  - valid signature
  - is at the same round of the validator checking it
  - contain certificates for at least 2f + 1 blocks of the previous round
  - be the first block received from that creator in that round
- If a validator receives a valid block, it signs the block digest, round number and the creator's identity
- It then acknowledges the block by replying with ^
- Once the creator of a block amasses 2f + 1 distinct signatures, it uses them to create a certificate
of block availability which is then broadcasted so that it can be referenced in the next round
- Upon initialization, all validators create and certify empty blocks for round `r = 0`

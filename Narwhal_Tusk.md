# Abstract
Separating "reliable transcation dissemination" from "transaction ordering"
Why?
Achieve high-performance

What is Narwhal?
Mempool protocol specialized in high-throughput reliable dissemination and storage of causal histories of transactions
What is Tusk?
Zero-message overhead asynchronous consensus protocol (designed to work with Narwhal)
Why Tusk?
Other consensus protocols had lower throughputs (Narwhal-HotStuff).

# 1 Introduction

Theoretical message complexity should not be the only optimisation target:
- Centralised algorithms make the leader become a bottleneck
- Metadata (e.g. votes) and block messages contribute evenly for the complexity, although block messages are many orders of magnitude larger than consensus messages

Hypothesis: "a better Mempool, that reliably distributes transactions, is the key enabler of a high-performance ledger It should be separated from the consensus protocol altogether, leaving consensus only the job of ordering small fixedsize references. This leads to an overall system throughput being largely unaffected by consensus throughput"

How to test:
Use Hotstuff but separate block dissemination into a separate system called "Batched-HS"
Results:
Increases performance but only under perfect conditions so they designed a new protocol "Narwhal"

What is Narwhal ?
DAG-based structured Mempool which implements causal order reliable broadcast of transaction blocks.
Results:
Combining Narwhal with HotStuff provides good throughput but at an higher latency.To reduce this latency they designed Tusk.

What is Tusk ?
Narwhal extension with a random coin (to provide asynchronous consensus).
Fully-asynchronous, DDoS resilient, zero overhead, wait-free consensus where each party decides the agreed values by examining its local DAG without sending any additional messages.

# 2 Overview

# 2.1 System model, goals and assumptions

Set of N parties
Corrupt up to f < N/3 parties
Links among honest parties are asynchronous eventually reliable communication links, meaning that there is no bound on message delays and there is a finity but unknown number of messages that can be lost.

Block
A block contains a list of transactions and a list of references to previous blocks. 
It's identified by its digest.

Mempool
Exposes to all participants a key-value block store abstraction
- write(d, b) writes b associated with its digest (d) -> returns an unforgeable certificate of availability, c(d)
- valid(d, c(d)) checks if c is a valid certificate of d
- read(d) returns b if write(d, b) has succeeded
- read_causal(d) returns a set of blocks B which happened before b

Properties:
- Integrity: 2 independent invocations of read(d) on honest parties always return b
- Block-availability: read(d) after write(d, b) always returns (eventually) b
- Containment: given the sets returned by two read_causal operations, one of them is always contained or equal to the other
- 2/3 causality: A successful read_causal(d) returns a set B that contains at least 2/3 of the blocks written successfully before write(d, b) was invoked.
• 1/2-Chain Quality: At least 1/2 of the blocks in the returned set B of a successful read_causal(d) invocation were written by honest parties.

Integrity and block-availability allow separation between data dissemination and consensus
Causality and containment allow for high-throughput despite periods of asynchrony

Extra desired property:
- Scale out: Narwhal’s throughput increases linearly with the number of resources each validator has while the latency does not suffer.

# 2.2 Intuitions behind the Narwhal design

Best-effort gossip mempool (used by established blockchains)
- One validator transmits submitted transactions to all others
- Leads to fine-grained double transmissions (blocks may contain transactions known by nodes)

Steps to reduce the need for double transmission and enable scaling out:
- Broadcast blocks instead of transactions
    - Leader proposes a hash of a block
    - Let mempool provide integrity
    - Validators ensure hashes represent available blocks by downloading them before certifying them
- Consistently broadcast the block
    - Ensures availability
    - Leader proposes a certificate of the block
    - Problem: If the consensus loses liveness, the number to be committed may grow indefinitely
- Propose a single certificate for multiple blocks
    - Provides causalty
    - Have a block's certificate include certificates of past Mempool blocks, from all validators, allowing it to refer to the block's full causal history
    - Ensures that delays in reaching consensus impact latency but not average throughput–as mempool blocks continue to be produced and are eventually committed
    - Problems:
        - A very fast validator may force others to perform large downloads by generating blocks at a high speed
        - Honest validators may not get enough bandwidth to share their blocks with others, leading to potential censorship
- Impose restrictions on block creation rate
    - Provides chain quality
    - Each block from a validator contains a round number, and must include a quorum of certificates from the previous round to be valid
    - As a result, a fraction of honest validators’ blocks are included in any proposal
    - A validator cannot advance to a Mempool round before some honest ones concluded the previous round, preventing flooding
    - An adversary cannot kill leaders because it does not like the proposal as all proposals include at lest 50% of honest transactions, even the ones from Byzantine leaders
- Have multiple worker machines per validator sharing Mempool sub-blocks (a.k.a. batches)
    - Enables scale-out
    - One primary integrates references to batches in Mempool primary blocks
    - Allows quasi-linear scaling
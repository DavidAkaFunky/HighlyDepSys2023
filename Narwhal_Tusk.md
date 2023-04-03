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
Extended Narwhal with a random coin (to provide asynchronous consensus).
Fully-asynchronous, wait-free consensus where each party decides the agreed values by examining its local DAG without sending any additional messages.

# 2 Overview

# 2.1 System model, goals and assumptions

Set of N parties
Corrupt up to f < N/3 parties
Links among honest parties are asynchronous eventually reliable communication links, meaning that there is no bound on message delays and there is a finity but unknown number of messages that can be lost.

What is the mempool ?
A block contains a list of transactions and a list of references to previous blocks. 
Its identified by its digest.


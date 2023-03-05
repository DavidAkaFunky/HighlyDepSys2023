# Communication

```
The network is unreliable: it can drop, delay, duplicate, or corrupt messages, 
and communication channels are not secured. This means that solutions relying 
on secure channel technologies such as TLS are not allowed. In fact we request 
that the basic communication is done using UDP, with various layers of 
communication abstraction built on top. Using UDP allows the network to 
approximate the behavior of Fair Loss Links, thus allowing you to build (a 
subset of) the abstractions we learned in class
```

# What's being communicated

A string to be appended.  
The request should wait for a reply confirming if and when the request was executed

# Gap between client interface and IBFT interface

Need to decide how this communication will be done (must be UDP).
```
We leave it as an open design decision to devise a mechanism to fill this gap, i.e., 
translate client requests to invocations of the Istanbul BFT protocol, and also for 
the respective response path. There are several approaches to address these issues, 
so it is up to students to propose a design and justify why it is adequate.
```

Note that this communication goes both ways, we should decide how the client calls the 
blockchain service and how the blockchain service responds.

```
Finally, on the blockchain 
side, there should also exist an upcall that is invoked whenever the DECIDE output is 
reached, for notifying the upper layer where the blockchain implementation resides. 
This upcall can be handled by a simple service implementation that keeps an array with 
all the appended strings in memory.
```

# Testing

Our tests should be able to simulate byzantine nodes

# Abstractions

SL (Stubborn links) using FLL (Fair loss link)
--------------------------------
```
upon event <sp2pSend, dest, m> do
    while (true) do
        trigger <flp2pSend, dest, m>;
upon event <flp2pDeliver, src, m> do
    trigger <sp2pDeliver, src, m>;
```

PF (Perfect links) using SL (Stubborn links)
--------------------------------
```
upon event <init> do  
    delivered = { };  
upon event <pp2pSend, dest, m> do  
    trigger <sp2pSend, dest, m>;  
upon event <sp2pDeliver, src, m> do  
    if m ∉ delivered then  
        trigger <pp2pDeliver, src, m>;  
        delivered = delivered ⋃ { m };  
```

PF (Perfect links) using FLL (Fair loss link)
--------------------------------
```
upon event <init> do
    delivered = { };    
upon event <pp2pSend, dest, m> do
    while (true) do
        trigger <flp2pSend, dest, m>;
upon event <flp2pDeliver, src, m> do
    if m ∉ delivered then
        trigger <pp2pDeliver, src, m>;
        delivered = delivered ⋃ { m };
```

APF (Authenticated perfect links) using SL (using MACs)
--------------------------------
```
upon event <init> do
    delivered = { };
upon event <alp2pSend, dest, m> do
    a = authenticate(self, dest,m);
    trigger <sp2pSend, dest, [m,a]>;
upon event <sp2pDeliver, src, m, a> do
    if verifyauth(src, self, m, a) && m ∉ delivered then
        trigger <alp2pDeliver, src, m>;
        delivered = delivered ⋃ { m };
```
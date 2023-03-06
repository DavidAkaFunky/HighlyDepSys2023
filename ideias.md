The network is unreliable: it can drop, delay, duplicate, or corrupt messages,
and communication channels are not secured. This means that solutions relying
on secure channel technologies such as TLS are not allowed. In fact we request
that the basic communication is done using UDP, with various layers of
communication abstraction built on top. Using UDP allows the network to
approximate the behavior of Fair Loss Links, thus allowing you to build (a
subset of) the abstractions we learned in class.
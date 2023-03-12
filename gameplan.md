# Architecture

* Client issues request to LedgerService
* LedgerService is listening and receives the request
* LedgerService spawns a thread, processes the request
* LedgerService issues a command on the blockchain (inside thread)
* This thread is responsible for sending the response back to the client?
*  Problems with this:
    * Client broadcasts request
    * Every node is going to send a message back to the client
    * The client is blocking and the library can maintain state on what consensus instances it has seen
    (using consensus instance as a sort of a nonce)
    * This removes the need for a `PerfectChannel` between the client and the node as the client
    just keeps spamming every node every **timeout** seconds until it gets a response.
    * Make sure to close client socket every time, to avoid reading stale messages. 
    (Responses from other nodes)

* This makes PerfectChannels only needed for Node-Node communication, therefore, 
making the links identifiable by id.
* So PerfectLink is only used as middleware for `NodeService`

According to the points above, the to-do list is as follows:

1. Fix `PerfectLinks` / `SimplexLinks` for them to use `NodeId` as a key for everything.
2. Create a broadcast method for the client library to call.
3. Everything directly related to LedgerService (Messages, Receiving, Sending...)

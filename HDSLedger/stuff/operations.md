# Client Communication

Clietn only sends the message to the leader
and only the leader replies

## Read
 
read() -> string   <==>  append("") -> string

Read should send a no-op before reading the state


## Write

append(string) -> string


## Nice to have

replicas forward messages to leader (just in case)
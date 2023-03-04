# Istanbul BFT

## System Model

Each upon rule is triggered at most once for any round ri. (excp. line 17 Alg3)
A quorum of valid messages is floor((n+f)/2)+1 valid messages from different processes.
A valid message must carry some proof of integrity and authentication of its sender.
The external validity predicate β must also be true.

## Algorithm 1
Starts a consensus instance λ on pi.
```
pi ⊲ The identifier of the process
λi ⊲ The identifier of the consensus instance
ri ⊲ The current round
pri ⊲ The round at which the process has prepared
pvi ⊲ The value for which the process has prepared
inputValue_i ⊲ The value passed as input to this instance
timer_i

procedure Start(λ, value)
    λi ← λ
    ri ← 1
    pri ← ⊥
    pvi ← ⊥
    inputValue_i ← value
    if leader(hi, ri) = pi then
        broadcast <PRE-PREPARE, λi, ri, inputValue_i> message
    set timer_i to running and expire after t(ri)
```

## Algorithm 2
Normal execution of IBFT, which happens during periods where the leader is correct and 
messages are timely delivered.
```
upon receiving a valid <PRE-PREPARE, λi, ri, value> message m from leader(λi, round) such that JustifyPrePrepare(m) do
    set timeri to running and expire after t(ri)
    broadcast <PREPARE, λi, ri, value>
    
upon receiving a quorum of valid <PREPARE, λi, ri, value> messages do
    pri ← ri
    pvi ← value
    broadcast <COMMIT, λi, ri, value>
    
upon receiving a quorum Qcommit of valid <COMMIT, λi, round, value> messages do
    set timer_i to stopped
    Decide(λi, value, Qcommit)
```

## Algorithm 4
```
predicate JustifyPrePrepare(hPRE-PREPARE, λi, round, valuei)
    return
        round = 1
        ∨ received a quorum Qrc of valid <ROUND-CHANGE, λi, round, prj , pvj> messages
            such that:
                ∀<ROUND-CHANGE, λi, round, prj , pvj> ∈ Qrc : prj = ⊥ ∧ prj = ⊥
                ∨ received a quorum of valid <PREPARE, λi, pr, value> messages such that:
                    (pr, value) = HighestPrepared(Qrc)

"""
Helper function that returns a tuple (pr, pv) where pr and pv are, respectively, the prepared round
and the prepared value of the ROUND-CHANGE message in Qrc with the highest prepared round
"""         
function HighestPrepared(Qrc)
    return (pr, pv) such that:
        ∃<ROUND-CHANGE, λi, round, pr, pv> ∈ Qrc :
            ∀<ROUND-CHANGE, λi, round, prj , pvj> ∈ Qrc : prj = ⊥ ∨ pr ≥ prj
```
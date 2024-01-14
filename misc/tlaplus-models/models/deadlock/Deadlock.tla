(*
Author: Michel Charpentier.
This example shows how you can quickly spot concurrency bugs.

A blocking queue is a buffer that holds data created by producing threads until they are retrieved by consuming threads.
More importantly, the buffer acts as a synchronizer, blocking and suspending threads when there is nothing for them to do.
The buffer has finite capacity, producing threads must be suspended when the buffer is full.
Any consuming thread needs to be blocked until there is data in the buffer.

When "get" calls "notify", the intent is to notify a producer that a slot has been made available in the buffer.
The problem is that this call can sometimes notify a consumer instead and, if this happens a few times, it can lead to a deadlock.
The Java reproducer takes a lot of time to detect the deadlock and doesn't give a clear error trace because logs are interleaved.
Instead, running this model will give you a counter example (sequence of actions that led to the issue) in just a second.
*)
---- MODULE Deadlock ----

EXTENDS Naturals, Sequences

CONSTANTS
    Producers, \* set of producers
    Consumers, \* set of consumers
    BufCapacity, \* max amount of messages in the bounded buffers
    Data \* set of values that can be produced and/or consumed

ASSUME
    /\ Producers # {} \* at least one producer
    /\ Consumers # {} \* at least one consumer
    /\ Producers \intersect Consumers = {} \* no thread is both producer and consumer
    /\ BufCapacity > 0 \* buffer capacity is at least 1
    /\ Data # {} \* the type of data is nonempty

VARIABLES
    buffer, \* the buffer (sequence of messages)
    waitSet \* the wait set of threads
vars == <<buffer, waitSet>>

Participants == (Producers \cup Consumers)
RunningThreads == (Producers \cup Consumers) \ waitSet

Init ==
    /\ buffer = <<>> \* the buffer is empty
    /\ waitSet = {} \* no thread is waiting

----

Notify ==
    IF waitSet # {}
    THEN \E t \in waitSet : waitSet' = waitSet \ {t}
    ELSE UNCHANGED waitSet

NotifyAll ==
    waitSet' = {}

Wait(t) ==
    waitSet' = waitSet \cup {t}

Put(t, m) ==
    IF Len(buffer) < BufCapacity
    THEN /\ buffer' = Append(buffer, m)
         /\ Notify
    ELSE /\ Wait(t)
         /\ UNCHANGED buffer

Get(t) ==
    IF Len(buffer) > 0
    THEN /\ buffer' = Tail(buffer)
         /\ Notify \* replace Notify with NotifyAll to fix
    ELSE /\ Wait(t)
         /\ UNCHANGED buffer

RunThread(t) ==
    \/ /\ t \in Producers
       /\ \E m \in Data : Put(t, m)
    \/ /\ t \in Consumers
       /\ Get(t)

----

\* non-deterministically pick a thread out using the existential quantifier
Next ==
    \E t \in RunningThreads : RunThread(t)

Spec ==
    Init /\ [][Next]_vars

----

\* invariant: type correctness
TypeOK ==
    /\ buffer \in Seq(Data)
    /\ Len(buffer) \in 0..BufCapacity
    /\ waitSet \in SUBSET Participants

\* liveness: at least one thread is expected to run at any time
NoDeadlock ==
    [](RunningThreads # {})

====

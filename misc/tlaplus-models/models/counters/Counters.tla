(*
Author: Jack Vanlightly.
We have a number of counters and in each step, one of them increments its value by 1.
If we set no upper limit on the counter values the state space becomes infinite.
If we limit the counters to staying equal to or lower than 2 we get 8 possible states and 12 state transitions.
*)
---- MODULE Counters ----

EXTENDS Integers

CONSTANT C, Limit
ASSUME Limit > 0

VARIABLES counter
vars == <<counter>>

Init ==
    counter = [c \in C |-> 0]

----

IncrementCounter(c) ==
    /\ counter[c] < Limit
    /\ counter' = [counter EXCEPT ![c] = counter[c] + 1]

----

\* non-deterministically choose a counter using the existential quantifier
Next ==
    \E c \in C :
        IncrementCounter(c)

\* each counter must be cyclically incremented
Fairness ==
    \A c \in C : SF_vars(IncrementCounter(c))

Spec ==
    Init /\ [][Next]_vars /\ Fairness

----

\* invariant: counters can only increment
AlwaysNonNegative ==
    \A c \in C : counter[c] > -1

AllAtLimit ==
    \A c \in C : counter[c] = Limit

\* liveness: eventually all counters will reach the limit and stay there forever
EventuallyAllAtLimit ==
    <>[]AllAtLimit

====

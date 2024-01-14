(*
Author: Federico Valeri.
Simple vending machine model that can be used to introduce TLA+.
*)
---- MODULE VMachine ----

EXTENDS Naturals

\* runtime parameters
CONSTANTS Max \* max number of cans
\* Constant validation checked before the model runs
ASSUME Max > 0

\* global variables
VARIABLES
    coke,   \* available cans of coke
    sprite, \* available cans of sprite
    coin,   \* whether a coin was entered
    wallet  \* earnings since last refill
vars == <<coke, sprite, coin, wallet>>

\* initial state formula (possible initial values of all variables)
Init ==
    /\ coke = Max
    /\ sprite = Max
    /\ coin = 0
    /\ wallet = 0

----

\* state transition formulas (next-state actions)

GetCoke ==
    /\ coin > 0
    /\ coke > 0 \* enablement condition
    /\ coin' = coin - 1 \* state change
    /\ wallet' = wallet + 1
    /\ coke' = coke - 1
    /\ UNCHANGED sprite

GetSprite ==
    /\ coin > 0
    /\ sprite > 0
    /\ coin' = coin - 1
    /\ wallet' = wallet + 1
    /\ sprite' = sprite - 1
    /\ UNCHANGED coke

InsertCoin ==
    /\ coin < coke + sprite
    /\ coin' = coin + 1
    /\ UNCHANGED <<coke, sprite, wallet>>

Refill ==
    /\ coin = 0
    /\ coke = 0
    /\ sprite = 0
    /\ wallet' = 0
    /\ coke' = Max
    /\ sprite' = Max
    /\ UNCHANGED coin

----

\* next state relationship (non deterministic)
Next ==
    \/ InsertCoin
    \/ GetCoke
    \/ GetSprite
    \/ Refill

\* fairness constraints that rule out unwanted behaviors
\* the vending machine is eventually refilled
Fairness ==
    SF_vars(Refill)

\* temporal formula that describes any possible behavior
Spec ==
    Init /\ [][Next]_vars /\ Fairness

----

\* safety and liveness properties we want to check

\* invariant: type correctness
TypeOK ==
    /\ coke <= Max
    /\ sprite <= Max
    /\ coin <= coke + sprite
    /\ wallet >= 0

\* invariant: the wallet must always contain one coin for each released can
NoFreeDrinks ==
    wallet = (Max - coke) + (Max - sprite)

\* liveness: if we run out of cans, then the machine is eventually refilled
EventuallyRefilled ==
    coke = 0 /\ sprite = 0 ~> coke = Max /\ sprite = Max

====

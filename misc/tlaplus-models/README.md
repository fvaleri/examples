# TLA+ models

*TLA+ (Temporal Logic of Actions)* is a formal specification language that can be used to create models of distributed systems and concurrent algorithms.
When we model a system in TLA+ we are modelling its design (what it should do), not its implementation (how it should do it).
In addition to design a new system or algorithm, you can use it to reveal design flaws and check modifications of existing systems.
In a TLA+ specification, the system design is represented by variables, constants and actions.

- *Constants* describe aspects of the system that do not change (this could be the actors in the system, or some threshold).
- *Variables* describe the mutable state of the system, such as its data or the current status of the different actors (a server being up or down).
- *Actions* modify the system (mutating the variables), for example, a message is sent or received, data is written or deleted.

A *state* is a mapping of values to variables, while a *step* is the transition from one state to another.
We describe the *initial state* or even multiple possible states that the system can start in.
The *actions* define how the system can change, and are used to transition into subsequent states.
At the start of each action we can specify *enabling conditions* using equality operators.

## Model checker

The *TLC Model Checker* is a program that explores all possible executions of a system, revealing any concurrency error or design flaw.
You can specify system properties and behaviors, and the model checker will tell you if they hold or not.
It works by creating a graph of all reachable states (state space), and doing a breadth-first search (BFS) to check system properties.
If a property is violated, TLC returns an error trace (counter example) showing how to reproduce the bad behavior (sequence of states).

We classify system properties into two types: *safety* (bad things do not happen) and *liveness* (something good must eventually happen).
The most widely used safety property is the *invariant*, which is a property that must always be true in every reachable state.
Invariants are checked on every single state, but we may also want to add liveness properties, which checked against entire behaviors.
Liveness checking is slow, but useful to make sure that the system makes progress (no deadlocks or cycles) and reaches the expected states.

Invariants are a powerful tool for reasoning about algorithms, data structures, and distributed systems. 
It's worth thinking through a set of invariants for any complex system or algorithm you design or implement.
It's also worth building your implementation in such a way that even global invariants can be easily tested in a deterministic and repeatable way.

How you can find invariants? You have to sit down and go through the algorithm step-by-step, asking what is true about the data structure at this step.
In the end, you get a set of step-by-step invariants, and some global invariants that must hold true in every state of the algorithm.
Global invariants is what you want to model in TLA+ to check for correctness.

## System fairness

Stuttering is the concept that while no actions in your specification execute (none are enabled), the world beyond your spec continues.
A system is unfair if an infinite sequence of stutter steps is a valid behavior.
Liveness properties require the system to eventually reach a state and then be blocked by stuttering.

We use weak fairness (WF) to guarantee that an action (state transition) that can always happen (always enabled) eventually happens.
We use strong fairness (SF) to guarantee that an action that happens sporadically (cyclically enabled) eventually happens.

## State space reduction

If you want to model check your specification with TLC, then you don't want an infinite state space (state space explosion).
You need to constrain the number of states that can be reached.

TLC can compute a global state fingerprint and use that to know if it has previously reached a state and can skip subsequent states.
Using `VIEW` we can constrain the state space by excluding some auxiliary variables from the fingerprint (i.e. counters).
Using `SYMMETRY` on input sets we can constrain the state space by excluding symmetric states (`CHOOSE` can't be used).

The problem with the previous state space constraints is that they prevent liveness checks (they can only be done on infinite behaviors).
To avoid this, we can use enabling conditions to restrict the state space, while preserving the ability to check liveness properties.

## Run script

This repository includes a `run.sh` script that helps to run TLC.
It is possible to override default configuration by using `JAVA_OPTS` and `TLC_OPTS` environment variables.

```sh
# check the model
./run.sh models/vmachine/VendingMachine
./run.sh models/vmachine/VendingMachine.tla models/vmachine/VendingMachine.cfg

# simulate a random behavior
export TLC_OPTS="-simulate file=/tmp/tlc/sim.txt,num=1 metadir /tmp/tlc -depth 10 -workers 1"
./run.sh models/vmachine/VendingMachine

# generate the state graph (ony for small state spaces)
sudo dnf -y install graphviz
export TLC_OPTS="-modelcheck -metadir /tmp/tlc -workers auto -noGenerateSpecTE -dump dot,colorize,actionlabels /tmp/tlc/graph.dot"
./run.sh models/vmachine/VendingMachine
dot -Tpng /tmp/tlc/graph.dot > models/vmachine/StateSpace.png
```

## Language syntax

Language syntax and module structure.

```txt
---- MODULE Example ----    \* starts module (should be in file Example.tla)
====                        \* ends module (everything after that is ignored)

CONSTANTS x, y, ...         \* declares constants x, y, ... (should be defined in Example.cfg)
VARIABLES x, y, ...         \* declares variables x, y, ...

Name == e                   \* defines operator Name without parameters, and with expression e as a body
Name(x, y, ...) == e        \* defines operator Name with parameters x, y, ..., and body e (may refer to x, y, ...)

(* Boolean logic. *)

TRUE                        \* boolean true
FALSE                       \* boolean false
~x                          \* not x; negation
x /\ y                      \* x and y; conjunction (can be also put at line start, in multi-line conjunctions)
x \/ y                      \* x or y; disjunction (can be also put at line start, in multi-line disjunctions)
x = y                       \* x equals y
x /= y                      \* x not equals y
x => y                      \* implication: y is true whenever x is true
x <=> y                     \* equivalence: x is true if and only if y is true

(* Integers: EXTENDS Integers, EXTENDS Naturals if you don't need negative numbers. *)

1, -2, 1234567890           \* integer literals; integers are unbounded
a..b                        \* integer range: all integers between a and b inclusive
-x                          \* integer negation: negate the integer literal or variable x
x + y, x - y, x * y         \* integer addition, subtraction, multiplication
x < y, x <= y               \* less than, less than or equal
x > y, x >= y               \* greater than, greater than or equal

(* Sets: EXTENDS FiniteSets, unordered, no duplicates. *)

{a, b, c}                   \* set constructor: the set containing a, b, c
Cardinality(S)              \* number of elements in set S
SUBSET(S)                   \* the set of all subsets of S
x \in S                     \* x belongs to set S
x \notin S                  \* x does not belong to set S
S \subseteq T               \* is set S a subset of set T? true of all elements of S belong to T
S \union T                  \* union of sets S and T: all x belonging to S or T
S \intersect T              \* intersection of sets S and T: all x belonging to S and T
S \ T                       \* set difference, S less T: all x belonging to S but not T
{x \in S: P(x)}             \* set filter: selects all elements x in S such that P(x) is true
{e: x \in S}                \* set map: maps all elements x in set S to expression e (which may contain x)

(* Quantifiers. *)

\A x \in S:                 \* for all elements x in set S it holds that
\E x \in S:                 \* there exists an element x in set S such that (non deterministic)

(* Functions: imutable maps from keys domain to value domain. *) 

[x \in S |-> e]             \* function constructor: maps all keys x from domain S to expression e (may refer to x) 
[S -> T]                    \* the set of all functions whose domain is S and such that \A x in S: f[x] \in T
f[x]                        \* function application: the value of function f at key x
DOMAIN f                    \* function domain: the set of keys of function f
[f EXCEPT ![x] = e]         \* function f with key x remapped to expression e (may reference @, the original f[x])
[f EXCEPT ![x] = e1,        \* function f with multiple keys remapped: 
          ![y] = e2, ...]   \*   x to e1 (@ in e1 will be equal to f[x]), y to e2 (@ in e2 will be equal to f[y])

(* Records: function from a finite set of strings to values. *)

[x |-> e1, y |-> e2, ...]   \* record constructor: a record equal at field x to e1, and at y to e2 
r.x                         \* record field access: the value of field x of record r
[r EXCEPT !.x = e]          \* record r with field x remapped to expression e (may reference @, the original r.x)
[r EXCEPT !.x = e1,         \* record r with multiple fields remapped: 
          !.y = e2, ...]    \*   x to e1 (@ in e1 is equal to r.x), y to e2 (@ in e2 is equal to r.y)

(* Sequences: EXTENDS Sequences, functions with domain 1 .. n for some natural number n. *)

<<a, b, c>>                 \* sequence constructor: a sequence containing elements a, b, c
s[i]                        \* ith element of the sequence t (1-indexed!)
s \o t                      \* concatenation of sequences s and t
Seq(s)                      \* set of all finite sequences with elements in set S
S \X T                      \* set product of S and T (all pairs <<s, t>>)
Len(s)                      \* length of sequence s
Append(s, x)                \* sequence s with x added to the end
Head(s)                     \* first element of sequence s
Tail(s)                     \* sequence s with first element removed
SubSeq(s, m, n)             \* subsequence of s from m to n inclusive
SelectSeq(s, P(_))          \* subsequence of elements of sequence s that satisfy predicate P

(* Miscellaneous constructs. *)

IF P THEN t ELSE e          \* conditional expression
CASE p1 -> e1               \* conditional expressions with more than two branches
    [] p2 -> e2             \*   if p2 is true, then e2
    [] OTHER -> e           \*   OTHER branch is optional
LET x == t IN e             \* introduce local definitions
CHOOSE x \in S: p           \* picks the least value that matches predicate p (deterministic)

(* Temporal operators. *)
[]F                         \* F is always true
<>F                         \* F is eventually true
WF(A)                       \* weak fairness for action A
SF(A)                       \* strong fairness for action A
F ~> G                      \* F leads to G

(* Other operators, EXTENDS TLC. *)
a :> b                      \* the function [x \in {a} |-> b]
func1 @@ func2              \* function merge
Permutations(set)           \* the set of all functions that act as permutations of set
SortSeq(seq, Op(_, _))      \* sorts the sequence with comparator Op
PrintT(val)                 \* print to stdout
```

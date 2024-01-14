---- MODULE Types ----

EXTENDS Naturals, FiniteSets, Sequences, TLC

\* the set of server IDs
CONSTANTS Servers

\* the set of values that can go into the log
CONSTANTS Values

\* server states
CONSTANTS Follower, Candidate, Leader

\* a reserved value
CONSTANTS Nil

\* message types
CONSTANTS RequestVoteRequest, RequestVoteResponse,
          AppendEntriesRequest, AppendEntriesResponse

\* used for filtering messages under different circumstance
CONSTANTS EqualTerm, LessOrEqualTerm

\* reduce the state space by limiting the number of elections and restarts
CONSTANTS MaxElections, MaxRestarts

\* constants validation
ASSUME
  /\ Servers # {}
  /\ Values # {}
  /\ MaxElections \in Nat
  /\ MaxRestarts \in Nat

----

\* a bag of records representing requests and responses sent from one server to another
\* TLAPS doesn't support the Bags module, so this is a function mapping Message to Nat
VARIABLES messages

\* per server variables (functions with domain Server)

\* the server's term number
VARIABLES currentTerm
\* the server's state (Follower, Candidate, or Leader)
VARIABLES state
\* the candidate the server voted for in its current term, or Nil if it hasn't voted for any
VARIABLES votedFor
serverVars == <<currentTerm, state, votedFor>>

\* a sequence of log entries
\* the index into this sequence is the index of the log entry
\* unfortunately, the Sequence module defines Head(s) as the entry with index 1, so be careful not to use that
VARIABLES log
\* the index of the latest entry in the log the state machine may apply
VARIABLES commitIndex
logVars == <<log, commitIndex>>

\* the following variables are used only on candidates

\* the set of servers from which the candidate has received a vote in its currentTerm
VARIABLES votesGranted
candidateVars == <<votesGranted>>

\* the following variables are used only on leaders

\* the next entry to send to each follower
VARIABLES nextIndex
\* the latest entry that each follower has acknowledged is the same as the leader's
\* this is used to calculate commitIndex on the leader
VARIABLES matchIndex
\* used to track which peers a leader is waiting on a response for
\* used for one-at-a-time AppendEntries RPCs
\* not really required but permitting out of order requests explodes the state space
VARIABLES pendingResponse
leaderVars == <<nextIndex, matchIndex, pendingResponse>>

\* auxiliary variables (used for state space control, invariants etc)

\* the values that have been received from a client and whether the value has been acked back to the client
\* used in invariants to detect data loss
VARIABLES acked
\* counter for elections and restarts. Used used to control state space
VARIABLES electionCtr, restartCtr
auxVars == <<acked, electionCtr, restartCtr>>

----

\* all variables, used for stuttering (asserting state hasn't changed)
vars == <<messages, serverVars, candidateVars, leaderVars, logVars, auxVars>>
\* this view is used by TLC to distinguish states, instead of using all variables
varsWithoutAux == <<messages, serverVars, candidateVars, leaderVars, logVars>>
\* used to restrict the state space by skipping symmetric states
\* this can't be used in CHOOSE or liveness expressions
symmServers == Permutations(Servers)

\* the set of all quorums
\* this calculates simple majorities, but the only important property is that every quorum overlaps with every other
Quorum == {i \in SUBSET(Servers) : Cardinality(i) * 2 > Cardinality(Servers)}

====

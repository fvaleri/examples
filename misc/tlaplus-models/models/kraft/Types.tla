---- MODULE Types ----
EXTENDS Integers, Naturals, FiniteSets, Sequences, TLC

\* the set of server IDs
CONSTANTS Servers

\* the set of requests that can go into the log
CONSTANTS Values

\* server states
CONSTANTS Follower, Candidate, Leader, Unattached, Voted

\* a reserved value
CONSTANTS Nil

\* message types
CONSTANTS RequestVoteRequest, RequestVoteResponse,
          BeginQuorumRequest, BeginQuorumResponse,
          EndQuorumRequest,
          FetchRequest, FetchResponse

\* fetch response codes
CONSTANTS Ok, NotOk, Diverging

\* errors
CONSTANTS FencedLeaderEpoch, NotLeader, UnknownLeader

\* special state that indicates a server has entered an illegal state
CONSTANTS IllegalState

\* used for filtering messages under different circumstances
CONSTANTS AnyEpoch, EqualEpoch

\* limiting state space by limiting the number of elections and restarts
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

\* the server's epoch number (the Raft term)
VARIABLES currentEpoch
\* the server's state (Follower, Candidate etc)
VARIABLES state
\* the candidate the server voted for in its current epoch
VARIABLES votedFor
\* the peer that the server believes is the current leader
VARIABLES leader
\* tracks the currently pending fetch request of a follower
VARIABLES pendingFetch
serverVars == <<currentEpoch, state, votedFor, leader, pendingFetch>>

\* a sequence of log entries
\* the offset into this sequence is the offset of the log entry
\* unfortunately, the Sequence module defines Head(s) as the entry with offset 1, so be careful not to use that
VARIABLES log
\* the offset of the latest entry in the log the state machine may apply
VARIABLES highWatermark
logVars == <<log, highWatermark>>

\* the following variables are used only on candidates

\* the set of servers from which the candidate has received a vote in its currentEpoch
VARIABLES votesGranted
candidateVars == <<votesGranted>>

\* the following variables are used only on leaders

\* the latest entry that each follower has acknowledged is the same as the leader's
\* this is used to calculate highWatermark on the leader
VARIABLES endOffset
leaderVars == <<endOffset>>

\* auxilliary variables (used for state-space control, invariants etc)

\* the values that have been received from a client and whether the value has been acked back to the client
\* used in invariants to detect data loss
VARIABLES acked
\* counter for elections and restarts used used to control state space
VARIABLES electionCtr, restartCtr
auxVars == <<acked, electionCtr, restartCtr>>

----

\* all variables, used for stuttering (asserting state hasn't changed)
vars == <<messages, serverVars, candidateVars, leaderVars, logVars, auxVars>>
varsWithoutAux == <<messages, serverVars, candidateVars, leaderVars, logVars, acked>>
symmServers == Permutations(Servers)
symmValues == Permutations(Values)

\* the set of all quorums
\* this just calculates simple majorities, but the only important property is that every quorum overlaps with every other
Quorum == {i \in SUBSET(Servers) : Cardinality(i) * 2 > Cardinality(Servers)}

====

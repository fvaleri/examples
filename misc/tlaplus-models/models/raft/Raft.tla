(*
Author: Jack Vanlightly.
Model checking optimized fork of the original Raft spec by Diego Ongaro: https://github.com/ongardie/raft.tla.

Summary of changes:

- Updated message helpers.
- Prevent resending the same message multiple times (unless explicity via the duplicate action).
- Can only receive a message that hasn't been delivered yet.
- Optimized for model checking (reduction in state space).
- Removed history variables (using simple invariants instead).
- Decomposed "receive" into separate actions.
- Compressed multi-step AppendEntriesRequest processing into one.
- Compressed timeout and RequestVote into single action.
- Server directly votes for itself in an election (it doesn't send itself a vote request).
- Fixed some bugs.
- Adding the same value over and over again.
- Allowing actions to remain enabled producing odd results.

Notes on action enablement:

- Send is only enabled if the message has not been previously sent.
- This is leveraged to disable actions once executed, such as sending a specific AppendEntriesRequest.
- It won't be sent again, so no need for extra variables to track that.

Additional changes:

- Added constants validation.
- Variable currentTerm initialized to 0 on first boot.
- Added note on liveness property that can't be used with symmetry sets.
- Created different modules for types, functions and properties.
*)
---- MODULE Raft ----

EXTENDS Naturals, FiniteSets, Sequences, TLC, Types, Functions, Properties

InitServerVars ==
    /\ currentTerm = [i \in Servers |-> 0]
    /\ state       = [i \in Servers |-> Follower]
    /\ votedFor    = [i \in Servers |-> Nil]
InitCandidateVars == votesGranted = [i \in Servers |-> {}]
\* the values nextIndex[i][i] and matchIndex[i][i] are never read, since the leader does not send itself messages
\* it's still easier to include these in the functions
InitLeaderVars ==
    /\ nextIndex  = [i \in Servers |-> [j \in Servers |-> 1]]
    /\ matchIndex = [i \in Servers |-> [j \in Servers |-> 0]]
InitLogVars ==
    /\ log             = [i \in Servers |-> <<>>]
    /\ commitIndex     = [i \in Servers |-> 0]
    /\ pendingResponse = [i \in Servers |-> [j \in Servers |-> FALSE]]
InitAuxVars ==
    /\ electionCtr = 0
    /\ restartCtr = 0
    /\ acked = [v \in Values |-> Nil]

Init ==
    /\ messages = [m \in {} |-> 0]
    /\ InitServerVars
    /\ InitCandidateVars
    /\ InitLeaderVars
    /\ InitLogVars
    /\ InitAuxVars

----

\* server i restarts from stable storage
\* it loses everything but its currentTerm, votedFor and log
Restart(i) ==
    /\ restartCtr < MaxRestarts
    /\ state'           = [state EXCEPT ![i] = Follower]
    /\ votesGranted'    = [votesGranted EXCEPT ![i] = {}]
    /\ nextIndex'       = [nextIndex EXCEPT ![i] = [j \in Servers |-> 1]]
    /\ matchIndex'      = [matchIndex EXCEPT ![i] = [j \in Servers |-> 0]]
    /\ pendingResponse' = [pendingResponse EXCEPT ![i] = [j \in Servers |-> FALSE]]
    /\ commitIndex'     = [commitIndex EXCEPT ![i] = 0]
    /\ restartCtr'      = restartCtr + 1
    /\ UNCHANGED <<messages, currentTerm, votedFor, log, acked, electionCtr>>

\* combined Timeout and RequestVote of the original spec to reduce state space
\* server i times out and starts a new election
\* sends a RequestVote request to all peers but not itself
RequestVote(i) ==
    /\ electionCtr < MaxElections
    /\ state[i] \in {Follower, Candidate}
    /\ state' = [state EXCEPT ![i] = Candidate]
    /\ currentTerm' = [currentTerm EXCEPT ![i] = currentTerm[i] + 1]
    /\ votedFor' = [votedFor EXCEPT ![i] = i] \* votes for itself
    /\ votesGranted' = [votesGranted EXCEPT ![i] = {i}] \* votes for itself
    /\ electionCtr' = electionCtr + 1
    /\ SendMultipleOnce(
           {[mtype         |-> RequestVoteRequest,
             mterm         |-> currentTerm[i] + 1,
             mlastLogTerm  |-> LastTerm(log[i]),
             mlastLogIndex |-> Len(log[i]),
             msource       |-> i,
             mdest         |-> j] : j \in Servers \ {i}})
    /\ UNCHANGED <<acked, leaderVars, logVars, restartCtr>>

\* leader i sends j an AppendEntries request containing up to 1 entry
\* while implementations may want to send more than 1 at a time, this spec uses
\* just 1 because it minimizes atomic regions without loss of generality
AppendEntries(i, j) ==
    /\ i # j
    /\ state[i] = Leader
    /\ pendingResponse[i][j] = FALSE \* not already waiting for a response
    /\ LET prevLogIndex == nextIndex[i][j] - 1
           prevLogTerm == IF prevLogIndex > 0 THEN
                              log[i][prevLogIndex].term
                          ELSE
                              0
           \* send up to 1 entry, constrained by the end of the log
           lastEntry == Min({Len(log[i]), nextIndex[i][j]})
           entries == SubSeq(log[i], nextIndex[i][j], lastEntry)
       IN
          /\ pendingResponse' = [pendingResponse EXCEPT ![i][j] = TRUE]
          /\ Send([mtype          |-> AppendEntriesRequest,
                   mterm          |-> currentTerm[i],
                   mprevLogIndex  |-> prevLogIndex,
                   mprevLogTerm   |-> prevLogTerm,
                   mentries       |-> entries,
                   mcommitIndex   |-> Min({commitIndex[i], lastEntry}),
                   msource        |-> i,
                   mdest          |-> j])
    /\ UNCHANGED <<serverVars, candidateVars, nextIndex, matchIndex, logVars, auxVars>>

\* candidate i transitions to leader
BecomeLeader(i) ==
    /\ state[i] = Candidate
    /\ votesGranted[i] \in Quorum
    /\ state'      = [state EXCEPT ![i] = Leader]
    /\ nextIndex'  = [nextIndex EXCEPT ![i] = [j \in Servers |-> Len(log[i]) + 1]]
    /\ matchIndex' = [matchIndex EXCEPT ![i] = [j \in Servers |-> 0]]
    /\ pendingResponse' = [pendingResponse EXCEPT ![i] = [j \in Servers |-> FALSE]]
    /\ UNCHANGED <<messages, currentTerm, votedFor, candidateVars, logVars, auxVars>>

\* leader i receives a client request to add v to the log
ClientRequest(i, v) ==
    /\ state[i] = Leader
    /\ acked[v] = Nil \* prevent submitting the same value repeatedly
    /\ LET entry == [term |-> currentTerm[i], value |-> v]
           newLog == Append(log[i], entry)
       IN  /\ log' = [log EXCEPT ![i] = newLog]
           /\ acked' = [acked EXCEPT ![v] = FALSE]
    /\ UNCHANGED <<messages, serverVars, candidateVars,
                   leaderVars, commitIndex, electionCtr, restartCtr>>

\* leader i advances its commitIndex
\* this is done as a separate step from handling AppendEntries responses, in part to minimize atomic regions,
\* and in part so that leaders of single-server clusters are able to mark entries committed
AdvanceCommitIndex(i) ==
    /\ state[i] = Leader
    /\ LET \* the set of servers that agree up through index
           Agree(index) == {i} \cup {k \in Servers : matchIndex[i][k] >= index}
           \* the maximum indexes for which a quorum agrees
           agreeIndexes == {index \in 1..Len(log[i]) : Agree(index) \in Quorum}
           \* new value for commitIndex'[i]
           newCommitIndex ==
              IF /\ agreeIndexes # {}
                 /\ log[i][Max(agreeIndexes)].term = currentTerm[i]
              THEN
                  Max(agreeIndexes)
              ELSE
                  commitIndex[i]
       IN
          /\ commitIndex[i] < newCommitIndex \* only enabled if it actually advances
          /\ commitIndex' = [commitIndex EXCEPT ![i] = newCommitIndex]
          /\ acked' = [v \in Values |->
                        IF acked[v] = FALSE
                        THEN v \in {log[i][index].value : index \in commitIndex[i]+1..newCommitIndex}
                        ELSE acked[v]]
    /\ UNCHANGED <<messages, serverVars, candidateVars, leaderVars, log,
                   pendingResponse, electionCtr, restartCtr>>

\* any RPC with a newer term causes the recipient to advance its term first
UpdateTerm ==
    \E m \in DOMAIN messages :
        /\ m.mterm > currentTerm[m.mdest]
        /\ currentTerm'    = [currentTerm EXCEPT ![m.mdest] = m.mterm]
        /\ state'          = [state       EXCEPT ![m.mdest] = Follower]
        /\ votedFor'       = [votedFor    EXCEPT ![m.mdest] = Nil]
           \* messages is unchanged so m can be processed further
        /\ UNCHANGED <<messages, candidateVars, leaderVars, logVars, auxVars>>

\* server i receives a RequestVote request from server j with m.mterm <= currentTerm[i]
HandleRequestVoteRequest ==
    \E m \in DOMAIN messages :
        /\ ReceivableMessage(m, RequestVoteRequest, LessOrEqualTerm)
        /\ LET i     == m.mdest
               j     == m.msource
               logOk == \/ m.mlastLogTerm > LastTerm(log[i])
                        \/ /\ m.mlastLogTerm = LastTerm(log[i])
                           /\ m.mlastLogIndex >= Len(log[i])
               grant == /\ m.mterm = currentTerm[i]
                        /\ logOk
                        /\ votedFor[i] \in {Nil, j}
            IN /\ m.mterm <= currentTerm[i]
               /\ \/ grant  /\ votedFor' = [votedFor EXCEPT ![i] = j]
                  \/ ~grant /\ UNCHANGED votedFor
               /\ Reply([mtype        |-> RequestVoteResponse,
                         mterm        |-> currentTerm[i],
                         mvoteGranted |-> grant,
                         msource      |-> i,
                         mdest        |-> j],
                         m)
               /\ UNCHANGED <<state, currentTerm, candidateVars, leaderVars, logVars, auxVars>>

\* server i receives a RequestVote response from server j with m.mterm = currentTerm[i]
HandleRequestVoteResponse ==
    \E m \in DOMAIN messages :
        \* this counts votes even when the current state is not Candidate,
        \* but they won't be looked at, so it doesn't matter
        /\ ReceivableMessage(m, RequestVoteResponse, EqualTerm)
        /\ LET i     == m.mdest
               j     == m.msource
           IN
              /\ \/ /\ m.mvoteGranted
                    /\ votesGranted' = [votesGranted EXCEPT ![i] =
                                              votesGranted[i] \cup {j}]
                 \/ /\ ~m.mvoteGranted
                    /\ UNCHANGED <<votesGranted>>
              /\ Discard(m)
              /\ UNCHANGED <<serverVars, votedFor, leaderVars, logVars, auxVars>>

\* server i receives an invalid AppendEntries request from server j
\* either the term of the message is stale or the message entry is too high (beyond the last log entry + 1)
RejectAppendEntriesRequest ==
    \E m \in DOMAIN messages :
        /\ ReceivableMessage(m, AppendEntriesRequest, LessOrEqualTerm)
        /\ LET i     == m.mdest
               j     == m.msource
               logOk == LogOk(i, m)
           IN  /\ \/ m.mterm < currentTerm[i]
                  \/ /\ m.mterm = currentTerm[i]
                     /\ state[i] = Follower
                     /\ ~logOk
               /\ Reply([mtype       |-> AppendEntriesResponse,
                         mterm       |-> currentTerm[i],
                         msuccess    |-> FALSE,
                         mmatchIndex |-> 0,
                         msource     |-> i,
                         mdest       |-> j],
                         m)
               /\ UNCHANGED <<state, candidateVars, leaderVars, serverVars, logVars, auxVars>>

\* server i receives a valid AppendEntries request from server j
\* the original spec had to three sub actions, this version is compressed
\* in one step it can: truncate the log, append an entry to the log, respond to the leader
AcceptAppendEntriesRequest ==
    \E m \in DOMAIN messages :
        /\ ReceivableMessage(m, AppendEntriesRequest, EqualTerm)
        /\ LET i     == m.mdest
               j     == m.msource
               logOk == LogOk(i, m)
               index == m.mprevLogIndex + 1
           IN
              /\ state[i] \in {Follower, Candidate}
              /\ logOk
              /\ LET newLog == CASE CanAppend(m, i) ->
                                        [log EXCEPT ![i] = Append(log[i], m.mentries[1])]
                                  [] NeedsTruncation(m, i, index) /\ m.mentries # <<>> ->
                                        [log EXCEPT ![i] = Append(TruncateLog(m, i), m.mentries[1])]
                                  [] NeedsTruncation(m, i, index) /\ m.mentries = <<>> ->
                                        [log EXCEPT ![i] = TruncateLog(m, i)]
                                  [] OTHER -> log
                 IN
                    /\ state' = [state EXCEPT ![i] = Follower]
                    /\ commitIndex' = [commitIndex EXCEPT ![i] = m.mcommitIndex]
                    /\ log' = newLog
                    /\ Reply([mtype       |-> AppendEntriesResponse,
                              mterm       |-> currentTerm[i],
                              msuccess    |-> TRUE,
                              mmatchIndex |-> m.mprevLogIndex + Len(m.mentries),
                              msource     |-> i,
                              mdest       |-> j],
                              m)
                    /\ UNCHANGED <<candidateVars, leaderVars, votedFor, currentTerm, auxVars>>

\* server i receives an AppendEntries response from server j with m.mterm = currentTerm[i]
HandleAppendEntriesResponse ==
    \E m \in DOMAIN messages :
        /\ ReceivableMessage(m, AppendEntriesResponse, EqualTerm)
        /\ LET i     == m.mdest
               j     == m.msource
           IN
              /\ \/ /\ m.msuccess \* successful
                    /\ nextIndex'  = [nextIndex  EXCEPT ![i][j] = m.mmatchIndex + 1]
                    /\ matchIndex' = [matchIndex EXCEPT ![i][j] = m.mmatchIndex]
                 \/ /\ ~m.msuccess \* not successful
                    /\ nextIndex' = [nextIndex EXCEPT ![i][j] = Max({nextIndex[i][j] - 1, 1})]
                    /\ UNCHANGED <<matchIndex>>
              /\ pendingResponse' = [pendingResponse EXCEPT ![i][j] = FALSE]
              /\ Discard(m)
              /\ UNCHANGED <<serverVars, candidateVars, logVars, auxVars>>

----

Next ==
    \/ \E i \in Servers : Restart(i)
    \/ \E i \in Servers : RequestVote(i)
    \/ \E i \in Servers : BecomeLeader(i)
    \/ \E i \in Servers, v \in Values : ClientRequest(i, v)
    \/ \E i \in Servers : AdvanceCommitIndex(i)
    \/ \E i,j \in Servers : AppendEntries(i, j)
    \/ UpdateTerm
    \/ HandleRequestVoteRequest
    \/ HandleRequestVoteResponse
    \/ RejectAppendEntriesRequest
    \/ AcceptAppendEntriesRequest
    \/ HandleAppendEntriesResponse
    \*\/ \E m \in DOMAIN messages : DuplicateMessage(m)
    \*\/ \E m \in DOMAIN messages : DropMessage(m)

Spec ==
    Init /\ [][Next]_vars

====

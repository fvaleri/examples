(*
Author: Jack Vanlightly.
This specification is based on (with heavy modification) the original Raft specification by Diego Ongaro which can be found here: https://github.com/ongardie/raft.tla.

This is a specification that has been reverse engineered from the Kafka KRaft implementation.
It makes some effort to reuse some of the functions of the implementation in order to ensure it is accurately modelling the behaviour.

KRaft is a pull variant of Raft, which is push based.

Note the following messages are not modelled:

- BeginQuorumResponse as this is only required by the implementation for liveness.
  If the leader doesn't receive a response it resends the BeginQuorumRequest.
  However, in the specification, message retries are implicit and so explicit retries are not required.
- EndQuorumRequest/Response as this exists as an optimization for a leader that gracefully shutdown.
  It is not needed for correctness and so is not included.
- FetchSnapshotRequest/Response as this is a straight forward optimization and so has not been explicitly modelled.

The KRaft implementation uses a cache object as an index for epochs and their start offsets which is required for leaders to be able to give followers the information they need to truncate their logs.
This specification does not model this cache but simply looks up this information from the log itself.

State transitions (taken from https://github.com/apache/kafka/blob/trunk/raft/src/main/java/org/apache/kafka/raft/QuorumState.java):

Unattached|Resigned transitions to:
- Unattached: After learning of a new election with a higher epoch.
- Voted: After granting a vote to a candidate.
- Candidate: After expiration of the election timeout.
- Follower: After discovering a leader with an equal or larger epoch.

Voted transitions to:
- Unattached: After learning of a new election with a higher epoch.
- Candidate: After expiration of the election timeout.

Candidate transitions to:
- Unattached: After learning of a new election with a higher epoch.
- Candidate: After expiration of the election timeout.
- Leader: After receiving a majority of votes.

Leader transitions to:
- Unattached: After learning of a new election with a higher epoch.
- Resigned: When shutting down gracefully.

Follower transitions to:
- Unattached: After learning of a new election with a higher epoch.
- Candidate: After expiration of the fetch timeout.
- Follower: After discovering a leader with a larger epoch.

## Additional changes

- Added constants validation.
- Variable currentEpoch initialized to 0 on first boot.
- Added note on liveness property that can't be used with symmetry sets.
- Created different modules for types, functions and properties.
*)
---- MODULE KRaft ----

EXTENDS Integers, Naturals, FiniteSets, Sequences, TLC, Types, Functions, Properties

InitServerVars == /\ currentEpoch = [i \in Servers |-> 0]
                  /\ state        = [i \in Servers |-> Unattached]
                  /\ leader       = [i \in Servers |-> Nil]
                  /\ votedFor     = [i \in Servers |-> Nil]
                  /\ pendingFetch = [i \in Servers |-> Nil]
InitCandidateVars == /\ votesGranted = [i \in Servers |-> {}]
InitLeaderVars == /\ endOffset = [i \in Servers |-> [j \in Servers |-> 0]]
InitLogVars == /\ log           = [i \in Servers |-> <<>>]
               /\ highWatermark = [i \in Servers |-> 0]
InitAuxVars == /\ electionCtr = 0
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
\* it loses everything but its currentEpoch, votedFor and log
Restart(i) ==
    /\ restartCtr < MaxRestarts
    /\ state'         = [state EXCEPT ![i] = Follower]
    /\ leader'        = [leader EXCEPT ![i] = Nil]
    /\ votesGranted'  = [votesGranted EXCEPT ![i] = {}]
    /\ endOffset'     = [endOffset EXCEPT ![i] = [j \in Servers |-> 0]]
    /\ highWatermark' = [highWatermark EXCEPT ![i] = 0]
    /\ pendingFetch'  = [pendingFetch EXCEPT ![i] = Nil]
    /\ restartCtr'    = restartCtr + 1
    /\ UNCHANGED <<messages, currentEpoch, votedFor, log, acked, electionCtr>>

\* combined Timeout and RequestVote of the original spec to reduce state space
\* server i times out and starts a new election
\* sends a RequestVote request to all peers but not itself
RequestVote(i) ==
    /\ electionCtr < MaxElections
    /\ state[i] \in {Follower, Candidate, Unattached}
    /\ state'        = [state EXCEPT ![i] = Candidate]
    /\ currentEpoch' = [currentEpoch EXCEPT ![i] = currentEpoch[i] + 1]
    /\ leader'       = [leader EXCEPT ![i] = Nil]
    /\ votedFor'     = [votedFor EXCEPT ![i] = i] \* votes for itself
    /\ votesGranted' = [votesGranted EXCEPT ![i] = {i}] \* votes for itself
    /\ pendingFetch' = [pendingFetch EXCEPT ![i] = Nil]
    /\ electionCtr'  = electionCtr + 1
    /\ SendMultipleOnce(
           {[mtype          |-> RequestVoteRequest,
             mepoch         |-> currentEpoch[i] + 1,
             mlastLogEpoch  |-> LastEpoch(log[i]),
             mlastLogOffset |-> Len(log[i]),
             msource        |-> i,
             mdest          |-> j] : j \in Servers \ {i}})
    /\ UNCHANGED <<acked, leaderVars, logVars, restartCtr>>

\* server i receives a RequestVote request from server j
\* server i will vote for j if:
\* 1. epoch of j >= epoch of i
\* 2. last entry of i is <= to the last entry of j
\* 3. i has not already voted for a different server
HandleRequestVoteRequest ==
    \E m \in DOMAIN messages :
        /\ ReceivableMessage(m, RequestVoteRequest, AnyEpoch)
        /\ LET i     == m.mdest
               j     == m.msource
               error    == IF m.mepoch < currentEpoch[i]
                           THEN FencedLeaderEpoch
                           ELSE Nil
               state0   == IF m.mepoch > currentEpoch[i]
                           THEN TransitionToUnattached(m.mepoch)
                           ELSE NoTransition(i)
               logOk == CompareEntries(m.mlastLogOffset,
                                       m.mlastLogEpoch,
                                       Len(log[i]),
                                       LastEpoch(log[i])) >= 0
               grant == /\ \/ state0.state = Unattached
                           \/ /\ state0.state = Voted
                              /\ votedFor[i] = j
                        /\ logOk
               finalState == IF grant /\ state0.state = Unattached
                             THEN TransitionToVoted(i, m.mepoch, state0)
                             ELSE state0
            IN /\ IF error = Nil
                  THEN
                       /\ state' = [state EXCEPT ![i] = finalState.state]
                       /\ currentEpoch' = [currentEpoch EXCEPT ![i] = finalState.epoch]
                       /\ leader' = [leader EXCEPT ![i] = finalState.leader]
                       /\ \/ /\ grant
                             /\ votedFor' = [votedFor EXCEPT ![i] = j]
                          \/ /\ ~grant
                             /\ UNCHANGED votedFor
                       /\ IF state # state'
                          THEN pendingFetch' = [pendingFetch EXCEPT ![i] = Nil]
                          ELSE UNCHANGED pendingFetch
                       /\ Reply([mtype         |-> RequestVoteResponse,
                                 mepoch        |-> m.mepoch,
                                 mleader       |-> finalState.leader,
                                 mvoteGranted  |-> grant,
                                 merror        |-> Nil,
                                 msource       |-> i,
                                 mdest         |-> j], m)
                  ELSE /\ Reply([mtype         |-> RequestVoteResponse,
                                 mepoch        |-> currentEpoch[i],
                                 mleader       |-> leader[i],
                                 mvoteGranted  |-> FALSE,
                                 merror        |-> error,
                                 msource       |-> i,
                                 mdest         |-> j], m)
                       /\ UNCHANGED <<serverVars>>
               /\ UNCHANGED <<candidateVars, leaderVars, logVars, auxVars>>

\* server i receives a RequestVote response from server j
\* if the response is stale the server i will not register the vote either way
HandleRequestVoteResponse ==
    \E m \in DOMAIN messages :
        /\ ReceivableMessage(m, RequestVoteResponse, AnyEpoch)
        /\ LET i        == m.mdest
               j        == m.msource
               newState == MaybeHandleCommonResponse(i, m.mleader, m.mepoch, m.merror)
           IN
              /\ IF newState.handled = TRUE
                 THEN /\ state' = [state EXCEPT ![i] = newState.state]
                      /\ leader' = [leader EXCEPT ![i] = newState.leader]
                      /\ currentEpoch' = [currentEpoch EXCEPT ![i] = newState.epoch]
                      /\ UNCHANGED <<votesGranted>>
                 ELSE
                      /\ state[i] = Candidate
                      /\ \/ /\ m.mvoteGranted
                            /\ votesGranted' = [votesGranted EXCEPT ![i] =
                                                      votesGranted[i] \cup {j}]
                         \/ /\ ~m.mvoteGranted
                            /\ UNCHANGED <<votesGranted>>
                      /\ UNCHANGED <<state, leader, currentEpoch>>
              /\ Discard(m)
              /\ UNCHANGED <<votedFor, pendingFetch, leaderVars, logVars, auxVars>>

\* candidate i transitions to leader and notifies all peers of its leadership via the BeginQuorumRequest
BecomeLeader(i) ==
    /\ state[i] = Candidate
    /\ votesGranted[i] \in Quorum
    /\ state'  = [state EXCEPT ![i] = Leader]
    /\ leader' = [leader EXCEPT ![i] = i]
    /\ endOffset' = [endOffset EXCEPT ![i] = [j \in Servers |-> 0]]
    /\ SendMultipleOnce(
          {[mtype    |-> BeginQuorumRequest,
            mepoch   |-> currentEpoch[i],
            msource  |-> i,
            mdest    |-> j] : j \in Servers \ {i}})
    /\ UNCHANGED <<currentEpoch, votedFor, pendingFetch, candidateVars, auxVars, logVars>>

\* a server receives a BeginQuorumRequest and transitions to a follower unless the message is stale
HandleBeginQuorumRequest ==
    \E m \in DOMAIN messages :
        /\ ReceivableMessage(m, BeginQuorumRequest, AnyEpoch)
        /\ LET i        == m.mdest
               j        == m.msource
               error    == IF m.mepoch < currentEpoch[i]
                           THEN FencedLeaderEpoch
                           ELSE Nil
               newState == MaybeTransition(i, m.msource, m.mepoch)
           IN IF error = Nil
              THEN
                   /\ state' = [state EXCEPT ![i] = newState.state]
                   /\ leader' = [leader EXCEPT ![i] = newState.leader]
                   /\ currentEpoch' = [currentEpoch EXCEPT ![i] = newState.epoch]
                   /\ pendingFetch' = [pendingFetch EXCEPT ![i] = Nil]
                   /\ Reply([mtype       |-> BeginQuorumResponse,
                             mepoch      |-> m.mepoch,
                             msource     |-> i,
                             mdest       |-> j,
                             merror      |-> Nil], m)
              ELSE /\ Reply([mtype       |-> BeginQuorumResponse,
                             mepoch      |-> currentEpoch[i],
                             msource     |-> i,
                             mdest       |-> j,
                             merror      |-> error], m)
                   /\ UNCHANGED <<state, leader, currentEpoch, pendingFetch>>
        /\ UNCHANGED <<votedFor, log, candidateVars, leaderVars, highWatermark, auxVars>>

\* leader i receives a client request to add v to the log
ClientRequest(i, v) ==
    /\ state[i] = Leader
    /\ acked[v] = Nil \* prevent submitting the same value repeatedly
    /\ LET entry == [epoch |-> currentEpoch[i],
                     value |-> v]
           newLog == Append(log[i], entry)
       IN  /\ log' = [log EXCEPT ![i] = newLog]
           /\ acked' = [acked EXCEPT ![v] = FALSE]
    /\ UNCHANGED <<messages, serverVars, candidateVars, leaderVars, highWatermark, electionCtr, restartCtr>>

\* follower i sends leader j a FetchRequest
SendFetchRequest(i, j) ==
    /\ i # j
    /\ state[i] = Follower
    /\ leader[i] = j
    /\ pendingFetch[i] = Nil
    /\ LET lastLogOffset == Len(log[i])
           lastLogEpoch == IF lastLogOffset > 0
                           THEN log[i][lastLogOffset].epoch
                           ELSE 0
           fetchMsg     == [mtype             |-> FetchRequest,
                            mepoch            |-> currentEpoch[i],
                            mfetchOffset      |-> lastLogOffset,
                            mlastFetchedEpoch |-> lastLogEpoch,
                            msource           |-> i,
                            mdest             |-> j]
       IN /\ pendingFetch' = [pendingFetch EXCEPT ![i] = fetchMsg]
          /\ Send(fetchMsg)
    /\ UNCHANGED <<currentEpoch, state, votedFor, leader, candidateVars, leaderVars, logVars, auxVars>>

\* server i rejects a FetchRequest due to either:
\* - i is not a leader
\* - the message epoch is lower than the server epoch
\* - the message epoch is higher than the server epoch
RejectFetchRequest ==
    \E m \in DOMAIN messages :
        /\ ReceivableMessage(m, FetchRequest, AnyEpoch)
        /\ LET i                   == m.mdest
               j                   == m.msource
               error               == CASE state[i] # Leader -> NotLeader
                                        [] m.mepoch < currentEpoch[i] -> FencedLeaderEpoch
                                        [] m.mepoch > currentEpoch[i] -> UnknownLeader
                                        [] OTHER -> Nil
           IN  /\ error # Nil
               /\ Reply([mtype |-> FetchResponse,
                         mresult     |-> NotOk,
                         merror      |-> error,
                         mleader     |-> leader[i],
                         mepoch      |-> currentEpoch[i],
                         mhwm        |-> highWatermark[i],
                         msource     |-> i,
                         mdest       |-> j,
                         correlation |-> m], m)
               /\ UNCHANGED <<candidateVars, leaderVars, serverVars, logVars, auxVars>>

\* leader i receives a FetchRequest from an inconsistent log position so it responds with the highest
\* offset that matches the epoch of the follower fetch position so it can truncate its log and start
\* fetching from a consistent offset
DivergingFetchRequest ==
    \E m \in DOMAIN messages :
        /\ ReceivableMessage(m, FetchRequest, EqualEpoch)
        /\ LET i                   == m.mdest
               j                   == m.msource
               valid               == ValidFetchPosition(i, m)
               validOffsetAndEpoch == EndOffsetForEpoch(i, m.mlastFetchedEpoch)
           IN  /\ state[i] = Leader
               /\ ~valid
               /\ Reply([mtype               |-> FetchResponse,
                         mepoch              |-> currentEpoch[i],
                         mresult             |-> Diverging,
                         merror              |-> Nil,
                         mdivergingEpoch     |-> validOffsetAndEpoch.epoch,
                         mdivergingEndOffset |-> validOffsetAndEpoch.offset,
                         mleader             |-> leader[i],
                         mhwm                |-> highWatermark[i],
                         msource             |-> i,
                         mdest               |-> j,
                         correlation         |-> m], m)
               /\ UNCHANGED <<candidateVars, leaderVars, serverVars, logVars, auxVars>>

\* leader i receives a FetchRequest from a valid position and responds with an entry if there is one or an empty response if not
\* the leader updates the end offset of the fetching peer and advances the high watermark if it can
\* it can only advance the high watermark to an entry of the current epoch
NewHighwaterMark(i, newEndOffset) ==
    LET Agree(offset) == {i} \cup {k \in Servers :
                            /\ newEndOffset[k] >= offset }
        \* the maximum offsets for which a quorum agrees
        agreeOffsets  == {offset \in 1..Len(log[i]) :
                            Agree(offset) \in Quorum}
    IN
        IF /\ agreeOffsets # {}
           /\ log[i][Max(agreeOffsets)].epoch = currentEpoch[i]
        THEN
            Max(agreeOffsets)
        ELSE
            highWatermark[i]

\* server i is a leader and accepts a FetchRequest
AcceptFetchRequest ==
    \E m \in DOMAIN messages :
        /\ ReceivableMessage(m, FetchRequest, EqualEpoch)
        /\ LET i       == m.mdest
               j       == m.msource
               valid   == ValidFetchPosition(i, m)
               offset  == m.mfetchOffset + 1
               entries == IF offset > Len(log[i])
                          THEN <<>>
                          ELSE <<log[i][offset]>>
           IN
              /\ state[i] = Leader
              /\ valid
              /\ LET newEndOffset == [endOffset[i] EXCEPT ![j] = m.mfetchOffset]
                     newHwm == NewHighwaterMark(i, newEndOffset)
                 IN
                    /\ endOffset' = [endOffset EXCEPT ![i] = newEndOffset]
                    /\ highWatermark' = [highWatermark EXCEPT ![i] = newHwm]
                    /\ acked' = [v \in Values |->
                                    IF acked[v] = FALSE
                                    THEN v \in { log[i][ind].value : ind \in highWatermark[i]+1..newHwm }
                                    ELSE acked[v]]
                    /\ Reply([mtype       |-> FetchResponse,
                              mepoch      |-> currentEpoch[i],
                              mleader     |-> leader[i],
                              mresult     |-> Ok,
                              merror      |-> Nil,
                              mentries    |-> entries,
                              mhwm        |-> Min({newHwm, offset}),
                              msource     |-> i,
                              mdest       |-> j,
                              correlation |-> m], m)
                    /\ UNCHANGED <<candidateVars, currentEpoch, log, state, votedFor,
                                   pendingFetch, leader, electionCtr, restartCtr>>

\* follower i receives a valid Fetch response from server j and appends any entries to its log and updates its high watermark
HandleSuccessFetchResponse ==
    \E m \in DOMAIN messages :
        /\ ReceivableMessage(m, FetchResponse, AnyEpoch)
        /\ LET i     == m.mdest
               j     == m.msource
               newState == MaybeHandleCommonResponse(i, m.mleader, m.mepoch, m.merror)
           IN /\ newState.handled = FALSE
              /\ pendingFetch[i] = m.correlation
              /\ m.mresult = Ok
              /\ highWatermark'  = [highWatermark  EXCEPT ![i] = m.mhwm]
              /\ IF Len(m.mentries) > 0
                 THEN log' = [log EXCEPT ![i] = Append(@, m.mentries[1])]
                 ELSE UNCHANGED log
              /\ pendingFetch' = [pendingFetch EXCEPT ![i] = Nil]
              /\ Discard(m)
              /\ UNCHANGED <<currentEpoch, state, leader, votedFor, candidateVars, endOffset, auxVars>>

\* follower i receives a Fetch response from server j and the response indicates that the fetch position is inconsistent
\* the response includes the highest offset of the last common epoch the leader and follower share, so the follower truncates
\* its log to the highest entry it has at or below that point which will be the highest common entry that the leader and follower share
\* after this it can send another FetchRequest to the leader from a valid fetch position
HandleDivergingFetchResponse ==
    \E m \in DOMAIN messages :
        /\ ReceivableMessage(m, FetchResponse, AnyEpoch)
        /\ LET i        == m.mdest
               j        == m.msource
               newState == MaybeHandleCommonResponse(i, m.mleader, m.mepoch, m.merror)
           IN
              /\ newState.handled = FALSE
              /\ pendingFetch[i] = m.correlation
              /\ m.mresult = Diverging
              /\ log' = [log EXCEPT ![i] = TruncateLog(i, m)]
              /\ pendingFetch' = [pendingFetch EXCEPT ![i] = Nil]
              /\ Discard(m)
        /\ UNCHANGED <<currentEpoch, state, leader, votedFor, candidateVars, leaderVars,
                       highWatermark, auxVars>>

\* server i receives a FetchResponse with an error from server j
\* depending on the error, the follower may transition to being unattached or being the follower of a new leader that it was no aware of
HandleErrorFetchResponse ==
    \E m \in DOMAIN messages :
        /\ ReceivableMessage(m, FetchResponse, AnyEpoch)
        /\ LET i        == m.mdest
               j        == m.msource
               newState == MaybeHandleCommonResponse(i, m.mleader, m.mepoch, m.merror)
           IN
              /\ newState.handled = TRUE
              /\ pendingFetch[i] = m.correlation
              /\ state' = [state EXCEPT ![i] = newState.state]
              /\ leader' = [leader EXCEPT ![i] = newState.leader]
              /\ currentEpoch' = [currentEpoch EXCEPT ![i] = newState.epoch]
              /\ pendingFetch' = [pendingFetch EXCEPT ![i] = Nil]
              /\ Discard(m)
        /\ UNCHANGED <<votedFor, candidateVars, leaderVars, logVars, auxVars>>

----

Next ==
    \/ \E i \in Servers : Restart(i)
    \* elections
    \/ \E i \in Servers : RequestVote(i)
    \/ HandleRequestVoteRequest
    \/ HandleRequestVoteResponse
    \/ \E i \in Servers : BecomeLeader(i)
    \* leader actions
    \/ \E i \in Servers, v \in Values : ClientRequest(i, v)
    \/ RejectFetchRequest
    \/ DivergingFetchRequest
    \/ AcceptFetchRequest
    \* follower actions
    \/ HandleBeginQuorumRequest
    \/ \E i,j \in Servers : SendFetchRequest(i, j)
    \/ HandleSuccessFetchResponse
    \/ HandleDivergingFetchResponse
    \/ HandleErrorFetchResponse
    \*\/ \E m \in DOMAIN messages : DuplicateMessage(m)
    \*\/ \E m \in DOMAIN messages : DropMessage(m)

Spec ==
    Init /\ [][Next]_vars

====

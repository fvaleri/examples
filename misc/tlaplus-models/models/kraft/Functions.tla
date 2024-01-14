---- MODULE Functions ----
EXTENDS Integers, Naturals, FiniteSets, Sequences, TLC, Types

\* the epoch of the last entry in a log, or 0 if the log is empty
LastEpoch(xlog) == IF Len(xlog) = 0 THEN 0 ELSE xlog[Len(xlog)].epoch

\* send the message even if an identical one was previously sent
\* this can happen with FetchRequests
_SendNoRestriction(m) ==
    \/ /\ m \notin DOMAIN messages
       /\ messages' = messages @@ (m :> 1)
    \/ /\ m \in DOMAIN messages
       /\ messages' = [messages EXCEPT ![m] = @ + 1]

\* will only send the message if it hasn't been sent before
\* basically disables the parent action once sent
\* allows us to prevent infinite sending without adding an extra variable
_SendOnce(m) ==
    /\ m \notin DOMAIN messages
    /\ messages' = messages @@ (m :> 1)

\* add a message to the bag of messages
\* note 1: to prevent infinite cycles, we allow the sending some types of message once
\* in this specification we do not need retries, each send is implicitly retried forever as its delivery count remains 1 until processed
\* note 2: a message can only match an existing message if it is identical (all fields)
Send(m) ==
    IF \/ m.mtype = RequestVoteRequest
       \/ m.mtype = BeginQuorumRequest
    THEN _SendOnce(m)
    ELSE _SendNoRestriction(m)

\* will only send the messages if it hasn't done so before
\* basically disables the parent action once sent
\* again, retries are implicit here
SendMultipleOnce(msgs) ==
    /\ \A m \in msgs : m \notin DOMAIN messages
    /\ messages' = messages @@ [msg \in msgs |-> 1]

\* explicit duplicate operator for when we purposefully want message duplication
Duplicate(m) ==
    /\ m \in DOMAIN messages
    /\ messages' = [messages EXCEPT ![m] = @ + 1]

\* remove a message from the bag of messages
\* used when a server is done processing a message
Discard(m) ==
    /\ m \in DOMAIN messages
    /\ messages[m] > 0 \* message must exist
    /\ messages' = [messages EXCEPT ![m] = @ - 1]

\* combination of Send and Discard.
\* to prevent infinite empty fetches, we don't allow two identical fetch responses
\* if an empty fetch response was previously sent, then only when something has changed such as the HWM will a response be sendable
Reply(response, request) ==
    /\ messages[request] > 0 \* request must exist
    /\ \/ /\ response \notin DOMAIN messages \* response does not exist, so add it
          /\ messages' = [messages EXCEPT ![request] = @ - 1] @@ (response :> 1)
       \/ /\ response \in DOMAIN messages \* response was sent previously, so increment delivery counter
          /\ response.mtype # FetchResponse
          /\ messages' = [messages EXCEPT ![request] = @ - 1,
                                          ![response] = @ + 1]

\* the message is of the type and has a matching epoch
ReceivableMessage(m, mtype, epoch_match) ==
    /\ messages[m] > 0
    /\ m.mtype = mtype
    /\ \/ epoch_match = AnyEpoch
       \/ /\ epoch_match = EqualEpoch
          /\ m.mepoch = currentEpoch[m.mdest]

\* return the minimum value from a set, or undefined if the set is empty
Min(s) == CHOOSE x \in s : \A y \in s : x <= y
\* return the maximum value from a set, or undefined if the set is empty
Max(s) == CHOOSE x \in s : \A y \in s : x >= y

\* compares two entries, with epoch taking precedence
\* offset only matters when both have the same epoch
\* when entry1 > entry2 then 1
\* when entry1 = entry2 then 0
\* when entry1 < entry2 then 1
CompareEntries(offset1, epoch1, offset2, epoch2) ==
    CASE epoch1 > epoch2 -> 1
      [] epoch1 = epoch2 /\ offset1 > offset2 -> 1
      [] epoch1 = epoch2 /\ offset1 = offset2 -> 0
      [] OTHER -> -1

\* finds the highest offset in the log which is <= to the supplied epoch and its last offset
HighestCommonOffset(i, endOffsetForEpoch, epoch) ==
      \* 1) the log is empty so no common offset
    CASE log[i] = <<>> ->
            [offset |-> 0, epoch |-> 0]
      \* 2) there is no lower entry in the log, so no common offset
      [] ~\E offset \in DOMAIN log[i] :
            CompareEntries(offset, log[i][offset].epoch,
                           endOffsetForEpoch, epoch) <= 0 ->
            [offset |-> 0, epoch |-> 0]
      [] OTHER ->
      \* there is a common entry, so choose the highest one
            LET offset == CHOOSE offset \in DOMAIN log[i] :
                            /\ CompareEntries(offset, log[i][offset].epoch,
                                              endOffsetForEpoch, epoch) <= 0
                            /\ ~\E offset2 \in DOMAIN log[i] :
                                /\ CompareEntries(offset2, log[i][offset2].epoch,
                                                  endOffsetForEpoch, epoch) <= 0
                                /\ offset2 > offset
            IN [offset |-> offset, epoch |-> log[i][offset].epoch]

\* create a new log, truncated to the highest common entry
TruncateLog(i, m) ==
    LET highestCommonOffset == HighestCommonOffset(i,
                                                   m.mdivergingEndOffset,
                                                   m.mdivergingEpoch)
    IN IF highestCommonOffset.offset = 0
       THEN <<>>
       ELSE [offset \in 1..highestCommonOffset.offset |-> log[i][offset]]

\* the highest offset in the leader's log that has the same or lower epoch
EndOffsetForEpoch(i, lastFetchedEpoch) ==
      \* 1. the log is empty so no end offset
    CASE log[i] = <<>> ->
            [offset |-> 0, epoch |-> 0]
      \* 2. there is no entry at or below the epoch in the log, so no end offset
      [] ~\E offset \in DOMAIN log[i] :
            log[i][offset].epoch <= lastFetchedEpoch ->
            [offset |-> 0, epoch |-> 0]
      \* 3. there is an entry at or below the epoch in the log, so return the highest one
      [] OTHER ->
            LET offset == CHOOSE offset \in DOMAIN log[i] :
                            /\ log[i][offset].epoch <= lastFetchedEpoch
                            /\ ~\E offset2 \in DOMAIN log[i] :
                                /\ log[i][offset2].epoch <= lastFetchedEpoch
                                /\ offset2 > offset
            IN [offset |-> offset, epoch |-> log[i][offset].epoch]

\* TRUE if the fetch position of the follower is consistent with the log of the leader
ValidFetchPosition(i, m) ==
    \/ /\ m.mfetchOffset = 0
       /\ m.mlastFetchedEpoch = 0
    \/ LET endOffsetAndEpoch == EndOffsetForEpoch(i, m.mlastFetchedEpoch)
       IN /\ m.mfetchOffset <= endOffsetAndEpoch.offset
          /\ m.mlastFetchedEpoch = endOffsetAndEpoch.epoch

\* TRUE if server i and the peer have a consistent view on leadership, FALSE if not
HasConsistentLeader(i, leaderId, epoch) ==
    IF leaderId = i
    THEN \* if the peer thinks I am leader, and I am really leader
         \* then TRUE, else FALSE
         state[i] = Leader
    ELSE \* either the peer doesn't know there is a leader, or this
         \* node doesn't know a leader, or both agree on the same leader,
         \* or they have different epochs
         \/ epoch # currentEpoch[i]
         \/ leaderId = Nil
         \/ leader[i] = Nil
         \/ leader[i] = leaderId

SetIllegalState ==
    [state |-> IllegalState, epoch |-> 0, leader |-> Nil]

NoTransition(i) ==
    [state |-> state[i], epoch |-> currentEpoch[i], leader |-> leader[i]]

TransitionToVoted(i, epoch, state0) ==
    IF /\ state0.epoch = epoch
       /\ state0.state # Unattached
    THEN SetIllegalState
    ELSE [state |-> Voted, epoch |-> epoch, leader |-> Nil]

TransitionToUnattached(epoch) ==
    [state |-> Unattached, epoch |-> epoch, leader |-> Nil]

TransitionToFollower(i, leaderId, epoch) ==
    IF /\ currentEpoch[i] = epoch
       /\ \/ state[i] = Follower
          \/ state[i] = Leader
    THEN SetIllegalState
    ELSE [state |-> Follower, epoch |-> epoch, leader |-> leaderId]

MaybeTransition(i, leaderId, epoch) ==
    CASE ~HasConsistentLeader(i, leaderId, epoch) ->
            SetIllegalState
      [] epoch > currentEpoch[i] ->
            \* the epoch of the node is stale, become a follower
            \* if the request contained the leader id, else become
            \* unattached
            IF leaderId = Nil
            THEN TransitionToUnattached(epoch)
            ELSE TransitionToFollower(i, leaderId, epoch)
      [] leaderId # Nil /\ leader[i] = Nil ->
            \* the request contained a leader id and this node does not know
            \* of a leader, so become a follower of that leader
            TransitionToFollower(i, leaderId, epoch)
      [] OTHER ->
            \* no changes
            NoTransition(i)

MaybeHandleCommonResponse(i, leaderId, epoch, errors) ==
    CASE epoch < currentEpoch[i] ->
                \* stale epoch, do nothing
                [state |-> state[i],
                 epoch |-> currentEpoch[i],
                 leader |-> leader[i],
                 handled |-> TRUE]
      [] epoch > currentEpoch[i] \/ errors # Nil ->
                \* higher epoch or an error
                MaybeTransition(i, leaderId, epoch) @@ [handled |-> TRUE]
      [] /\ epoch = currentEpoch[i]
         /\ leaderId # Nil
         /\ leader[i] = Nil ->
                \* become a follower
                [state   |-> Follower,
                 leader  |-> leaderId,
                 epoch   |-> currentEpoch[i],
                 handled |-> TRUE]
      [] OTHER ->
                \* no changes to state or leadership
                [state   |-> state[i],
                 epoch   |-> currentEpoch[i],
                 leader  |-> leader[i],
                 handled |-> FALSE]

\* the network duplicates a message
\* there is no state-space control for this action, it causes infinite state space
DuplicateMessage(m) ==
    /\ Duplicate(m)
    /\ UNCHANGED <<serverVars, candidateVars, leaderVars, logVars, auxVars>>

\* the network drops a message
\* in reality is not required as the specification does not force any server to receive a message, so we already get this for free
DropMessage(m) ==
    /\ Discard(m)
    /\ UNCHANGED <<serverVars, candidateVars, leaderVars, logVars, auxVars>>

MinHighWatermark(s1, s2) ==
    IF highWatermark[s1] < highWatermark[s2]
    THEN highWatermark[s1]
    ELSE highWatermark[s2]

ValueInServerLog(i, v) ==
    \E index \in DOMAIN log[i] :
        log[i][index].value = v

ValueAllOrNothing(v) ==
    IF /\ electionCtr = MaxElections
       /\ ~\E i \in Servers : state[i] = Leader
    THEN TRUE
    ELSE \/ \A i \in Servers : ValueInServerLog(i, v)
         \/ ~\E i \in Servers : ValueInServerLog(i, v)

====

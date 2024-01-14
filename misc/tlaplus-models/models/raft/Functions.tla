---- MODULE Functions ----

EXTENDS Naturals, FiniteSets, Sequences, TLC, Types

\* the term of the last entry in a log, or 0 if the log is empty
LastTerm(xlog) == IF Len(xlog) = 0 THEN 0 ELSE xlog[Len(xlog)].term

\* send the message whether it already exists or not
SendNoRestriction(m) ==
    IF m \in DOMAIN messages
    THEN messages' = [messages EXCEPT ![m] = @ + 1]
    ELSE messages' = messages @@ (m :> 1)

\* will only send the message if it hasn't been sent before
\* basically disables the parent action once sent
SendOnce(m) ==
    /\ m \notin DOMAIN messages
    /\ messages' = messages @@ (m :> 1)

\* add a message to the bag of messages
\* note 1: to prevent infinite cycles, empty AppendEntriesRequest messages can only be sent once
\* note 2: a message can only match an existing message if it is identical (all fields)
Send(m) ==
    IF /\ m.mtype = AppendEntriesRequest
       /\ m.mentries = <<>>
    THEN SendOnce(m)
    ELSE SendNoRestriction(m)

\* will only send the messages if it hasn't done so before
\* basically disables the parent action once sent
SendMultipleOnce(msgs) ==
    /\ \A m \in msgs : m \notin DOMAIN messages
    /\ messages' = messages @@ [msg \in msgs |-> 1]

\* explicit duplicate operator for when we purposefully want message duplication
Duplicate(m) ==
    /\ m \in DOMAIN messages
    /\ messages' = [messages EXCEPT ![m] = @ + 1]

\* remove a message from the bag of messages. Used when a server is done processing a message
Discard(m) ==
    /\ m \in DOMAIN messages
    /\ messages[m] > 0 \* message must exist
    /\ messages' = [messages EXCEPT ![m] = @ - 1]

\* combination of Send and Discard
Reply(response, request) ==
    /\ messages[request] > 0 \* request must exist
    /\ \/ /\ response \notin DOMAIN messages \* response does not exist, so add it
          /\ messages' = [messages EXCEPT ![request] = @ - 1] @@ (response :> 1)
       \/ /\ response \in DOMAIN messages \* response was sent previously, so increment delivery counter
          /\ messages' = [messages EXCEPT ![request] = @ - 1,
                                          ![response] = @ + 1]

\* the message is of the type and has a matching term
\* messages with a higher term are handled by the action UpdateTerm
ReceivableMessage(m, mtype, termMatch) ==
    /\ messages[m] > 0
    /\ m.mtype = mtype
    /\ \/ /\ termMatch = EqualTerm
          /\ m.mterm = currentTerm[m.mdest]
       \/ /\ termMatch = LessOrEqualTerm
          /\ m.mterm <= currentTerm[m.mdest]

\* return the minimum value from a set, or undefined if the set is empty
Min(s) == CHOOSE x \in s : \A y \in s : x <= y
\* return the maximum value from a set, or undefined if the set is empty
Max(s) == CHOOSE x \in s : \A y \in s : x >= y

\* checks if message m is compatible with the log of server i
LogOk(i, m) ==
    \/ m.mprevLogIndex = 0
    \/ /\ m.mprevLogIndex > 0
       /\ m.mprevLogIndex <= Len(log[i])
       /\ m.mprevLogTerm = log[i][m.mprevLogIndex].term

\* checks if the message previous log index is what is expected
CanAppend(m, i) ==
    /\ m.mentries # <<>>
    /\ Len(log[i]) = m.mprevLogIndex

\* truncate in two cases:
\* 1. the last log entry index is >= than the entry being received
\* 2. this is an empty RPC and the last log entry index is > than the previous log entry received
NeedsTruncation(m, i, index) ==
    \/ /\ m.mentries # <<>>
       /\ Len(log[i]) >= index
    \/ /\ m.mentries = <<>>
       /\ Len(log[i]) > m.mprevLogIndex

\* truncate the log
TruncateLog(m, i) ==
    [index \in 1..m.mprevLogIndex |-> log[i][index]]

\* the network duplicates a message
\* there is no state-space control for this action, it causes infinite state space
DuplicateMessage(m) ==
    /\ Duplicate(m)
    /\ UNCHANGED <<serverVars, candidateVars, leaderVars, logVars, auxVars>>

\* the network drops a message
\* the specification does not force any server to receive a message, so we already get this for free
DropMessage(m) ==
    /\ Discard(m)
    /\ UNCHANGED <<serverVars, candidateVars, leaderVars, logVars, auxVars>>

MinCommitIndex(s1, s2) ==
    IF commitIndex[s1] < commitIndex[s2]
    THEN commitIndex[s1]
    ELSE commitIndex[s2]

ValueInServerLog(i, v) == \E index \in DOMAIN log[i] : log[i][index].value = v

ValueAllOrNothing(v) ==
    IF /\ electionCtr = MaxElections
       /\ ~\E i \in Servers : state[i] = Leader
    THEN TRUE
    ELSE \/ \A i \in Servers : ValueInServerLog(i, v)
         \/ ~\E i \in Servers : ValueInServerLog(i, v)

====

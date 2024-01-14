---- MODULE Properties ----

EXTENDS Naturals, FiniteSets, Sequences, TLC, Types, Functions

\* invariant: the log index is consistent across all servers
\* on those servers whose commitIndex is equal or higher than the index
NoLogDivergence ==
    \A s1, s2 \in Servers :
        IF s1 = s2
        THEN TRUE
        ELSE
            LET lowestCommonCi == MinCommitIndex(s1, s2)
            IN IF lowestCommonCi > 0
               THEN \A index \in 1..lowestCommonCi : log[s1][index] = log[s2][index]
               ELSE TRUE

\* invariant: a non-stale leader cannot be missing an acknowledged value
LeaderHasAllAckedValues ==
    \* for every acknowledged value
    \A v \in Values :
        IF acked[v] = TRUE
        THEN
            \* there does not exist a server that
            ~\E i \in Servers :
                \* is a leader
                /\ state[i] = Leader
                \* and which is the newest leader (aka not stale)
                /\ ~\E l \in Servers :
                    /\ l # i
                    /\ currentTerm[l] > currentTerm[i]
                \* and that is missing the value
                /\ ~\E index \in DOMAIN log[i] :
                    log[i][index].value = v
        ELSE TRUE

\* invariant: there cannot be a committed entry that is not at majority quorum
\* don't use this invariant when allowing data loss on a server
CommittedEntriesReachMajority ==
    IF \E i \in Servers : state[i] = Leader /\ commitIndex[i] > 0
    THEN \E i \in Servers :
           /\ state[i] = Leader
           /\ commitIndex[i] > 0
           /\ \E quorum \in SUBSET Servers :
               /\ Cardinality(quorum) = (Cardinality(Servers) \div 2) + 1
               /\ i \in quorum
               /\ \A j \in quorum :
                   /\ Len(log[j]) >= commitIndex[i]
                   /\ log[j][commitIndex[i]] = log[i][commitIndex[i]]
    ELSE TRUE

\* liveness: a client value will either get committed and be fully replicated or it will be truncated
\* and not be found on any server log
\* note that due to the number of elections being limited, the last possible election could fail and prevent progress,
\* so this liveness formula only applies in cases a behaviour does not end with all elections used up and no elected leader
\* additionally, you cannot use symmetry sets if the set is used in a liveness property
ValuesNotStuck == \A v \in Values : []<>ValueAllOrNothing(v)

====

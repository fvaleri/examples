(*
Author: Jack Vanlightly.
Gossa is a gossip algorithm for nodes in a cluster to share information about which nodes are alive and dead in a static group.
Each node periodically exchanges its entire knowledge with a random peer.
A node can start, die, correctly or incorrectly detect that a peer has died and update its state for that peer to dead.

A 3-nodes cluster can be initialized like this:

running = [1 = TRUE, 2 = TRUE, 3 = TRUE]
peer_state == [
    1 = [1 = [1, Alive], 2 = [1, Alive], 3 = [1, Alive]],
    2 = [1 = [1, Alive], 2 = [1, Alive], 3 = [1, Alive]],
    3 = [1 = [1, Alive], 2 = [1, Alive], 3 = [1, Alive]]
]
*)
---- MODULE Gossa ----

EXTENDS Naturals

CONSTANTS NodeCount,
          MaxGeneration
ASSUME
    /\ NodeCount > 0
    /\ MaxGeneration > 0

VARIABLES running,
          peer_status,
          generation
vars == <<running, peer_status, generation>>

Nodes == 1..NodeCount

\* Node peer states.
Alive == 1
Dead == 2

Init ==
    /\ running     = [node \in Nodes |-> TRUE]
    /\ peer_status = [node1 \in Nodes |->
                        [node2 \in Nodes |->
                            [generation |-> 1,
                             status     |-> Alive]]]
    /\ generation  = [node \in Nodes |-> 1]

----

\* fix 1: we use precedence (Dead > Alive), to prevent cycles due to nodes contesting whether a peer is dead or alive
\* fix 2: we use generation state to prevent a falsely accused dead node unable to refute its deadness as no-one would pay attention to it
Highest(peer1, peer2) ==
    CASE peer1.generation > peer2.generation -> peer1
      [] peer2.generation > peer1.generation -> peer2
      [] peer1.status > peer2.status -> peer1
      [] OTHER -> peer2

MergePeerState(local_node, local_ps, remote_ps) ==
    [node \in Nodes |->
        CASE
          \* this is node is a peer so we select the higher precedence of the two
             node /= local_node ->
                Highest(local_ps[node], remote_ps[node])
          \* this node is the local node and the remote believes it is dead so it increments its generation
          [] /\ remote_ps[node].generation = generation[node]
             /\ remote_ps[node].status = Dead ->
                [generation |-> generation[node] + 1,
                 status     |-> Alive]
          \* this node is the local node and the remote believes it is alive so it keeps its local knowledge of itself
          [] OTHER ->
                local_ps[node]]

Gossip ==
    \E source, dest \in Nodes :
        LET gossip_sent      == peer_status[source]
            merged_on_dest   == MergePeerState(
                                    dest,
                                    peer_status[dest],
                                    gossip_sent)
            gossip_replied   == merged_on_dest
            merged_on_source == MergePeerState(
                                    source,
                                    peer_status[source],
                                    gossip_replied)
            new_gen_dest     == merged_on_dest[dest].generation
            new_gen_source   == merged_on_source[source].generation
        IN
            /\ running[source] = TRUE
            /\ running[dest] = TRUE
            /\ peer_status[source] /= peer_status[dest]
            /\ peer_status' = [peer_status EXCEPT ![dest]   = merged_on_dest,
                                                  ![source] = merged_on_source]
            /\ generation' = [generation EXCEPT ![dest]   = new_gen_dest,
                                                ![source] = new_gen_source]
            /\ UNCHANGED running

Die ==
    \E node \in Nodes :
        /\ running[node] = TRUE
        /\ running' = [running EXCEPT ![node] = FALSE]
        /\ UNCHANGED <<peer_status, generation>>

Start ==
    \E node \in Nodes :
        /\ running[node] = FALSE
        /\ generation[node] < MaxGeneration \* limit generation
        /\ LET new_gen == generation[node] + 1
           IN
              /\ running' = [running EXCEPT ![node] = TRUE]
              /\ generation' = [generation EXCEPT ![node] = new_gen]
              /\ peer_status' = [peer_status EXCEPT ![node][node].generation = new_gen]

CorrectlyDetectDeadNode ==
    \E local, remote \in Nodes :
        /\ local /= remote
        /\ running[local] = TRUE
        /\ running[remote] = FALSE
        /\ peer_status[local][remote].status = Alive
        /\ peer_status' = [peer_status EXCEPT ![local][remote].status = Dead]
        /\ UNCHANGED <<running, generation>>

FalselyDetectDeadNode ==
    \E local, remote \in Nodes :
        /\ local /= remote
        /\ running[local] = TRUE
        /\ running[remote] = TRUE
        /\ generation[remote] <= MaxGeneration \* limit generation
        /\ peer_status[local][remote].status = Alive
        /\ peer_status' = [peer_status EXCEPT ![local][remote].status = Dead]
        /\ UNCHANGED <<running, generation>>

----

Next ==
    \/ Gossip
    \/ Die
    \/ CorrectlyDetectDeadNode
    \/ FalselyDetectDeadNode
    \/ Start

Fairness ==
    WF_vars(\/ Gossip
            \/ CorrectlyDetectDeadNode)

Spec ==
    Init /\ [][Next]_vars /\ Fairness

----

\* invariant: type correctness
TypeOK ==
    /\ running \in [Nodes -> BOOLEAN]
    /\ peer_status \in [Nodes ->
                           [Nodes ->
                                [generation: Nat,
                                 status: {Alive, Dead}]]]
    /\ generation \in [Nodes -> Nat]

\* invariant: valid generation
ValidGeneration ==
    ~\E node \in Nodes :
        /\ generation[node] /= peer_status[node][node].generation

\* liveness: eventually, all nodes should converge on the same knowledge
Converged ==
    ~\E local, remote \in Nodes :
       /\ running[local] = TRUE
       /\ \/ /\ running[remote] = TRUE
             /\ \/ peer_status[local][remote].status = Dead
                \/ peer_status[local][remote].generation /= generation[remote]
          \/ /\ running[remote] = FALSE
             /\ peer_status[local][remote].status = Alive

\* a diverged system leads to a converged system
EventuallyConverges ==
    ~Converged ~> Converged

====

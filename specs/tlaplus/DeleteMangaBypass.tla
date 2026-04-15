---- MODULE DeleteMangaBypass ----
EXTENDS Naturals

CONSTANTS Target, Other

VARIABLES queue, lock, deleteState, reorderState

StaleSnapshot == {Target, Other}

Init ==
    /\ queue = {Target, Other}
    /\ lock = "none"
    /\ deleteState = "waiting"
    /\ reorderState = "waiting"

\* deleteManga(removeQueued=true) enters queueMutex and removes target.
DeleteAcquire ==
    /\ lock = "none"
    /\ deleteState = "waiting"
    /\ lock' = "delete"
    /\ deleteState' = "removing"
    /\ UNCHANGED <<queue, reorderState>>

DeleteRemove ==
    /\ lock = "delete"
    /\ deleteState = "removing"
    /\ queue' = queue \ {Target}
    /\ deleteState' = "done"
    /\ lock' = "none"
    /\ UNCHANGED reorderState

\* reorderQueue applies a stale snapshot, but normalized order only keeps IDs that
\* still exist in current queue.
ReorderApplyStale ==
    /\ lock = "none"
    /\ deleteState = "done"
    /\ reorderState = "waiting"
    /\ queue' = (StaleSnapshot \cap queue) \cup (queue \ StaleSnapshot)
    /\ reorderState' = "done"
    /\ lock' = "none"
    /\ UNCHANGED deleteState

Stutter ==
    UNCHANGED <<queue, lock, deleteState, reorderState>>

Next ==
    \/ DeleteAcquire
    \/ DeleteRemove
    \/ ReorderApplyStale
    \/ Stutter

TypeOk ==
    /\ queue \subseteq {Target, Other}
    /\ lock \in {"none", "delete"}
    /\ deleteState \in {"waiting", "removing", "done"}
    /\ reorderState \in {"waiting", "done"}

NoResurrectionAfterDelete ==
    deleteState = "done" => ~(Target \in queue)

Spec ==
    Init /\ [][Next]_<<queue, lock, deleteState, reorderState>>

====

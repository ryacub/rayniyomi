---- MODULE StartNowRemoveRace ----
EXTENDS Naturals

CONSTANT Target

VARIABLES queue, lock, startState, removeState

Init ==
    /\ queue = {}
    /\ lock = "start"
    /\ startState = "lookup"
    /\ removeState = "waiting"

\* Model the overlapping call pattern this ticket fixes:
\* startDownloadNow enters the mutex first, removeFromQueueSafely is queued behind it.
StartWithinLock ==
    /\ lock = "start"
    /\ startState = "lookup"
    /\ startState' = "moved"
    /\ queue' \in {queue, queue \cup {Target}}
    /\ UNCHANGED <<lock, removeState>>

StartReleasesLock ==
    /\ lock = "start"
    /\ startState = "moved"
    /\ startState' = "done"
    /\ lock' = "none"
    /\ UNCHANGED <<queue, removeState>>

RemoveAfterStart ==
    /\ lock = "none"
    /\ removeState = "waiting"
    /\ lock' = "none"
    /\ removeState' = "done"
    /\ queue' = queue \ {Target}
    /\ UNCHANGED startState

Stutter ==
    UNCHANGED <<queue, lock, startState, removeState>>

Next ==
    \/ StartWithinLock
    \/ StartReleasesLock
    \/ RemoveAfterStart
    \/ Stutter

TypeOk ==
    /\ queue \subseteq {Target}
    /\ lock \in {"none", "start"}
    /\ startState \in {"lookup", "moved", "done"}
    /\ removeState \in {"waiting", "done"}

NoReinsertAfterRemove ==
    removeState = "done" => ~(Target \in queue)

Spec ==
    Init /\ [][Next]_<<queue, lock, startState, removeState>>

====

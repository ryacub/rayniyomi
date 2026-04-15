---- MODULE StartNowRemoveRace ----
EXTENDS Naturals

CONSTANT Target

VARIABLES queue, removed

Init ==
    /\ queue = {}
    /\ removed = FALSE

StartNow ==
    /\ ~removed
    /\ queue' = queue \cup {Target}
    /\ UNCHANGED removed

Remove ==
    /\ removed' = TRUE
    /\ queue' = queue \ {Target}

Next ==
    \/ StartNow
    \/ Remove

NoReinsertAfterRemove ==
    removed => ~(Target \in queue)

Spec ==
    Init /\ [][Next]_<<queue, removed>>

====

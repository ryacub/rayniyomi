------------------------------ MODULE BackupProgressRace ------------------------------
EXTENDS Naturals, FiniteSets

CONSTANT Workers

VARIABLES progress, done

Init ==
    /\ progress = 0
    /\ done = {}

AtomicIncrement(w) ==
    /\ w \in Workers
    /\ w \notin done
    /\ done' = done \cup {w}
    /\ progress' = progress + 1

Next == \E w \in Workers : AtomicIncrement(w)

AllDone == done = Workers

ProgressMatchesDone == progress = Cardinality(done)
CompletionExact == AllDone => progress = Cardinality(Workers)

Spec == Init /\ [][Next]_<<progress, done>> /\ WF_<<progress, done>>(Next)

THEOREM Spec => []ProgressMatchesDone
=======================================================================================

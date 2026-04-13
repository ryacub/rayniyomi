------------------------------ MODULE BatteryPrompt ------------------------------
EXTENDS Naturals

VARIABLES promptShown, promptEmitCount

Init ==
    /\ promptShown = FALSE
    /\ promptEmitCount = 0

QueueDownload ==
    IF ~promptShown
        THEN /\ promptShown' = TRUE
             /\ promptEmitCount' = promptEmitCount + 1
        ELSE /\ promptShown' = promptShown
             /\ promptEmitCount' = promptEmitCount

Next == QueueDownload

Spec == Init /\ [][Next]_<<promptShown, promptEmitCount>>

PromptEmittedAtMostOnce == promptEmitCount <= 1

=============================================================================

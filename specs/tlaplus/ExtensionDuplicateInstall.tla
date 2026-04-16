---- MODULE ExtensionDuplicateInstall ----
EXTENDS Naturals, TLC

CONSTANT PACKAGE

VARIABLES mapEntry, dl1Running, dl2Running, c1State, c2State

Init ==
  /\ mapEntry = "none"
  /\ dl1Running = FALSE
  /\ dl2Running = FALSE
  /\ c1State = "idle"
  /\ c2State = "idle"

\* Old racy behavior model:
\* both callers can observe empty map and both enqueue.
C1_Read ==
  /\ c1State = "idle"
  /\ c1State' = "read"
  /\ UNCHANGED << mapEntry, dl1Running, dl2Running, c2State >>

C2_Read ==
  /\ c2State = "idle"
  /\ c2State' = "read"
  /\ UNCHANGED << mapEntry, dl1Running, dl2Running, c1State >>

C1_Enqueue ==
  /\ c1State = "read"
  /\ dl1Running' = TRUE
  /\ mapEntry' = "dl1"
  /\ c1State' = "done"
  /\ UNCHANGED << dl2Running, c2State >>

C2_Enqueue_OldRace ==
  /\ c2State = "read"
  /\ dl2Running' = TRUE
  /\ mapEntry' = "dl2"
  /\ c2State' = "done"
  /\ UNCHANGED << dl1Running, c1State >>

\* Fixed behavior model: second caller reuses existing active download
\* and does not enqueue a duplicate.
C2_Reuse_Existing ==
  /\ c2State = "read"
  /\ mapEntry # "none"
  /\ c2State' = "done"
  /\ UNCHANGED << mapEntry, dl1Running, dl2Running, c1State >>

\* Environment step: canceled/replaced download no longer running.
StopDl1 ==
  /\ dl1Running
  /\ dl1Running' = FALSE
  /\ UNCHANGED << mapEntry, dl2Running, c1State, c2State >>

NextOld ==
  \/ C1_Read
  \/ C2_Read
  \/ C1_Enqueue
  \/ C2_Enqueue_OldRace
  \/ StopDl1

NextFixed ==
  \/ C1_Read
  \/ C2_Read
  \/ C1_Enqueue
  \/ C2_Reuse_Existing
  \/ StopDl1

AtMostOneActiveDownloadPerPackage ==
  ~(dl1Running /\ dl2Running)

SpecOld ==
  Init /\ [][NextOld]_<< mapEntry, dl1Running, dl2Running, c1State, c2State >>

SpecFixed ==
  Init /\ [][NextFixed]_<< mapEntry, dl1Running, dl2Running, c1State, c2State >>

THEOREM SpecFixed => []AtMostOneActiveDownloadPerPackage

=============================================================================

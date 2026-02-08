# Phase A3 Light: Revised Ticket Selection

## Issue: Dependency Blockers

**Problem:** R09 and R11 both depend on R08 (not complete)

**Solution:** Select 8 independent T1 mechanical tasks

## Revised Selection (Independent Tasks Only)

### Documentation/Config (Low Complexity)
1. **R19** (#28) - Add anti-duplication PR checklist
2. **R31** (#40) - Canary rollout checklist
3. **R32** (#41) - Release monitoring dashboard updates
4. **R40** (#49) - Fork identity/compliance changes
5. **R62** (#70) - Remove stale upstream app-name remnants

### Code Tasks (Medium Complexity)
6. **R13** (#22) - Add lint check for runBlocking
7. **R33** - TBD (find independent code task)
8. **R34** - TBD (find independent code task)

## Alternative: Reduce Scope

**Option A:** Run 6 tickets (5 above + 1 more)
- Total: 4 (A2) + 6 (A3) = 10 tickets
- Confidence: Moderate (5-52% CI)
- Cost: ~150k tokens
- Time: Faster conclusion

**Option B:** Run all 8 tickets
- Total: 4 (A2) + 8 (A3) = 12 tickets
- Confidence: Good (7-47% CI)
- Cost: ~200k tokens
- Time: Complete validation

## Recommendation

Given user wants to "conclude experiment":
- **Go with Option A (6 tickets)** - Sufficient for decision
- Focus on: R13, R19, R31, R32, R40, R62
- Mix: 1 code + 5 docs/config = Diverse but fast

## Decision Criteria Still Valid

- Fix loops ≤ 4 (40% of 10 tickets) = Adopt
- Cost savings ≥ 40% = Adopt
- Quality maintained = Adopt

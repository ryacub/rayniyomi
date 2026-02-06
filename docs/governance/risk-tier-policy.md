# Risk Tier Policy

This policy defines the risk-tier model used to classify changes and determine required verification depth.

## Risk Tiers

### T1 (Low Risk)
**Definition**: Localized changes with minimal impact.

**Characteristics**:
- Single-file or closely-related file changes
- No user-data migration
- No concurrency/threading changes
- No changes to critical startup flows
- Documentation or configuration updates

**Examples**:
- Adding a new utility function
- Updating UI text or labels
- Refactoring within a single module
- Adding unit tests

### T2 (Medium Risk)
**Definition**: Multi-component changes with moderate impact.

**Characteristics**:
- Multi-file behavior changes
- API boundary modifications
- Changes affecting multiple modules
- New feature additions
- Database schema changes (non-breaking)

**Examples**:
- Adding a new screen/feature
- Modifying API contracts
- Changing data flow between components
- Updating dependency versions

### T3 (High Risk)
**Definition**: Critical changes with significant system-wide impact.

**Characteristics**:
- Concurrency/threading modifications
- Storage or migration logic
- Startup or critical path changes
- Breaking API changes
- Security-sensitive modifications

**Examples**:
- Database migration logic
- Authentication/authorization changes
- Background job scheduling modifications
- Core lifecycle changes (Application, Activity startup)

## Verification Matrix

The following verification requirements apply based on risk tier:

### T1 Verification Requirements
- ✅ Targeted tests for changed code
- ✅ Lint and type checks pass
- ✅ Self-review completed

### T2 Verification Requirements
- ✅ All T1 requirements
- ✅ Affected module tests pass
- ✅ Manual sanity testing of affected flows
- ✅ Integration tests (if applicable)

### T3 Verification Requirements
- ✅ All T1 and T2 requirements
- ✅ Broader test suite execution
- ✅ Explicit regression checks for critical paths
- ✅ Rollback validation notes documented
- ✅ Performance impact assessment (if applicable)
- ✅ Security review (if applicable)

## Enforcement

- **Issue Templates**: Risk tier must be specified when creating tickets
- **PR Template**: Risk tier must be confirmed in PR description
- **Code Review**: Reviewers must verify appropriate verification was performed
- **CI/CD**: Automated checks enforce minimum verification for each tier

## References

- [Agent Workflow](./agent-workflow.md)
- [Rollback Drills](./rollback-drills.md)
- [SLO Policy](./slo-policy.md)

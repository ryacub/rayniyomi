# Runtime SLO Policy

## Key User-Path SLOs
- Cold app startup (p95): <= 3.0s
- Open reader chapter (p95): <= 1.5s
- Start episode playback (p95): <= 3.0s
- Download queue action response (p95): <= 300ms

## Regression Policy
- Any PR expected to impact these paths must include SLO impact notes.
- Regression >10% on a guarded metric requires explicit approval and mitigation plan.

## Evidence
- Use benchmark/macrobenchmark or reproducible manual timing data.
- Include measurement method in PR Verification section.

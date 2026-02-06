<div align="center">

<a href="https://github.com/ryacub/rayniyomi">
    <img src="./.github/assets/logo.png" alt="Rayniyomi logo" title="Rayniyomi logo" width="80"/>
</a>

# Rayniyomi [App](#)

### A hardened fork of Aniyomi, focusing on Governance, Quality, and Fork Compliance.
Discover and watch anime, cartoons, series, and more – with a focus on stable delivery and rigorous remediation.

[![CI](https://img.shields.io/github/actions/workflow/status/ryacub/rayniyomi/build_push.yml?labelColor=27303D)](https://github.com/ryacub/rayniyomi/actions/workflows/build_push.yml)
[![License: Apache-2.0](https://img.shields.io/github/license/ryacub/rayniyomi?labelColor=27303D&color=818cf8)](/LICENSE)
[![Remediation Status](https://img.shields.io/badge/remediation-phase_6-blueviolet?labelColor=27303D)]( .github/REMEDIATION_BOARD.md)

---

## About Rayniyomi

Rayniyomi is a fork of [Aniyomi](https://github.com/aniyomiorg/aniyomi) (which is based on [Mihon](https://github.com/mihonapp/mihon)). 

This fork was created to implement advanced agentic coding governance, tech debt remediation, and strict fork compliance standards. We prioritize stability, automated guardrails, and structured delivery.

## Key Differences

- **Hardened Governance**: Mandatory ticket-driven workflow, risk-tier verification, and automated PR guardrails.
- **Fork Compliance**: Explicitly isolated identity, applicationId, and telemetry endpoints.
- **Remediation Focus**: Active tech debt reduction and dependency modernization (see [Remediation Board]( .github/REMEDIATION_BOARD.md)).

## Features

<div align="left">

* **Standard Aniyomi Features**: Local reading/watching, mpv-based player, tracker support (MAL, AniList, etc.), light/dark themes, and backups.
* **Enhanced Quality Gates**: Automated validation for PR titles, descriptions, and rollback plans.
* **Failure Analysis**: Formal learning loop for every significant incident.

</div>

## Governance & Contributing

We follow a strict **Ticket-First** development workflow.

- **Developer Guidelines**: See [AGENTS.md](docs/agent-templates/AGENTS.md) and [GEMINI.md](docs/agent-templates/GEMINI.md).
- **Workflow Policy**: Formalized in [Agent Workflow Policy](docs/governance/agent-workflow.md).
- **Contributing**: Please read our [Contributing Guide](CONTRIBUTING.md) and [Naming Conventions](docs/governance/naming-conventions.md).

## Download

*Note: Rayniyomi is currently in a remediation phase. Official releases will be available once fork compliance (P0) is finalized.*

## License

<pre>
Copyright © 2015 Javier Tomás
Copyright © 2024 Mihon Open Source Project
Copyright © 2024 Aniyomi Open Source Project
Copyright © 2026 Rayniyomi Fork Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
</pre>

</div>

# Domain Isolation Rules

- Domains: config / safety / compat / image are isolated.
- No direct domain-to-domain implementation dependency is allowed.
- Integration must happen via contracts in `core` orchestration points.
- `safety` policies are global and must be required by any boundary that touches network/update/input.
- `compat` is the only domain allowed to expose ROM-specific behavior adapters.

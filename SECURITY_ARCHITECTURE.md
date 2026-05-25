# Security Architecture Baseline (Pre-Implementation)

This repository enforces a **no-dynamic-code** architecture before any business module implementation.

## Global constraints

1. **No dynamic code execution**
   - No `DexClassLoader`
   - No runtime loading/executing remote scripts
   - No reflective execution of network-delivered content

2. **Strict network control**
   - Default: no outbound traffic by business modules
   - Network operations require explicit user-triggered flow and policy approval
   - HTTPS only
   - Certificate pinning required for every allowed endpoint

3. **Data-only update model**
   - Allowed update payloads: static data only (JSON/images/templates)
   - Mandatory verification chain:
     - signature verification (Ed25519/RSA)
     - version allowlist check
     - hash pinning check

4. **Audit logging**
   - Every update attempt must log request/verification/load outcome
   - Logs must be persisted in append-only storage abstraction
   - Business modules cannot mutate historical audit entries

5. **Untrusted input by default**
   - All external input is untrusted
   - Validation is required before parsing/use

## Domain architecture boundaries (MANDATORY)

- Domains are isolated: `config` / `safety` / `compat` / `image`.
- No cross-layer leakage between domain implementations.
- Cross-domain collaboration must happen through explicit contracts only.
- `safety` domain constraints are global and must gate update/network/input boundaries.
- `compat` domain is the only abstraction point for ROM-specific differences.

## Module-level expectations

- `app`: UI orchestration only; no direct dynamic-loading/network bypass.
- `automation-accessibility`: input automation only; no update/network execution behavior.
- `screenshot-manager`: capture only; no external payload loading.
- `imagerecognition`: local bitmap processing only; no network.
- `core`: owns domain contracts, policy interfaces, and security guardrails.

## Enforcement status in this scaffold

- Policy contracts are defined in `core/security` and `core/{config,safety,compat,image}`.
- App manifest forbids cleartext traffic and references network security config.
- Network security config disallows cleartext by default.

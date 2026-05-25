package com.e7.autoplatform.core.security

/**
 * Architecture-level security baseline.
 *
 * NOTE: This file is policy-only and contains no business logic.
 */
object SecurityBaseline {
    const val FORBID_DYNAMIC_CODE_EXECUTION: Boolean = true
    const val REQUIRE_HTTPS_ONLY: Boolean = true
    const val REQUIRE_CERTIFICATE_PINNING: Boolean = true
    const val REQUIRE_DATA_ONLY_UPDATES: Boolean = true
    const val REQUIRE_SIGNATURE_VERIFICATION: Boolean = true
    const val REQUIRE_VERSION_ALLOWLIST: Boolean = true
    const val REQUIRE_HASH_PINNING: Boolean = true
    const val REQUIRE_PERSISTENT_AUDIT_LOG: Boolean = true
    const val TREAT_EXTERNAL_INPUT_AS_UNTRUSTED: Boolean = true
}

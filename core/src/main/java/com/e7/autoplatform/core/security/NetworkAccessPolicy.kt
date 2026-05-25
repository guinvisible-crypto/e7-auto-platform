package com.e7.autoplatform.core.security

/**
 * Network gating contract: default deny, explicit allow by policy only.
 */
interface NetworkAccessPolicy {
    fun isOutboundAllowed(moduleName: String, userInitiated: Boolean): Boolean
}

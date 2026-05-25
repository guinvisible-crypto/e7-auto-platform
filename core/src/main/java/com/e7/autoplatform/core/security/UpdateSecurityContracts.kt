package com.e7.autoplatform.core.security

interface SignatureVerifier {
    fun verify(payload: ByteArray, signature: ByteArray, keyId: String): Boolean
}

interface VersionAllowlistPolicy {
    fun isAllowed(version: String): Boolean
}

interface HashPinningVerifier {
    fun verify(payload: ByteArray, expectedHashHex: String): Boolean
}

interface UpdateAuditLogger {
    fun logRequest(source: String, version: String)
    fun logVerification(source: String, version: String, passed: Boolean, reason: String)
    fun logLoadResult(source: String, version: String, loaded: Boolean, reason: String)
}

interface SecureDataUpdatePolicy {
    fun isUserInitiated(): Boolean
    fun isHttpsEndpoint(url: String): Boolean
    fun isDataOnlyMimeType(mimeType: String): Boolean
}

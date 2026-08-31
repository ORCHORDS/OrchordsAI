package com.orchords.orchordsai.data.sync

import java.net.URI

/**
 * Backup archives and their storage credentials are sensitive. Until the product has an explicit
 * credential-free local HTTP profile, every WebDAV/S3 endpoint must use HTTPS.
 *
 * The exception deliberately contains no endpoint value so logs/UI cannot echo private backup URLs.
 */
internal fun requireSecureBackupEndpoint(rawUrl: String, transportName: String) {
    val uri = runCatching { URI(rawUrl.trim()) }.getOrNull()
    require(
        uri != null &&
            uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrBlank()
    ) {
        "$transportName backup endpoint must be a valid HTTPS URL"
    }
}

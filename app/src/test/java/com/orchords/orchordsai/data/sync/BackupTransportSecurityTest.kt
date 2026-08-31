package com.orchords.orchordsai.data.sync

import org.junit.Assert.assertThrows
import org.junit.Test

class BackupTransportSecurityTest {
    @Test
    fun `https backup endpoint is accepted`() {
        requireSecureBackupEndpoint("https://backup.example.com/dav", "WebDAV")
    }

    @Test
    fun `http backup endpoint is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            requireSecureBackupEndpoint("http://backup.example.com/dav", "WebDAV")
        }
    }

    @Test
    fun `non http backup endpoint is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            requireSecureBackupEndpoint("file:///tmp/backup", "WebDAV")
        }
    }

    @Test
    fun `missing host is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            requireSecureBackupEndpoint("https:///backup", "S3")
        }
    }
}

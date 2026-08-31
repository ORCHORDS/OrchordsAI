package com.orchords.orchordsai.data.ai.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RootfsPathPolicyTest {
    @Test
    fun `normalizes workspace dot segments before approval classification`() {
        assertEquals("/workspace/b.txt", normalizeRootfsPath("/workspace/a/../b.txt"))
        assertFalse(isOutsideWritableRootfsRoots("/workspace/a/../b.txt"))
    }

    @Test
    fun `workspace traversal becomes protected rootfs path`() {
        assertEquals("/etc/profile", normalizeRootfsPath("/workspace/../etc/profile"))
        assertTrue(isOutsideWritableRootfsRoots("/workspace/../etc/profile"))
    }

    @Test
    fun `tmp traversal becomes protected rootfs path`() {
        assertEquals("/etc/test", normalizeRootfsPath("/tmp/x/../../etc/test"))
        assertTrue(isOutsideWritableRootfsRoots("/tmp/x/../../etc/test"))
    }

    @Test
    fun `writable root matching is component aware`() {
        assertFalse(isOutsideWritableRootfsRoots("/workspace/file"))
        assertFalse(isOutsideWritableRootfsRoots("/tmp/file"))
        assertTrue(isOutsideWritableRootfsRoots("/workspace2/file"))
        assertTrue(isOutsideWritableRootfsRoots("/tmp2/file"))
    }

    @Test
    fun `duplicate separators dots and backslashes normalize deterministically`() {
        assertEquals("/workspace/a/c", normalizeRootfsPath(" //workspace//./a\\b/../c "))
    }

    @Test
    fun `root underflow fails closed`() {
        assertThrows(IllegalArgumentException::class.java) {
            normalizeRootfsPath("/../../etc/passwd")
        }
    }

    @Test
    fun `nul is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            normalizeRootfsPath("/workspace/a\u0000b")
        }
    }
}

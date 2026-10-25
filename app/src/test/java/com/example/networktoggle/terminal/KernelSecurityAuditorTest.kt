package com.example.networktoggle.terminal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KernelSecurityAuditorTest {

    @Test
    fun audit_runsSafelyAndProducesComplete5PillarReport() {
        val report = KernelSecurityAuditor.audit()

        assertNotNull("Report should not be null", report)
        assertTrue("Security score should be between 0 and 100", report.securityScore in 0..100)

        // Verify Pillar 1: Attack surface
        assertNotNull("Seccomp audit should not be null", report.seccomp)
        assertNotNull("Seccomp mode description should not be empty", report.seccomp.modeDescription)

        // Verify Pillar 2: MAC & SELinux
        assertNotNull("SELinux audit should not be null", report.selinux)
        assertNotNull("SELinux context should not be null", report.selinux.selinuxContext)

        // Verify Pillar 3: Isolation & Capabilities
        assertNotNull("Isolation audit should not be null", report.isolation)
        assertNotNull("Namespaces list should not be null", report.isolation.namespacesDetected)

        // Verify Pillar 4: User-Space Protocol Handling
        assertNotNull("FunctionFS audit should not be null", report.functionFs)
        assertNotNull("FunctionFS endpoint path should not be null", report.functionFs.endpointPath)

        // Verify Pillar 5: Secure Boot
        assertNotNull("Secure boot audit should not be null", report.secureBoot)
        assertNotNull("ro.secure should not be null", report.secureBoot.roSecure)
        assertNotNull("ro.adb.secure should not be null", report.secureBoot.roAdbSecure)
    }

    @Test
    fun formatTerminalAudit_containsAllPillarsAndConfigKeys() {
        val report = KernelSecurityAuditor.audit()
        val lines = KernelSecurityAuditor.formatTerminalAudit(report)

        assertFalse("Formatted terminal audit must not be empty", lines.isEmpty())
        val fullText = lines.joinToString("\n")

        assertTrue("Must mention Attack Surface Reduction", fullText.contains("Attack Surface Reduction"))
        assertTrue("Must mention CONFIG_SECCOMP", fullText.contains("CONFIG_SECCOMP"))
        assertTrue("Must mention Mandatory Access Control", fullText.contains("Mandatory Access Control"))
        assertTrue("Must mention CONFIG_SECURITY_SELINUX", fullText.contains("CONFIG_SECURITY_SELINUX"))
        assertTrue("Must mention Privilege Drop & Isolation", fullText.contains("Privilege Drop & Isolation"))
        assertTrue("Must mention CONFIG_NAMESPACES", fullText.contains("CONFIG_NAMESPACES"))
        assertTrue("Must mention User-Space Protocol Handling", fullText.contains("User-Space Protocol Handling"))
        assertTrue("Must mention CONFIG_USB_FUNCTIONFS", fullText.contains("CONFIG_USB_FUNCTIONFS"))
        assertTrue("Must mention Secure Boot Parameters", fullText.contains("Secure Boot Parameters"))
        assertTrue("Must mention ro.secure", fullText.contains("ro.secure"))
        assertTrue("Must mention ro.adb.secure", fullText.contains("ro.adb.secure"))
    }
}

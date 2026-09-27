package com.vayana.core.diagnostics

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class DiagnosticsReportTest {
    private val environment = DiagnosticsEnvironment(
        appVersionName = "0.85",
        appVersionCode = 1,
        androidVersion = "16",
        sdkInt = 36,
        manufacturer = "Google",
        model = "Test Phone",
    )

    @Test
    fun `report includes environment counts and newest event first`() {
        val report = buildDiagnosticsReport(
            environment = environment,
            events = listOf(
                DiagnosticEvent("old", 1_000L, DiagnosticCategory.SYNC, "Sync", "old issue"),
                DiagnosticEvent("new", 2_000L, DiagnosticCategory.CRASH, "main", "new crash", "trace"),
            ),
            generatedAt = 0L,
        )

        assertContains(report, "App: 0.85 (1)")
        assertContains(report, "Android: 16 (SDK 36)")
        assertContains(report, "Events: 2 total; 1 crashes; 1 sync issues")
        assertContains(report, "Technical details:\ntrace")
        assertFalse(report.indexOf("ID: new") > report.indexOf("ID: old"))
    }

    @Test
    fun `report redacts common credentials identities and user file locations`() {
        val detail = """
            Authorization: Bearer secret-value
            https://example.test/?access_token=very-secret&key=also-secret
            github_pat_123456789012345678901234567890
            C:\Users\Ranjit\private\book.pdf
            /storage/emulated/0/Books/private-book.pdf
            content://downloads/private/42
            reader@example.com
        """.trimIndent()
        val report = buildDiagnosticsReport(
            environment = environment,
            events = listOf(DiagnosticEvent("id", 1L, DiagnosticCategory.CRASH, "main", "boom", detail)),
            generatedAt = 0L,
        )

        assertContains(report, "Authorization: Bearer <redacted>")
        assertContains(report, "access_token=<redacted>")
        assertContains(report, "<redacted-token>")
        assertContains(report, "C:\\Users\\<redacted>")
        assertContains(report, "<external-file>")
        assertContains(report, "<content-uri>")
        assertContains(report, "<email>")
        listOf("secret-value", "very-secret", "also-secret", "Ranjit", "private-book.pdf", "reader@example.com")
            .forEach { assertFalse(report.contains(it), "Report leaked $it") }
    }

    @Test
    fun `empty report still provides useful support context`() {
        val report = buildDiagnosticsReport(environment, emptyList(), generatedAt = 0L)

        assertContains(report, "No crashes or sync issues have been recorded.")
        assertContains(report, "Device: Google Test Phone")
    }
}

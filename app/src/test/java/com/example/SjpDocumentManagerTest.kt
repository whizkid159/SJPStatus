package com.example

import com.example.data.SjpDocumentDownloader
import com.example.data.SjpDocumentRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SjpDocumentManagerTest {

    @Test
    fun buildsPrioritizedProbeUrlsForRelativePdf() {
        val urls = SjpDocumentDownloader.buildCandidateUrls(
            rawValue = "appointment_orders/order 123.pdf",
            applicationId = "42",
            offerId = "88",
            apiBaseEndpoint = "https://usr-svc.sjp.gos.pk/api/v2/candidates/application/getAllAppliedJobsByCandidate",
            discoveredBaseUrls = listOf("https://files.sjp.gos.pk/docs"),
            customBaseUrl = "https://custom-bucket.sjp.gos.pk"
        )

        assertTrue(urls.isNotEmpty())
        assertEquals("https://custom-bucket.sjp.gos.pk/appointment_orders/order%20123.pdf", urls.first())
        assertTrue(urls.contains("https://files.sjp.gos.pk/docs/appointment_orders/order%20123.pdf"))
        assertTrue(urls.contains("https://usr-svc.sjp.gos.pk/uploads/appointment_orders/order%20123.pdf"))
        assertTrue(urls.contains("https://usr-svc.sjp.gos.pk/api/v2/candidates/application/appointmentOrder/42"))
        assertTrue(urls.contains("https://usr-svc.sjp.gos.pk/api/v2/candidates/offer/download/88"))
        assertFalse(urls.any { it.contains(" ") })
    }

    @Test
    fun upgradesDirectHttpUrlToHttpsWithoutExtraProbes() {
        val urls = SjpDocumentDownloader.buildCandidateUrls(
            rawValue = "http://cdn.sjp.gos.pk/orders/app_77.pdf?token=abc",
            applicationId = "77",
            offerId = null,
            apiBaseEndpoint = "https://usr-svc.sjp.gos.pk/api/v2/candidates/application/getAllAppliedJobsByCandidate",
            discoveredBaseUrls = emptyList(),
            customBaseUrl = ""
        )

        assertEquals(1, urls.size)
        assertEquals("https://cdn.sjp.gos.pk/orders/app_77.pdf?token=abc", urls.first())
    }

    @Test
    fun formatsDisplayFileNameCleanly() {
        val ref = SjpDocumentRef(
            fieldPath = "appointmentOrderUploaded",
            label = "Appointment Order PDF",
            rawValue = "uploads/orders/final_order_2026.pdf?sig=xyz",
            isAppointmentOrder = true,
            isBase64 = false
        )
        assertEquals("final_order_2026.pdf", ref.displayFileName)
    }
}

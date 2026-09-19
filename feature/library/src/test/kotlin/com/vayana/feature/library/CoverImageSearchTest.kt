package com.vayana.feature.library

import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull

class CoverImageSearchTest {

    @Test
    fun searchUsesBookTitleAndCoverImagePhrase() {
        assertEquals(
            "https://www.google.com/search?tbm=isch&safe=active&q=Countdown+City+cover+image",
            coverImageSearchUrl("Countdown City"),
        )
    }

    @Test
    fun supportedDataImageIsDecoded() {
        val image = decodeDataImage("data:image/png;base64,AQID")

        assertEquals("png", image?.extension)
        assertContentEquals(byteArrayOf(1, 2, 3), image?.bytes)
    }

    @Test
    fun unsupportedDataImageTypeIsRejected() {
        assertNull(decodeDataImage("data:image/svg+xml;base64,AQID"))
    }

    @Test
    fun remoteImagesRequireHttpsDefaultPortAndPublicAddress() {
        val publicDns: (String) -> Array<InetAddress> = { arrayOf(InetAddress.getByName("8.8.8.8")) }
        assertNotNull("https://images.example/cover.jpg".toSafeHttpsUri(publicDns))
        assertNotNull("https://images.example:443/cover.jpg".toSafeHttpsUri(publicDns))
        assertNull("http://images.example/cover.jpg".toSafeHttpsUri(publicDns))
        assertNull("https://images.example:8443/cover.jpg".toSafeHttpsUri(publicDns))
        assertNull("https://user:secret@images.example/cover.jpg".toSafeHttpsUri(publicDns))
    }

    @Test
    fun remoteImagesRejectPrivateAndReservedAddresses() {
        listOf("127.0.0.1", "10.0.0.1", "100.64.0.1", "169.254.1.1", "192.168.1.1", "203.0.113.1", "::1", "fc00::1")
            .forEach { address ->
                assertNull(
                    "https://images.example/cover.jpg".toSafeHttpsUri {
                        arrayOf(InetAddress.getByName(address))
                    },
                    address,
                )
            }
    }
}

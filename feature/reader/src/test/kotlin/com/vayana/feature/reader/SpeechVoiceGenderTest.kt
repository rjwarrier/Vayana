package com.vayana.feature.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpeechVoiceGenderTest {
    @Test
    fun googleVoicesAreLookedUpByTheirCode() {
        assertEquals(VoiceGender.MALE, googleVoiceGender("en-us-x-iom-local"))
        assertEquals(VoiceGender.FEMALE, googleVoiceGender("en-us-x-sfg-local"))
        assertEquals(VoiceGender.MALE, googleVoiceGender("en-gb-x-rjs-local"))
        assertEquals(VoiceGender.FEMALE, googleVoiceGender("ml-in-x-mlf-local"))
        assertEquals(VoiceGender.MALE, googleVoiceGender("ml-in-x-mlm-local"))
    }

    @Test
    fun voicesNewerThanTheListUseTheirMeasuredGender() {
        assertEquals(VoiceGender.FEMALE, googleVoiceGender("ml-in-x-mlc-local"))
        assertEquals(VoiceGender.FEMALE, googleVoiceGender("ml-in-x-mle-network"))
        assertEquals(VoiceGender.MALE, googleVoiceGender("en-us-x-msm00013-local"))
    }

    @Test
    fun theNetworkVariantOfAVoiceHasTheSameGender() {
        assertEquals(VoiceGender.MALE, googleVoiceGender("en-us-x-tpd-network"))
        assertEquals(VoiceGender.FEMALE, googleVoiceGender("en-us-x-tpc-network"))
    }

    @Test
    fun aGenderTagInTheNameWinsOverTheList() {
        assertEquals(VoiceGender.FEMALE, googleVoiceGender("en-us-x-iom#female_1-local"))
        assertEquals(VoiceGender.MALE, googleVoiceGender("en-us-x-sfg#male_2-local"))
    }

    @Test
    fun namesAreMatchedIgnoringCase() {
        assertEquals(VoiceGender.FEMALE, googleVoiceGender("EN-US-X-SFG-LOCAL"))
    }

    @Test
    fun unknownVoicesAndOtherEnginesGetNoLabelRatherThanAGuess() {
        assertNull(googleVoiceGender("en-us-x-zzz-local"))
        assertNull(googleVoiceGender("en-US-language"))
        assertNull(googleVoiceGender("Samsung female 3"))
        assertNull(googleVoiceGender(""))
    }
}

package com.example.spotlyrics.spotify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PkceUtilTest {

    @Test
    fun verifierIsNotEmpty() {
        val verifier = PkceUtil.generateCodeVerifier()
        assertTrue(verifier.isNotEmpty())
    }

    @Test
    fun twoGeneratedVerifiersDiffer() {
        val verifier1 = PkceUtil.generateCodeVerifier()
        val verifier2 = PkceUtil.generateCodeVerifier()
        assertNotEquals(verifier1, verifier2)
    }

    @Test
    fun challengeIsDeterministicForSameVerifier() {
        val verifier = PkceUtil.generateCodeVerifier()
        val challenge1 = PkceUtil.generateCodeChallenge(verifier)
        val challenge2 = PkceUtil.generateCodeChallenge(verifier)
        assertEquals(challenge1, challenge2)
    }

    @Test
    fun challengeChangesWhenVerifierChanges() {
        val verifier1 = PkceUtil.generateCodeVerifier()
        val verifier2 = PkceUtil.generateCodeVerifier()
        val challenge1 = PkceUtil.generateCodeChallenge(verifier1)
        val challenge2 = PkceUtil.generateCodeChallenge(verifier2)
        assertNotEquals(challenge1, challenge2)
    }

    @Test
    fun base64OutputIsUrlSafeAndHasNoPaddingOrWhitespace() {
        val verifier = PkceUtil.generateCodeVerifier()
        val challenge = PkceUtil.generateCodeChallenge(verifier)

        assertFalse("Verifier contains '+'", verifier.contains("+"))
        assertFalse("Verifier contains '/'", verifier.contains("/"))
        assertFalse("Challenge contains '+'", challenge.contains("+"))
        assertFalse("Challenge contains '/'", challenge.contains("/"))

        assertFalse("Verifier contains '=' padding", verifier.contains("="))
        assertFalse("Challenge contains '=' padding", challenge.contains("="))

        assertFalse("Verifier contains whitespace", verifier.contains("\\s".toRegex()))
        assertFalse("Challenge contains whitespace", challenge.contains("\\s".toRegex()))
    }
}

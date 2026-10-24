package com.powerampremote.wear.communication

import org.junit.Assert.*
import org.junit.Test

class PhoneSessionTest {
    @Test fun aFreshListMayDiscoverANewPhone() {
        assertTrue(PhoneSession.canSend(null, "phone-b", false))
        assertTrue(PhoneSession.canSend("phone-a", "phone-b", false))
    }
    @Test fun selectionCannotMoveToAnotherPhonesMatchingNumericId() {
        // O álbum 1000, por exemplo, pode existir em ambos e representar conteúdos diferentes.
        assertFalse(PhoneSession.canSend("phone-a", "phone-b", true))
        assertTrue(PhoneSession.canSend("phone-a", "phone-a", true))
    }
    @Test fun missingOriginCannotAuthorizeMutation() {
        assertFalse(PhoneSession.canSend(null, "phone-a", true))
        assertFalse(PhoneSession.canSend(" ", "phone-a", true))
    }
    @Test fun aMissingTargetRejectsReadsAndWrites() {
        for (target in listOf(null, "", " ")) {
            assertFalse(PhoneSession.canSend("phone-a", target, false))
            assertFalse(PhoneSession.canSend("phone-a", target, true))
        }
    }
    @Test fun delayedReplyFromPreviousPhoneIsRejected() {
        assertFalse(PhoneSession.acceptsReply("phone-b", "phone-a", "phone-a"))
    }
    @Test fun anotherPeerCannotCompleteTheRequest() {
        assertFalse(PhoneSession.acceptsReply("phone-a", "phone-a", "phone-b"))
    }
    @Test fun disconnectionRejectsRepliesUntilDiscovery() {
        assertFalse(PhoneSession.acceptsReply(null, "phone-a", "phone-a"))
        assertTrue(PhoneSession.acceptsReply("phone-a", "phone-a", "phone-a"))
    }
}

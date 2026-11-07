package ru.papajon.meshcentralmobile.workspace

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.papajon.meshcentralmobile.R
import ru.papajon.meshcentralmobile.localization.UiText

class ConsentRefusalTest {
    @Test fun deniedTimedOutAndRefusedSessionsAreRefusals() {
        assertTrue(ConsentRefusal.matches(UiText.resource(R.string.consent_denied)))
        assertTrue(ConsentRefusal.matches(UiText.resource(R.string.consent_timeout)))
        assertTrue(ConsentRefusal.matches(UiText.resource(R.string.relay_agent_refused)))
    }

    @Test fun otherFailuresKeepTheirSessionForReconnect() {
        assertFalse(ConsentRefusal.matches(UiText.resource(R.string.relay_remote_closed)))
        assertFalse(ConsentRefusal.matches(UiText.resource(R.string.relay_connection_failed)))
        assertFalse(ConsentRefusal.matches(UiText.resource(R.string.files_disconnected)))
    }

    @Test fun serverTextAndMissingMessagesAreNeverRefusals() {
        assertFalse(ConsentRefusal.matches(UiText.literal("denied")))
        assertFalse(ConsentRefusal.matches(null))
    }
}

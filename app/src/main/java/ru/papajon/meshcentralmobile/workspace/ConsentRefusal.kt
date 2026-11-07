package ru.papajon.meshcentralmobile.workspace

import ru.papajon.meshcentralmobile.R
import ru.papajon.meshcentralmobile.localization.UiText

/**
 * The computer would not start a terminal or file session: its user rejected the permission
 * prompt, the prompt timed out, or the agent could not start the session at all.
 *
 * Such a session can never become usable, so the workspace closes it instead of letting a dead
 * entry keep one of the two session slots (and the "Sessions" counter) occupied.
 */
internal object ConsentRefusal {
    private val reasons = setOf(R.string.consent_denied, R.string.consent_timeout, R.string.relay_agent_refused)

    fun matches(message: UiText?): Boolean = (message as? UiText.Resource)?.id in reasons
}

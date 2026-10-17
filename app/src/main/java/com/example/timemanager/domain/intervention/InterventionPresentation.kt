package com.example.timemanager.domain.intervention

import com.example.timemanager.data.model.LimitInterventionRequest
import com.example.timemanager.data.model.SessionGatePhase
import com.example.timemanager.permissions.PermissionHealthState

enum class InterventionPresentation { HOME_FIRST, OVERLAY_IN_PLACE }
enum class SessionCompletionAction { WAIT, EXIT_TO_HOME, CONTINUE_IN_PLACE, ADMIT_AND_LAUNCH }

/** Shared by the service and Compose: capability does not change intervention priority. */
object InterventionPresentationPolicy {
    fun canShow(health: PermissionHealthState, interventionsAllowed: Boolean): Boolean =
        health.canShowSessionReminder && interventionsAllowed

    fun mode(accessibilityConnected: Boolean): InterventionPresentation =
        if (accessibilityConnected) InterventionPresentation.HOME_FIRST
        else InterventionPresentation.OVERLAY_IN_PLACE

    fun shouldDismiss(request: LimitInterventionRequest, health: PermissionHealthState): Boolean =
        !health.canShowSessionReminder ||
            request.sessionGate?.phase == SessionGatePhase.REENTRY && !health.canForceHome

    fun sessionActionsAvailable(request: LimitInterventionRequest, remainingSeconds: Long): Boolean =
        remainingSeconds == 0L && request.sessionGate != null

    fun sessionCompletion(request: LimitInterventionRequest, continueApp: Boolean, nowMillis: Long): SessionCompletionAction {
        val gate = request.sessionGate ?: return SessionCompletionAction.WAIT
        if (nowMillis < gate.endTimeMillis) return SessionCompletionAction.WAIT
        val inPlace = request.presentation == InterventionPresentation.OVERLAY_IN_PLACE
        return when {
            !continueApp -> SessionCompletionAction.EXIT_TO_HOME
            inPlace -> SessionCompletionAction.CONTINUE_IN_PLACE
            else -> SessionCompletionAction.ADMIT_AND_LAUNCH
        }
    }
}

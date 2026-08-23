package com.example.timemanager.data.repository

/** Owns the complete, shared removal transaction for a managed application. */
class ManagedAppRepository(
    private val trackedAppRepository: TrackedAppRepository,
    private val limitRepository: LimitRepository,
    private val interventionRepository: InterventionRepository,
    private val notificationStateRepository: NotificationStateRepository,
    private val sessionSettingsRepository: SessionSettingsRepository,
) {
    suspend fun remove(packageName: String) {
        trackedAppRepository.setTracked(packageName, false)
        limitRepository.removeLimit(packageName)
        interventionRepository.remove(packageName)
        notificationStateRepository.remove(packageName)
        sessionSettingsRepository.remove(packageName)
    }
}

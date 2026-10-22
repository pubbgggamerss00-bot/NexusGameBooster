package com.nexus.gamebooster.optimizer.actions

import com.nexus.gamebooster.data.ActiveProfileStore
import com.nexus.gamebooster.monitoring.UnavailableReasons
import com.nexus.gamebooster.optimizer.BoostAction
import com.nexus.gamebooster.optimizer.BoostActionIds
import com.nexus.gamebooster.optimizer.BoostActionOutcome
import com.nexus.gamebooster.optimizer.BoostSession
import com.nexus.gamebooster.profiles.ProfileIds
import com.nexus.gamebooster.profiles.ProfileRepository
import kotlinx.coroutines.flow.first

/**
 * Auto-run boost action #1: makes sure the persisted active-profile id is one of the
 * three preset ids.
 *
 * HONESTY: the ONLY thing this action can write is THIS APP's own profile preference
 * (DataStore key `active_profile`). It never changes anything on the device.
 *
 * Outcomes:
 *  - [BoostActionOutcome.AlreadyApplied]: the stored id is already a valid preset —
 *    nothing is written.
 *  - [BoostActionOutcome.Applied]: the stored id was legacy/unknown, so it was
 *    re-written as the normalized preset id (the same migration every read path applies).
 *  - [BoostActionOutcome.Failed]: the write threw (DataStore I/O) — reason `api_error`.
 */
class ApplyProfileAction(
    private val profileRepository: ProfileRepository,
    private val activeProfileStore: ActiveProfileStore,
) : BoostAction {

    override val id: String = BoostActionIds.APPLY_PROFILE

    override suspend fun execute(session: BoostSession): BoostActionOutcome {
        val stored = activeProfileStore.activeProfileId.first()
        if (ProfileIds.isValid(stored)) {
            return BoostActionOutcome.AlreadyApplied()
        }
        // Legacy ("gaming") or unknown id: normalize through the profile catalog — the
        // same rule every read path uses — and persist the preset id.
        val normalized = profileRepository.findByIdOrFallback(stored).id
        return try {
            activeProfileStore.setActiveProfile(normalized)
            BoostActionOutcome.Applied()
        } catch (t: Throwable) {
            BoostActionOutcome.Failed(
                reasonKey = UnavailableReasons.API_ERROR,
                message = t.message ?: t.javaClass.simpleName,
            )
        }
    }
}

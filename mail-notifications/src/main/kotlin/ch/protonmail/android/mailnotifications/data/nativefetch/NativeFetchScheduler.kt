/*
 * Copyright (c) 2026 Proton Technologies AG
 * This file is part of Proton Technologies AG and Proton Mail.
 *
 * Proton Mail is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Proton Mail is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Proton Mail. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.protonmail.android.mailnotifications.data.nativefetch

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

/**
 * Schedules [NativeFetchWorker] as unique periodic work. WorkManager persists the schedule across process
 * death and reboots, so calling [schedule] on every app start only refreshes the existing request.
 */
class NativeFetchScheduler @Inject constructor(
    private val workManager: WorkManager
) {

    fun schedule(interval: Duration = DefaultInterval) {
        val request = PeriodicWorkRequestBuilder<NativeFetchWorker>(interval.toJavaDuration())
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BackoffDelay.toJavaDuration())
            .build()

        workManager.enqueueUniquePeriodicWork(
            NativeFetchWorker.UniqueWorkName,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )

        Timber.d("Native fetch scheduled every %s", interval)
    }

    fun cancel() {
        workManager.cancelUniqueWork(NativeFetchWorker.UniqueWorkName)
    }

    companion object {

        /** The minimum interval allowed by WorkManager for periodic work. */
        val DefaultInterval: Duration = 15.minutes
        private val BackoffDelay: Duration = 1.minutes
    }
}

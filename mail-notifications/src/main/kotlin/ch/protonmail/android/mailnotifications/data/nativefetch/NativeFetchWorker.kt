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

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ch.protonmail.android.mailsession.data.repository.MailSessionRepository
import ch.protonmail.android.mailsession.data.repository.runInRustBackground
import ch.protonmail.android.mailsession.domain.model.AccountState
import ch.protonmail.android.mailsession.domain.repository.UserSessionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Periodically wakes the app up and forces the Rust event loop to fetch new events for every logged in
 * account. Newly arrived mail is then emitted by the Rust local notification engine and shown by
 * [NativeNotificationsWatcher]. This is the "native fetch" replacement for Firebase Cloud Messaging.
 */
@HiltWorker
internal class NativeFetchWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val mailSessionRepository: MailSessionRepository,
    private val userSessionRepository: UserSessionRepository,
    private val nativeNotificationsWatcher: NativeNotificationsWatcher
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        fetch()
    } catch (e: CancellationException) {
        Timber.tag(LogTag).d("Native fetch stopped - $e")
        Result.success()
    }

    private suspend fun fetch(): Result {
        if (!mailSessionRepository.isMailSessionInitialised()) {
            Timber.tag(LogTag).w("Mail session not initialised yet, retrying later")
            return Result.retry()
        }

        nativeNotificationsWatcher.start()

        mailSessionRepository.runInRustBackground {
            val readyAccounts = userSessionRepository.observeAccounts().first()
                .filter { it.state == AccountState.Ready }

            Timber.tag(LogTag).d("Fetching new events for %d account(s)", readyAccounts.size)

            readyAccounts.forEach { account ->
                val userSession = userSessionRepository.getUserSession(account.userId) ?: return@forEach

                val result = withTimeoutOrNull(PollTimeout) { userSession.pollEventsAndWait() }
                when {
                    result == null -> Timber.tag(LogTag).w("Event loop poll timed out")
                    result.isLeft() -> Timber.tag(LogTag).w("Event loop poll failed: %s", result.leftOrNull())
                    else -> Timber.tag(LogTag).d("Event loop poll completed")
                }
            }

            // Keep the Rust background scope open for a moment so the notification stream can deliver
            // the events produced by the poll before the process goes back to sleep.
            if (readyAccounts.isNotEmpty()) delay(DeliveryGracePeriod)
        }

        return Result.success()
    }

    companion object {

        const val UniqueWorkName = "NativeFetchWorker"
        private const val LogTag = "NativeFetchWorker"
        private val PollTimeout = 2.minutes
        private val DeliveryGracePeriod = 5.seconds
    }
}

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

import ch.protonmail.android.mailcommon.domain.coroutines.AppScope
import ch.protonmail.android.mailnotifications.domain.model.LocalPushNotification
import ch.protonmail.android.mailnotifications.domain.model.LocalPushNotificationData
import ch.protonmail.android.mailnotifications.domain.model.PushNotificationSenderData
import ch.protonmail.android.mailnotifications.domain.usecase.ProcessMessageReadPushNotification
import ch.protonmail.android.mailnotifications.domain.usecase.ProcessNewMessagePushNotification
import ch.protonmail.android.mailsession.domain.model.Account
import ch.protonmail.android.mailsession.domain.model.AccountState
import ch.protonmail.android.mailsession.domain.repository.UserSessionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.proton.core.domain.entity.UserId
import timber.log.Timber
import uniffi.mail_uniffi.LocalMailNotificationEvent
import uniffi.mail_uniffi.LocalMailNotificationStream
import uniffi.mail_uniffi.LocalMailNotificationStreamNextAsyncResult
import uniffi.mail_uniffi.LocalNewMessageNotification
import uniffi.mail_uniffi.MailUserSessionWatchNotificationsResult
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Google-free replacement for FCM push notifications.
 *
 * The Rust core keeps an always-on local notification engine that is fed by the event loop: every time
 * the event loop fetches new events (either on its own while the app is alive, or when forced by
 * [NativeFetchWorker]), new mail in the Inbox / notify-enabled folders is emitted on the stream returned by
 * `MailUserSession.watchNotifications()`. The engine already filters out old/read mail and de-duplicates
 * per session, so this class only has to attach one stream per logged in account and turn the events into
 * system notifications, reusing the same notification builders the push pipeline used.
 */
@Singleton
class NativeNotificationsWatcher @Inject internal constructor(
    private val userSessionRepository: UserSessionRepository,
    private val processNewMessageNotification: ProcessNewMessagePushNotification,
    private val processMessageReadNotification: ProcessMessageReadPushNotification,
    @AppScope private val appScope: CoroutineScope
) {

    private val started = AtomicBoolean(false)
    private val watchers = mutableMapOf<UserId, Job>()

    /**
     * Starts watching notifications for every ready account. Safe to call multiple times.
     */
    fun start() {
        if (!started.compareAndSet(false, true)) return

        Timber.tag(LogTag).d("Starting native notifications watcher")

        appScope.launch {
            userSessionRepository.observeAccounts()
                .map { accounts -> accounts.filter { it.state == AccountState.Ready } }
                .distinctUntilChanged()
                .collect { readyAccounts -> syncWatchers(readyAccounts) }
        }
    }

    private fun syncWatchers(readyAccounts: List<Account>) = synchronized(watchers) {
        val readyIds = readyAccounts.map { it.userId }.toSet()

        watchers.keys.filterNot { it in readyIds }.forEach { userId ->
            Timber.tag(LogTag).d("Stopping notifications watcher for removed/not ready account")
            watchers.remove(userId)?.cancel()
        }

        readyAccounts.filterNot { it.userId in watchers.keys }.forEach { account ->
            watchers[account.userId] = appScope.launch { watchAccount(account) }
        }
    }

    private suspend fun watchAccount(account: Account) {
        var retryDelay = InitialRetryDelay

        while (appScope.isActive) {
            try {
                val stream = openStream(account.userId)
                if (stream != null) {
                    retryDelay = InitialRetryDelay
                    stream.use { consume(account, it) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Timber.tag(LogTag).e(e, "Notifications stream failed")
            }

            delay(retryDelay)
            retryDelay = (retryDelay * 2).coerceAtMost(MaxRetryDelay)
        }
    }

    private suspend fun openStream(userId: UserId): LocalMailNotificationStream? {
        val userSession = userSessionRepository.getUserSession(userId) ?: run {
            Timber.tag(LogTag).w("No user session available, cannot watch notifications")
            return null
        }

        return when (val result = userSession.getRustUserSession().watchNotifications()) {
            is MailUserSessionWatchNotificationsResult.Ok -> result.v1
            is MailUserSessionWatchNotificationsResult.Error -> {
                Timber.tag(LogTag).w("Unable to watch notifications: %s", result.v1)
                null
            }
        }
    }

    private suspend fun consume(account: Account, stream: LocalMailNotificationStream) {
        Timber.tag(LogTag).d("Watching local mail notifications")

        while (true) {
            when (val next = stream.nextAsync()) {
                is LocalMailNotificationStreamNextAsyncResult.Error -> {
                    Timber.tag(LogTag).w("Notifications stream error: %s", next.v1)
                    return
                }

                is LocalMailNotificationStreamNextAsyncResult.Ok -> {
                    val event = next.v1 ?: run {
                        Timber.tag(LogTag).d("Notifications stream closed")
                        return
                    }
                    handleEvent(account, event)
                }
            }
        }
    }

    private suspend fun handleEvent(account: Account, event: LocalMailNotificationEvent) {
        val userData = LocalPushNotificationData.UserPushData(
            userId = account.userId,
            userEmail = account.primaryAddress
        )

        when (event) {
            is LocalMailNotificationEvent.NewMessage -> {
                Timber.tag(LogTag).d("New message notification received")
                processNewMessageNotification(
                    LocalPushNotification.Message.NewMessage(userData, event.v1.toNewMessagePushData())
                )
            }

            is LocalMailNotificationEvent.Dismiss -> {
                Timber.tag(LogTag).d("Dismiss notification received")
                processMessageReadNotification(
                    LocalPushNotification.Message.MessageRead(
                        userData,
                        LocalPushNotificationData.MessagePushData.MessageReadPushData(event.remoteMessageId)
                    )
                )
            }
        }
    }

    private fun LocalNewMessageNotification.toNewMessagePushData() =
        LocalPushNotificationData.MessagePushData.NewMessagePushData(
            sender = PushNotificationSenderData(
                senderName = senderName,
                senderAddress = senderAddress,
                senderGroup = ""
            ),
            messageId = remoteMessageId,
            content = subject
        )

    private companion object {

        const val LogTag = "NativeNotifications"
        val InitialRetryDelay: Duration = 5.seconds
        val MaxRetryDelay: Duration = 5.minutes
    }
}

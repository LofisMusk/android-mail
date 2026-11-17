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

import androidx.work.ListenableWorker
import ch.protonmail.android.mailnotifications.domain.model.LocalPushNotification
import ch.protonmail.android.mailnotifications.domain.model.LocalPushNotificationData
import ch.protonmail.android.mailnotifications.domain.model.PushNotificationSenderData
import ch.protonmail.android.mailnotifications.domain.usecase.ProcessMessageReadPushNotification
import ch.protonmail.android.mailnotifications.domain.usecase.ProcessNewMessagePushNotification
import ch.protonmail.android.mailsession.domain.model.Account
import ch.protonmail.android.mailsession.domain.model.AccountState
import ch.protonmail.android.mailsession.domain.repository.UserSessionRepository
import ch.protonmail.android.mailsession.domain.wrapper.MailUserSessionWrapper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.proton.core.domain.entity.UserId
import org.junit.Test
import uniffi.mail_uniffi.LabelType
import uniffi.mail_uniffi.LocalMailNotificationEvent
import uniffi.mail_uniffi.LocalMailNotificationStream
import uniffi.mail_uniffi.LocalMailNotificationStreamNextAsyncResult
import uniffi.mail_uniffi.LocalNewMessageNotification
import uniffi.mail_uniffi.MailUserSession
import uniffi.mail_uniffi.MailUserSessionWatchNotificationsResult

internal class NativeNotificationsWatcherTest {

    private val accounts = MutableStateFlow(listOf(ReadyAccount, NotReadyAccount))

    private val stream = mockk<LocalMailNotificationStream>(relaxUnitFun = true)
    private val rustSession = mockk<MailUserSession> {
        coEvery { watchNotifications() } returns MailUserSessionWatchNotificationsResult.Ok(stream)
    }
    private val userSession = mockk<MailUserSessionWrapper> {
        every { getRustUserSession() } returns rustSession
    }
    private val userSessionRepository = mockk<UserSessionRepository> {
        every { observeAccounts() } returns accounts
        coEvery { getUserSession(ReadyAccount.userId) } returns userSession
    }
    private val processNewMessage = mockk<ProcessNewMessagePushNotification> {
        coEvery { this@mockk(any()) } returns ListenableWorker.Result.success()
    }
    private val processMessageRead = mockk<ProcessMessageReadPushNotification> {
        every { this@mockk(any()) } returns ListenableWorker.Result.success()
    }

    @Test
    fun `new message events from the rust stream are shown as notifications`() = runTest {
        // Given
        val event = LocalMailNotificationEvent.NewMessage(NewMessage)
        coEvery { stream.nextAsync() } returns
            LocalMailNotificationStreamNextAsyncResult.Ok(event) coAndThen { awaitCancellation() }

        // When
        watcher(this.backgroundScope).start()
        runCurrent()

        // Then
        coVerify(exactly = 1) {
            processNewMessage(
                LocalPushNotification.Message.NewMessage(
                    UserData,
                    LocalPushNotificationData.MessagePushData.NewMessagePushData(
                        sender = PushNotificationSenderData("Sender", "sender@proton.me", ""),
                        messageId = RemoteMessageId,
                        content = "Subject"
                    )
                )
            )
        }
    }

    @Test
    fun `dismiss events from the rust stream dismiss the notification`() = runTest {
        // Given
        coEvery { stream.nextAsync() } returns
            LocalMailNotificationStreamNextAsyncResult.Ok(
                LocalMailNotificationEvent.Dismiss(messageId = 1uL, remoteMessageId = RemoteMessageId)
            ) coAndThen { awaitCancellation() }

        // When
        watcher(this.backgroundScope).start()
        runCurrent()

        // Then
        verify(exactly = 1) {
            processMessageRead(
                LocalPushNotification.Message.MessageRead(
                    UserData,
                    LocalPushNotificationData.MessagePushData.MessageReadPushData(RemoteMessageId)
                )
            )
        }
    }

    @Test
    fun `only ready accounts are watched and start is idempotent`() = runTest {
        // Given
        coEvery { stream.nextAsync() } coAnswers { awaitCancellation() }
        val watcher = watcher(this.backgroundScope)

        // When
        watcher.start()
        watcher.start()
        runCurrent()

        // Then
        coVerify(exactly = 1) { userSessionRepository.getUserSession(ReadyAccount.userId) }
        coVerify(exactly = 0) { userSessionRepository.getUserSession(NotReadyAccount.userId) }
        coVerify(exactly = 1) { rustSession.watchNotifications() }
    }

    @Test
    fun `account that becomes ready later starts being watched`() = runTest {
        // Given
        accounts.value = emptyList()
        coEvery { stream.nextAsync() } coAnswers { awaitCancellation() }
        watcher(this.backgroundScope).start()
        runCurrent()
        coVerify(exactly = 0) { rustSession.watchNotifications() }

        // When
        accounts.value = listOf(ReadyAccount)
        runCurrent()

        // Then
        coVerify(exactly = 1) { rustSession.watchNotifications() }
    }

    private fun watcher(scope: CoroutineScope) = NativeNotificationsWatcher(
        userSessionRepository = userSessionRepository,
        processNewMessageNotification = processNewMessage,
        processMessageReadNotification = processMessageRead,
        appScope = scope
    )

    private companion object {

        const val RemoteMessageId = "remote-message-id"

        val ReadyAccount = Account(
            userId = UserId("ready-user"),
            name = "ready",
            state = AccountState.Ready,
            primaryAddress = "ready@proton.me"
        )
        val NotReadyAccount = Account(
            userId = UserId("not-ready-user"),
            name = "not ready",
            state = AccountState.TwoFactorNeeded,
            primaryAddress = "notready@proton.me"
        )
        val UserData = LocalPushNotificationData.UserPushData(ReadyAccount.userId, ReadyAccount.primaryAddress)

        val NewMessage = LocalNewMessageNotification(
            messageId = 1uL,
            remoteMessageId = RemoteMessageId,
            conversationId = 2uL,
            labelId = 3uL,
            labelType = LabelType.SYSTEM,
            senderName = "Sender",
            senderAddress = "sender@proton.me",
            subject = "Subject",
            timestamp = 0uL
        )
    }
}

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

package me.proton.android.core.payment.google.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import me.proton.android.core.payment.domain.PaymentErrorCode
import me.proton.android.core.payment.domain.PaymentException
import me.proton.android.core.payment.domain.PaymentManager
import me.proton.android.core.payment.domain.model.PlayStoreProductOfferList
import me.proton.android.core.payment.domain.model.Purchase
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google-free (F-Droid) [PaymentManager]: the proprietary Google Play Billing library is not shipped, so the
 * store always reports billing as unavailable and no store purchases exist. Subscriptions can still be
 * managed on the web.
 */
@Singleton
class PaymentManagerImpl @Inject constructor() : PaymentManager {

    override suspend fun getStoreProducts(ids: List<String>): List<PlayStoreProductOfferList> =
        throw PaymentException(PaymentErrorCode.BILLING_UNAVAILABLE.value, BillingUnavailableMessage)

    override suspend fun getStorePurchases(): List<Purchase> = emptyList()

    override fun observeStorePurchases(): Flow<List<Purchase>> = flowOf(emptyList())

    private companion object {

        const val BillingUnavailableMessage = "Google Play Billing is not available in this build"
    }
}

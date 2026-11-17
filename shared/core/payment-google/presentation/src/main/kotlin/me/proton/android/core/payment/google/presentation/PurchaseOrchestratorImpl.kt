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

package me.proton.android.core.payment.google.presentation

import android.app.Activity
import me.proton.android.core.payment.domain.LogTag
import me.proton.android.core.payment.domain.model.ProductOfferToken
import me.proton.android.core.payment.presentation.PurchaseOrchestrator
import me.proton.core.util.kotlin.CoreLogger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google-free (F-Droid) [PurchaseOrchestrator]: Google Play Billing is not shipped, so in-app purchases cannot be
 * started. The UI never offers them because Google Play Services are reported as missing.
 */
@Singleton
class PurchaseOrchestratorImpl @Inject constructor() : PurchaseOrchestrator {

    override fun startPurchaseWorkflow(
        caller: Activity,
        productId: String,
        offerToken: ProductOfferToken,
        accountId: String
    ) {
        CoreLogger.d(LogTag.STORE, "In-app purchases are not available in this build (productId=$productId)")
    }
}

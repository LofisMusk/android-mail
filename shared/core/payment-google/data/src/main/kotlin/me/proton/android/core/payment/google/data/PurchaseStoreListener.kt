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

import me.proton.android.core.payment.domain.LogTag
import me.proton.android.core.payment.domain.PurchaseListener
import me.proton.android.core.payment.domain.model.Purchase
import me.proton.core.util.kotlin.CoreLogger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google-free (F-Droid) [PurchaseListener]: there is no store to receive purchases from, updates are only logged.
 */
@Singleton
class PurchaseStoreListener @Inject constructor() : PurchaseListener {

    override fun onPurchaseListUpdated(list: List<Purchase>) {
        CoreLogger.d(LogTag.DEFAULT, "onPurchaseListUpdated ignored, store billing is not available -> $list")
    }
}

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

import me.proton.android.core.payment.google.domain.GoogleServicesAvailability
import me.proton.android.core.payment.google.domain.GoogleServicesUtils
import javax.inject.Inject

/**
 * Google-free (F-Droid) [GoogleServicesUtils]: Google Play Services are never used by this build, so they are
 * always reported as missing. This keeps every Play-Store-only flow (e.g. in-app purchases) disabled.
 */
public class GoogleServicesUtilsImpl @Inject constructor() : GoogleServicesUtils {

    override fun getApkVersion(): Int = 0

    override fun getPlayServicesAvailability(): GoogleServicesAvailability = GoogleServicesAvailability.ServiceMissing
}

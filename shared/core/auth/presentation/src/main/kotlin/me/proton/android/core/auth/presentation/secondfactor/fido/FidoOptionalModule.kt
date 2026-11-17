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

package me.proton.android.core.auth.presentation.secondfactor.fido

import android.app.Activity
import androidx.activity.result.ActivityResultCaller
import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import me.proton.core.auth.fido.domain.usecase.PerformTwoFaWithSecurityKey

/**
 * The only FIDO2 implementation shipped by Proton Core (`auth-fido-play`) depends on the proprietary
 * Google Play Services FIDO API, so it is not included in the Google-free (F-Droid) build. Declaring the
 * binding as optional keeps security-key 2FA disabled; TOTP based 2FA keeps working.
 */
@Module
@InstallIn(SingletonComponent::class)
interface FidoOptionalModule {

    @BindsOptionalOf
    fun optionalPerformTwoFaWithSecurityKey(): PerformTwoFaWithSecurityKey<ActivityResultCaller, Activity>
}

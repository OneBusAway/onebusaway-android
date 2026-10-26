/*
 * Copyright (C) 2026 Open Transit Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.android.database.oba

import org.onebusaway.android.models.ObaStop
import org.onebusaway.android.util.MyTextUtils

/** A user's customization of a stop: whether it's a favorite, and any custom name. */
data class StopUserInfo(val isFavorite: Boolean, val userName: String?)

/**
 * The name to display for a stop: the user's custom name if they renamed it, otherwise the
 * formatted server name.
 */
fun stopDisplayName(stop: ObaStop, userInfo: StopUserInfo?): String = stopDisplayName(stop.name, userInfo)

/** Field-based overload, for callers (e.g. the api/ REST DTOs) without an [ObaStop]. */
fun stopDisplayName(serverName: String?, userInfo: StopUserInfo?): String = customStopName(userInfo?.userName) ?: MyTextUtils.formatDisplayText(serverName).orEmpty()

/** Whether [stopDisplayName] resolves to the rider's own name, which is shown with its glyph (#2414). */
val StopUserInfo?.hasCustomName: Boolean get() = customStopName(this?.userName) != null

/**
 * A stored `user_name` as a custom name, or null when there is none. The one Kotlin statement of the
 * rule that an empty name is no name (the list SQL's `UI_NAME` states it for sorting); new writes never
 * store one ([normalizedUserName]), but rows imported before the importer normalized it may hold one.
 */
fun customStopName(userName: String?): String? = userName?.takeIf { it.isNotEmpty() }

/**
 * What a rename stores as the custom name: the rider's [input], trimmed, or null — meaning "show the
 * official name" — when it's blank or exactly the [officialName]. Storing the official name verbatim
 * would freeze it, so a later server-side rename would never show through.
 */
fun normalizedUserName(input: String, officialName: String?): String? = input.trim().takeIf { it.isNotEmpty() && it != officialName }

/**
 * Builds the id → [StopUserInfo] map from the Room rows (the legacy UIUtils.StopUserInfoMap), so each
 * search result can show the star and the user's custom name. Callers fetch the rows via
 * `StopDao.userInfoMap()` after the one-time import gate.
 */
fun List<StopUserInfoMapRow>.toStopUserInfoMap(): Map<String, StopUserInfo> = associate { it.stopId to StopUserInfo(isFavorite = it.favorite == 1, userName = it.userName) }

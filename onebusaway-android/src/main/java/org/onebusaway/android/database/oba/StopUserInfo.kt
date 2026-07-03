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
fun stopDisplayName(stop: ObaStop, userInfo: StopUserInfo?): String =
    stopDisplayName(stop.name, userInfo)

/** Field-based overload, for callers (e.g. the api/ REST DTOs) without an [ObaStop]. */
fun stopDisplayName(serverName: String?, userInfo: StopUserInfo?): String =
    userInfo?.userName?.takeIf { it.isNotEmpty() } ?: MyTextUtils.formatDisplayText(serverName).orEmpty()

/**
 * Builds the id → [StopUserInfo] map from the Room rows (the legacy UIUtils.StopUserInfoMap), so each
 * search result can show the star and the user's custom name. Callers fetch the rows via
 * `StopDao.userInfoMap()` after the one-time import gate.
 */
fun List<StopUserInfoMapRow>.toStopUserInfoMap(): Map<String, StopUserInfo> =
    associate { it.stopId to StopUserInfo(isFavorite = it.favorite == 1, userName = it.userName) }

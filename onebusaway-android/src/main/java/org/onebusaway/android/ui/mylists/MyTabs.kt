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
package org.onebusaway.android.ui.mylists

import android.net.Uri

/**
 * The stable tab identifiers + `tab://` deep-link helpers for the `My*` tab activities, relocated
 * here from the (deleted) `MyTabActivityBase` and the list/search fragments' `TAB_NAME` constants.
 *
 * The tag strings and the `tab` URI scheme are a **frozen contract**: the `MyStarredStops`/`MyRecent*`
 * shortcut shells (and launcher shortcuts pinned by ancient app versions) deep-link into the tab
 * activities via `tab://<tag>`, so neither the scheme nor the tags may change.
 */
object MyTabs {

    const val RECENT_STOPS = "recent_stops"
    const val STARRED_STOPS = "starred"
    const val RECENT_ROUTES = "recent_routes"
    const val SEARCH = "search"

    private const val SCHEME = "tab"

    /** The tab tag carried by a `tab://<tag>` URI, or null if [uri] isn't a tab link. */
    fun defaultTabFromUri(uri: Uri): String? =
        uri.takeIf { it.scheme == SCHEME }?.schemeSpecificPart
}

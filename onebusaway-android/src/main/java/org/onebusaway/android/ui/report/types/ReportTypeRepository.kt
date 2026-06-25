/*
 * Copyright (C) 2014 University of South Florida,
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
package org.onebusaway.android.ui.report.types

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import edu.usf.cutr.open311client.Open311Manager
import org.onebusaway.android.R
import org.onebusaway.android.region.RegionRepository

/** Builds the "Send feedback" type list for the current region. */
interface ReportTypeRepository {

    fun reportTypes(): List<ReportType>
}

/**
 * Default implementation, replacing the array-wrangling in ReportTypeListFragment. Picks the
 * Open311 vs. non-Open311 type set, pairs titles/descriptions/icons with their [ReportAction]s, and
 * applies the region-email gate via [ReportTypeGate].
 */
class DefaultReportTypeRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val regionRepository: RegionRepository,
) : ReportTypeRepository {

    override fun reportTypes(): List<ReportType> {
        val open311Active = regionRepository.region.value != null && Open311Manager.isOpen311Exist()

        val titles = context.resources.getStringArray(
            if (open311Active) R.array.report_types else R.array.report_types_without_open311
        )
        val descriptions = context.resources.getStringArray(
            if (open311Active) R.array.report_types_desc else R.array.report_types_desc_without_open311
        )
        val icons = context.resources.obtainTypedArray(
            if (open311Active) R.array.report_types_icons else R.array.report_types_icons_without_open311
        )

        val types = titles.indices.map { index ->
            ReportType(
                title = titles[index],
                description = descriptions.getOrElse(index) { "" },
                iconRes = icons.getResourceId(index, 0),
                action = REPORT_TYPE_ACTIONS.getOrElse(index) { ReportAction.APP_FEEDBACK }
            )
        }
        icons.recycle()

        val emailDefined = regionRepository.region.value?.contactEmail != null
        return ReportTypeGate.apply(types, emailDefined)
    }
}

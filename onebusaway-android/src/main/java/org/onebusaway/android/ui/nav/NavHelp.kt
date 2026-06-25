/*
 * Copyright (C) 2013 Paul Watts (paulcwatts@gmail.com)
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
package org.onebusaway.android.ui.nav

import android.content.Context
import android.content.Intent
import org.onebusaway.android.ui.HomeActivity
import org.onebusaway.android.ui.tutorial.TutorialPrefs

object NavHelp {

    /**
     * Go back to the HomeActivity
     *
     * @param showTutorial true if the welcome tutorial should be started, false if it should not
     */
    fun goHome(context: Context, showTutorial: Boolean) {
        val intent = Intent(context, HomeActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (showTutorial) {
            intent.putExtra(TutorialPrefs.TUTORIAL_WELCOME, true)
        }
        context.startActivity(intent)
    }
}

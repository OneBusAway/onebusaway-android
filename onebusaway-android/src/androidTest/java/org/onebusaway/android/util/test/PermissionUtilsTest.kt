package org.onebusaway.android.util.test

import android.Manifest
import androidx.test.runner.AndroidJUnit4
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.onebusaway.android.util.PermissionUtils

@RunWith(AndroidJUnit4::class)
class PermissionUtilsTest {

    @Test
    fun constantsHaveRightValues(){
        assertEquals(1, PermissionUtils.LOCATION_PERMISSION_REQUEST)
        assertEquals(2, PermissionUtils.SAVE_BACKUP_PERMISSION_REQUEST)
        assertEquals(3, PermissionUtils.RESTORE_BACKUP_PERMISSION_REQUEST)
        assertEquals(4, PermissionUtils.BACKGROUND_LOCATION_PERMISSION_REQUEST)
        assertEquals(5, PermissionUtils.NOTIFICATION_PERMISSION_REQUEST)
    }

    @Test
    fun locationPermissionsContainsTheRightPermissions(){
        assertTrue(PermissionUtils.LOCATION_PERMISSIONS
            .contains(Manifest.permission.ACCESS_FINE_LOCATION))
        assertTrue(PermissionUtils.LOCATION_PERMISSIONS
            .contains(Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    @Test
    fun storagePermissionsContainsTheRightPermissions(){
        assertTrue(PermissionUtils.STORAGE_PERMISSIONS
            .contains(Manifest.permission.READ_EXTERNAL_STORAGE))
        assertTrue(PermissionUtils.STORAGE_PERMISSIONS
            .contains(Manifest.permission.WRITE_EXTERNAL_STORAGE))
    }
}
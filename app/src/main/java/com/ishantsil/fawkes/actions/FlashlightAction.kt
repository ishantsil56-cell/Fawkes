package com.ishantsil.fawkes.actions

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager

/** Flashlight on/off via CameraManager. Needs the CAMERA permission. */
object FlashlightAction {

    fun setEnabled(context: Context, on: Boolean): String {
        return if (setTorch(context, on)) {
            if (on) "Flashlight on." else "Flashlight off."
        } else {
            "I couldn't reach the flashlight. Check the camera permission."
        }
    }

    /** Silent torch toggle used by the SOS blinker. Returns false on failure. */
    fun setTorch(context: Context, on: Boolean): Boolean {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        return try {
            val cameraId = manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return false
            manager.setTorchMode(cameraId, on)
            true
        } catch (_: CameraAccessException) {
            false
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    fun isAvailable(context: Context): Boolean {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        return try {
            manager.cameraIdList.any { id ->
                manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (_: Exception) {
            false
        }
    }
}

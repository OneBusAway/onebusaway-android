/*
 * Copyright (C) 2010-2017 Paul Watts (paulcwatts@gmail.com),
 * University of South  Florida (sjbarbeau@gmail.com), Microsoft Corporation
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

package org.onebusaway.android.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.os.Environment
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A class containing utility methods related to bitmaps and image files.
 */
object BitmapUtils {

    /**
     * Creates a new Bitmap, with the black color of the source image changed to the given color.
     * The source Bitmap isn't modified.
     *
     * @param source the source Bitmap with a black background
     * @param color  the color to change the black color to
     * @return the resulting Bitmap that has the black changed to the color
     */
    @JvmStatic
    fun colorBitmap(source: Bitmap, color: Int): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        for (x in pixels.indices) {
            pixels[x] = if (pixels[x] == Color.BLACK) color else pixels[x]
        }

        val out = Bitmap.createBitmap(width, height, source.config!!)
        out.setPixels(pixels, 0, width, 0, 0, width, height)
        return out
    }

    /**
     * Creates a JPEG image file with the current date/time as the name
     *
     * @param nameSuffix A string that will be added to the end of the file name, or null if
     *                   nothing
     *                   should be added
     * @return a JPEG image file with the current date/time as the name
     */
    @Throws(IOException::class)
    fun createImageFile(context: Context, nameSuffix: String?): File {
        // Create an image file name
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val imageFileName = StringBuilder()
        imageFileName.append("JPEG_")
        imageFileName.append(timeStamp)
        imageFileName.append("_")
        if (nameSuffix != null) {
            imageFileName.append(nameSuffix)
        }
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            imageFileName.toString(),  /* prefix */
            ".jpg",         /* suffix */
            storageDir      /* directory */
        )
    }

    /**
     * Decode a smaller sampled bitmap given a large bitmap.
     * Adapted from https://developer.android.com/training/displaying-bitmaps/load-bitmap.html and
     * http://stackoverflow.com/a/31720143/937715.
     *
     * @param pathName  path to the full size image file
     * @param reqWidth  desired width
     * @param reqHeight desired height
     * @return a smaller version of the image at pathName, given the desired width and height
     */
    @Throws(IOException::class)
    fun decodeSampledBitmapFromFile(pathName: String, reqWidth: Int, reqHeight: Int): Bitmap {
        // First decode with inJustDecodeBounds=true to check dimensions
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFile(pathName, options)

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false

        val b = BitmapFactory.decodeFile(pathName, options)
        return rotateImageIfRequired(b, pathName)
    }

    /**
     * Calculate an inSampleSize for use in a [BitmapFactory.Options] object when decoding
     * bitmaps using the decode* methods from [BitmapFactory]. This implementation calculates
     * the closest inSampleSize that will result in the final decoded bitmap having a width and
     * height equal to or larger than the requested width and height. This implementation does not
     * ensure a power of 2 is returned for inSampleSize which can be faster when decoding but
     * results in a larger bitmap which isn't as useful for caching purposes.
     *
     * From http://stackoverflow.com/a/31720143/937715.
     *
     * @param options   An options object with out* params already populated (run through a decode*
     *                  method with inJustDecodeBounds==true
     * @param reqWidth  The requested width of the resulting bitmap
     * @param reqHeight The requested height of the resulting bitmap
     * @return The value to be used for inSampleSize
     */
    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int, reqHeight: Int
    ): Int {
        // Raw height and width of image
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            // Calculate ratios of height and width to requested height and width
            val heightRatio = Math.round(height.toFloat() / reqHeight.toFloat())
            val widthRatio = Math.round(width.toFloat() / reqWidth.toFloat())

            // Choose the smallest ratio as inSampleSize value, this will guarantee a final image
            // with both dimensions larger than or equal to the requested height and width.
            inSampleSize = if (heightRatio < widthRatio) heightRatio else widthRatio

            // This offers some additional logic in case the image has a strange
            // aspect ratio. For example, a panorama may have a much larger
            // width than height. In these cases the total pixels might still
            // end up being too large to fit comfortably in memory, so we should
            // be more aggressive with sample down the image (=larger inSampleSize).

            val totalPixels = (width * height).toFloat()

            // Anything more than 2x the requested pixels we'll sample down further
            val totalReqPixelsCap = (reqWidth * reqHeight * 2).toFloat()

            while (totalPixels / (inSampleSize * inSampleSize) > totalReqPixelsCap) {
                inSampleSize++
            }
        }
        return inSampleSize
    }

    /**
     * Rotate an image if required.
     *
     * @param img       The image bitmap
     * @param imagePath Path to image
     * @return The resulted Bitmap after manipulation
     */
    @Throws(IOException::class)
    private fun rotateImageIfRequired(img: Bitmap, imagePath: String): Bitmap {
        val ei = ExifInterface(imagePath)
        val orientation = ei
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)

        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> rotateImage(img, 90)
            ExifInterface.ORIENTATION_ROTATE_180 -> rotateImage(img, 180)
            ExifInterface.ORIENTATION_ROTATE_270 -> rotateImage(img, 270)
            else -> img
        }
    }

    /**
     * Rotate the given bitmap
     *
     * @param img    image to rotate
     * @param degree number of degrees to rotate, from 0-360
     * @return the provided bitmap rotated by the given number of degrees
     */
    private fun rotateImage(img: Bitmap, degree: Int): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degree.toFloat())
        val rotatedImg = Bitmap
            .createBitmap(img, 0, 0, img.width, img.height, matrix, true)
        img.recycle()
        return rotatedImg
    }
}

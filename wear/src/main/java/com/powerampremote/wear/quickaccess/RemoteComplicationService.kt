package com.powerampremote.wear.quickaccess

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import com.powerampremote.wear.MainActivity
import com.powerampremote.wear.R

/** Atalho estático: não representa conexão, música atual ou estado de reprodução. */
class RemoteComplicationService : ComplicationDataSourceService() {
    override fun onComplicationRequest(request: ComplicationRequest, listener: ComplicationRequestListener) {
        listener.onComplicationData(data(request.complicationType, preview = false))
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? = data(type, preview = true)

    private fun data(type: ComplicationType, preview: Boolean): ComplicationData? {
        val description = PlainComplicationText.Builder(getString(R.string.quick_access_description)).build()
        val image = MonochromaticImage.Builder(Icon.createWithResource(this, R.drawable.ic_remote)).build()
        // A prévia só descreve a aparência; o atalho real recebe a ação imutável de abertura.
        val tap = if (preview) null else PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                PlainComplicationText.Builder(getString(R.string.quick_access_complication_text)).build(), description)
                .setTitle(PlainComplicationText.Builder(getString(R.string.quick_access_complication_title)).build())
                .setMonochromaticImage(image).setTapAction(tap).build()
            ComplicationType.MONOCHROMATIC_IMAGE -> MonochromaticImageComplicationData.Builder(image, description)
                .setTapAction(tap).build()
            else -> null
        }
    }
}

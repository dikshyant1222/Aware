package com.aware.app.speech

import android.content.Context
import java.util.Locale

/** Caller must release AudioRecord before starting this recognizer. */
class MicTranscriber(
    context: Context,
    locale: Locale = Locale.getDefault(),
    onError: (Int?) -> Unit = {},
) : Transcriber by SharedAudioTranscriber(context, null, locale, onError)

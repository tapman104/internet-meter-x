package com.internetspeed.meterlite

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics

object FlavorInitializer {
    fun init(context: Context) {
        // Initialize Firebase Analytics for the firebase flavor
        FirebaseAnalytics.getInstance(context)
    }
}

package com.lagradost.cloudstream3

import android.app.Activity
import androidx.appcompat.app.AppCompatActivity

open class CommonActivity : AppCompatActivity() {
    companion object {
        @JvmStatic
        var activity: Activity?
            get() = AcraApplication.getActivity()
            set(value) {
                if (value != null) AcraApplication.setActivity(value)
            }
    }
}

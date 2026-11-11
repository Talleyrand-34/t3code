package com.t3tools.peer.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** The activity that hosts this composition, if there is one. */
internal tailrec fun Context.findActivity(): Activity? =
  when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
  }

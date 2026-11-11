package com.t3tools.peer.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** The activity that hosts this composition, if there is one. */
@Composable
fun hostActivity(): Activity? = LocalContext.current.findActivity()

internal tailrec fun Context.findActivity(): Activity? =
  when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
  }

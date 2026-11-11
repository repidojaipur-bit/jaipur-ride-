package com.drivepartner.app.core.common

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * Abstraction for UI text so ViewModels avoid hardcoded user-facing strings and
 * localization resources remain testable without a Context.
 */
sealed interface UiText {
    data class DynamicString(val value: String) : UiText

    data class StringResource(
        @StringRes val resId: Int,
        val args: List<Any> = emptyList()
    ) : UiText {
        constructor(@StringRes resId: Int, vararg formatArgs: Any) : this(
            resId = resId,
            args = formatArgs.toList()
        )
    }

    @Composable
    fun asString(): String = when (this) {
        is DynamicString -> value
        is StringResource -> if (args.isEmpty()) {
            stringResource(resId)
        } else {
            stringResource(resId, *args.toTypedArray())
        }
    }

    fun asString(context: Context): String = when (this) {
        is DynamicString -> value
        is StringResource -> if (args.isEmpty()) {
            context.getString(resId)
        } else {
            context.getString(resId, *args.toTypedArray())
        }
    }
}

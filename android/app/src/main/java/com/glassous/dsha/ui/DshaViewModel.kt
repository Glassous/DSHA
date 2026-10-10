package com.glassous.dsha.ui

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

/**
 * 用 Application 加额外参数构造 ViewModel 的极简工厂，
 * 省去为每个需要参数的 ViewModel 单独写 Factory 类。
 */
@Composable
inline fun <reified VM : ViewModel> dshaViewModel(
    key: String? = null,
    crossinline create: (Application) -> VM,
): VM {
    val application = LocalContext.current.applicationContext as Application
    return viewModel(
        key = key,
        factory = viewModelFactory { initializer<VM> { create(application) } },
    )
}

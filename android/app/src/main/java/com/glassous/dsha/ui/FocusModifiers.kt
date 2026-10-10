package com.glassous.dsha.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager

/**
 * 点击空白区域、顶部栏等非输入区域时清除焦点，让输入框收起键盘。
 *
 * 挂在页面根布局（如 `Scaffold`）上即可：命中测试依旧由子组件优先处理，
 * 输入框、按钮、可滚动区域已经消费掉的点击不会触发这里，正常交互不受影响。
 */
@Composable
fun Modifier.clearFocusOnTap(): Modifier {
    val focusManager = LocalFocusManager.current
    return pointerInput(focusManager) {
        detectTapGestures(onTap = { focusManager.clearFocus() })
    }
}

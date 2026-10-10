package com.glassous.dsha.ui.transition

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale

/**
 * 共享元素（容器变换）组件：让一个界面里的元素与另一个界面的容器在转场中连成同一块内容。
 *
 * 接入分三步：
 * 1. 导航图外层套 [SharedElementHost]，提供全局共享元素作用域与绘制 overlay；
 * 2. 参与共享的每个页面用 [SharedElementDestination] 包一层，登记本页自身的进出场作用域；
 * 3. 元素两端分别标注 [containerTransformSource] 与 [ContainerTransformTarget]，key 相同即自动匹配。
 *
 * 目前只有「新增连接按钮 → 新建连接组页」接入：按钮的圆角方块展开成整页容器，页面内容随
 * 容器展开淡入；返回时整页收缩回按钮。开启与关闭走的是同一段过渡，只是方向相反。
 */

/** 共享元素 key。两端 key 相同才会被匹配成同一个元素。 */
object SharedElementKey {

    /** 新增连接按钮（列表页悬浮按钮 / 空状态页按钮）↔「新建连接组」页整页容器。 */
    const val NewProfileContainer = "newProfileContainer"
}

/**
 * 容器展开 / 收起时长（毫秒）。
 * 参与共享的页面，其导航转场时长必须不短于此值：退出中的页面一旦提前销毁，
 * 共享元素动画会被从中间截断（见 [ContainerTransformPageExit]）。
 */
const val CONTAINER_TRANSFORM_DURATION_MS = 300

/** 源端按钮自身内容的淡出 / 淡入时长（毫秒）：先完成颜色交接，再让容器接着展开。 */
private const val SOURCE_FADE_MS = 140

/** 目标端页面内容的淡入 / 淡出时长（毫秒）：比容器展开略早结束，后半程是干净的展开。 */
private const val CONTENT_FADE_MS = 220

/**
 * 边界动画固定为 [CONTAINER_TRANSFORM_DURATION_MS] 的 tween（而非默认弹簧）：
 * 弹簧会多跑一小段收尾，导致共享元素比导航转场更晚结束。
 */
private val ContainerTransformBounds: BoundsTransform = BoundsTransform { _, _ ->
    tween(durationMillis = CONTAINER_TRANSFORM_DURATION_MS, easing = FastOutSlowInEasing)
}

/**
 * 两端内容按"完整放下"缩放：源端按钮与目标页面在彼此的边界里都完整可见，
 * 不会在展开途中被裁掉一角（按钮接近正方形、页面是竖长屏，两者比例差异很大）。
 */
private val ContainerTransformResizeMode: SharedTransitionScope.ResizeMode =
    SharedTransitionScope.ResizeMode.scaleToBounds(
        contentScale = ContentScale.Fit,
        alignment = Alignment.Center,
    )

/**
 * 参与容器变换页面的进场转场：位移与淡入全部交给共享元素，页面本身直接到位即可，
 * 否则页面会先滑进来一次、再被共享元素接管。
 */
val ContainerTransformPageEnter: EnterTransition = EnterTransition.None

/**
 * 参与容器变换页面的收起转场：退出中的页面必须留到共享元素动画结束才销毁，这里占满整段时长。
 * 页面内容此时渲染在共享元素 overlay 上，不受这段淡出的影响，因此它只负责"拖住"页面、不改变观感。
 */
val ContainerTransformPageExit: ExitTransition =
    fadeOut(tween(durationMillis = CONTAINER_TRANSFORM_DURATION_MS))

/** 共享元素作用域与目的地进出场作用域，只在本文件的组件内部流转。 */
private val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }
private val LocalDestinationVisibilityScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/** 应用级共享元素宿主：套在导航图外层，为整棵树提供共享元素能力与绘制 overlay。 */
@Composable
fun SharedElementHost(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    SharedTransitionLayout(modifier = modifier) {
        // 作用域经 CompositionLocal 下发，页面内部不必逐层透传参数
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            content()
        }
    }
}

/**
 * 导航目的地：登记本页自己的进出场作用域，供共享元素判断元素是"从哪来 / 往哪去"。
 * 未包这一层时，页内的共享元素标注会静默失效（按普通布局渲染），不会崩。
 */
@Composable
fun SharedElementDestination(
    animatedVisibilityScope: AnimatedVisibilityScope,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalDestinationVisibilityScope provides animatedVisibilityScope) {
        content()
    }
}

/**
 * 源端：把按钮之类的入口标记成共享元素的起点。
 * 转场中它的边界被目标容器接管，自身内容（图标 / 文字）先一步淡出，
 * 避免内容跟着边界一起放大、糊在展开中的页面上。
 */
@Composable
fun Modifier.containerTransformSource(key: Any): Modifier {
    val scope = LocalSharedTransitionScope.current ?: return this
    val visibilityScope = LocalDestinationVisibilityScope.current ?: return this
    val source = this
    val contentState = with(scope) { rememberSharedContentState(key) }
    return with(scope) {
        source.sharedBounds(
            sharedContentState = contentState,
            animatedVisibilityScope = visibilityScope,
            enter = fadeIn(tween(durationMillis = SOURCE_FADE_MS)),
            exit = fadeOut(tween(durationMillis = SOURCE_FADE_MS)),
            boundsTransform = ContainerTransformBounds,
        )
    }
}

/**
 * 目标端：整页容器。
 *
 * 转场中它的边界从源端按钮展开成整页、页面内容随展开淡入；收起时反向收缩回按钮。
 * 容器内容（整页，含页面底色）一起参与缩放，所以"按钮展开成整页"这一步两端是连续的。
 *
 * @param key 与源端一致的共享元素 key。
 * @param enabled 为 false 时不参与共享元素（例如编辑既有连接组），按普通转场进出。
 */
@Composable
fun ContainerTransformTarget(
    key: Any,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val scope = LocalSharedTransitionScope.current
    val visibilityScope = LocalDestinationVisibilityScope.current
    if (!enabled || scope == null || visibilityScope == null) {
        Box(modifier = modifier.fillMaxSize()) { content() }
        return
    }

    val contentState = with(scope) { rememberSharedContentState(key) }
    val container = with(scope) {
        modifier.fillMaxSize().sharedBounds(
            sharedContentState = contentState,
            animatedVisibilityScope = visibilityScope,
            enter = fadeIn(tween(durationMillis = CONTENT_FADE_MS)),
            exit = fadeOut(tween(durationMillis = CONTENT_FADE_MS)),
            boundsTransform = ContainerTransformBounds,
            resizeMode = ContainerTransformResizeMode,
            // 展开中的页面压在源端按钮之上：按钮的淡出与放大被页面盖住，不会在页面边缘露出残影
            zIndexInOverlay = 1f,
        )
    }
    Box(modifier = container) { content() }
}

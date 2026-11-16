package com.simplefox.browser.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/**
 * Animation-free replacements for the Compose animation APIs used across the app.
 * Files import these instead of the androidx versions, so state changes apply instantly.
 * Signatures match the originals, so call sites are unchanged.
 */

@Composable
fun animateFloatAsState(
    targetValue: Float,
    animationSpec: AnimationSpec<Float> = spring(),
    visibilityThreshold: Float = 0.01f,
    label: String = "FloatAnimation",
    finishedListener: ((Float) -> Unit)? = null
): State<Float> = rememberUpdatedState(targetValue)

@Composable
fun animateColorAsState(
    targetValue: Color,
    animationSpec: AnimationSpec<Color> = spring(),
    label: String = "ColorAnimation",
    finishedListener: ((Color) -> Unit)? = null
): State<Color> = rememberUpdatedState(targetValue)

@Composable
fun animateDpAsState(
    targetValue: Dp,
    animationSpec: AnimationSpec<Dp> = spring(),
    label: String = "DpAnimation",
    finishedListener: ((Dp) -> Unit)? = null
): State<Dp> = rememberUpdatedState(targetValue)

@Composable
fun AnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    enter: EnterTransition = EnterTransition.None,
    exit: ExitTransition = ExitTransition.None,
    label: String = "AnimatedVisibility",
    content: @Composable () -> Unit
) {
    if (visible) Box(modifier) { content() }
}

@Composable
fun <S> AnimatedContent(
    targetState: S,
    modifier: Modifier = Modifier,
    transitionSpec: AnimatedContentTransitionScope<S>.() -> ContentTransform = {
        ContentTransform(EnterTransition.None, ExitTransition.None)
    },
    contentAlignment: Alignment = Alignment.TopStart,
    label: String = "AnimatedContent",
    contentKey: (targetState: S) -> Any? = { it },
    content: @Composable (targetState: S) -> Unit
) {
    Box(modifier, contentAlignment) { content(targetState) }
}

@Composable
fun <T> Crossfade(
    targetState: T,
    modifier: Modifier = Modifier,
    animationSpec: FiniteAnimationSpec<Float> = spring(),
    label: String = "Crossfade",
    content: @Composable (T) -> Unit
) {
    Box(modifier) { content(targetState) }
}

@Suppress("UnusedReceiverParameter")
fun Modifier.animateContentSize(
    animationSpec: FiniteAnimationSpec<androidx.compose.ui.unit.IntSize> = spring(),
    finishedListener: ((androidx.compose.ui.unit.IntSize, androidx.compose.ui.unit.IntSize) -> Unit)? = null
): Modifier = this

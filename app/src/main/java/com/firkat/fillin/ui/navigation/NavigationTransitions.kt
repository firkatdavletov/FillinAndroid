package com.firkat.fillin.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset

private val navigationSlideSpec = tween<IntOffset>(durationMillis = 300)
private val navigationFadeSpec = tween<Float>(durationMillis = 300)

internal fun fadeNavigation(): ContentTransform =
    fadeIn(navigationFadeSpec) togetherWith fadeOut(navigationFadeSpec)

internal fun AnimatedContentTransitionScope<*>.slideNavigation(
    direction: SlideDirection
): ContentTransform = slideIntoContainer(direction, navigationSlideSpec) togetherWith
    slideOutOfContainer(direction, navigationSlideSpec)

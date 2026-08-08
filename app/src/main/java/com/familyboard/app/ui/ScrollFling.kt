package com.familyboard.app.ui

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlin.math.abs

/**
 * 초기 속도를 [factor] 배로 키우고 저마찰 [decay] 로 감속 → 한 번의 스와이프로 더 멀리 스크롤.
 * (홈·맛집·가볼 곳 목록 공용. LazyColumn/verticalScroll 의 flingBehavior 로 사용)
 */
class BoostFling(
    private val decay: DecayAnimationSpec<Float>,
    private val factor: Float,
) : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        if (abs(initialVelocity) <= 1f) return initialVelocity
        var last = 0f
        var velocityLeft = initialVelocity
        AnimationState(initialValue = 0f, initialVelocity = initialVelocity * factor).animateDecay(decay) {
            val delta = value - last
            val consumed = scrollBy(delta)
            last = value
            velocityLeft = this.velocity
            if (abs(delta - consumed) > 0.5f) cancelAnimation()
        }
        return velocityLeft
    }
}

/** 시원한 스크롤용 fling: 기본 초기 속도 3.0배 + 저마찰(0.3). */
@Composable
fun rememberBoostFling(factor: Float = 3.0f, friction: Float = 0.3f): FlingBehavior =
    remember(factor, friction) { BoostFling(exponentialDecay(frictionMultiplier = friction), factor) }

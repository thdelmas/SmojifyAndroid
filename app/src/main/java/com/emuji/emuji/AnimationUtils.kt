package com.emuji.emuji

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.view.animation.AnimationUtils

/**
 * Utility class for common animation and haptic feedback operations.
 * Provides reusable methods for consistent UX across the app.
 */
object EmujiAnimationUtils {
    
    /**
     * Animates a button press with scale effect
     * @param view The view to animate
     * @param scale The scale factor (default 0.95 for 95%)
     * @param duration Animation duration in milliseconds
     * @param onComplete Callback when animation completes
     */
    fun animateButtonPress(
        view: View, 
        scale: Float = 0.95f, 
        duration: Long = 100L,
        onComplete: (() -> Unit)? = null
    ) {
        view.animate()
            .scaleX(scale)
            .scaleY(scale)
            .setDuration(duration)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(duration)
                    .withEndAction(onComplete)
                    .start()
            }
            .start()
    }
    
    /**
     * Animates a FAB button press with more pronounced scale effect
     * @param view The FAB view to animate
     * @param onComplete Callback when animation completes
     */
    fun animateFabPress(view: View, onComplete: (() -> Unit)? = null) {
        animateButtonPress(view, scale = 0.85f, duration = 100L, onComplete)
    }
    
    /**
     * Animates a card press with subtle scale effect
     * @param view The card view to animate
     * @param onComplete Callback when animation completes
     */
    fun animateCardPress(view: View, onComplete: (() -> Unit)? = null) {
        animateButtonPress(view, scale = 0.95f, duration = 100L, onComplete)
    }
    
    /**
     * Provides haptic feedback for user interactions
     * @param context Android context
     * @param duration Vibration duration in milliseconds (default 10ms)
     */
    fun performHapticFeedback(context: Context, duration: Long = 10L) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        vibrator?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                it.vibrate(
                    VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(duration)
            }
        }
    }
    
    /**
     * Applies fade-in animation to a view
     * @param view The view to animate
     */
    fun fadeIn(view: View) {
        val animation = AnimationUtils.loadAnimation(view.context, R.anim.fade_in)
        view.startAnimation(animation)
    }
    
    /**
     * Applies fade-out animation to a view
     * @param view The view to animate
     */
    fun fadeOut(view: View) {
        val animation = AnimationUtils.loadAnimation(view.context, R.anim.fade_out)
        view.startAnimation(animation)
    }
    
    /**
     * Applies slide-in-right animation to a view
     * @param view The view to animate
     */
    fun slideInRight(view: View) {
        val animation = AnimationUtils.loadAnimation(view.context, R.anim.slide_in_right)
        view.startAnimation(animation)
    }
    
    /**
     * Applies bounce-in animation to a view (good for emojis)
     * @param view The view to animate
     */
    fun bounceIn(view: View) {
        val animation = AnimationUtils.loadAnimation(view.context, R.anim.bounce_in)
        view.startAnimation(animation)
    }
    
    /**
     * Animates a list of views with staggered timing
     * @param views List of views to animate
     * @param animationType The animation resource ID
     * @param staggerDelay Delay between each view animation (default 100ms)
     * @param initialDelay Initial delay before first animation (default 100ms)
     */
    fun staggeredAnimation(
        views: List<View>,
        animationType: Int = R.anim.slide_in_right,
        staggerDelay: Long = 100L,
        initialDelay: Long = 100L
    ) {
        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.postDelayed({
                val animation = AnimationUtils.loadAnimation(view.context, animationType)
                view.startAnimation(animation)
                view.alpha = 1f
            }, initialDelay + (index * staggerDelay))
        }
    }
    
    /**
     * Standard activity transition constants
     */
    object Transitions {
        val ENTER_FROM_RIGHT = R.anim.slide_in_right
        val EXIT_TO_LEFT = R.anim.slide_out_left
        val ENTER_FROM_LEFT = R.anim.slide_in_left
        val EXIT_TO_RIGHT = R.anim.slide_out_right
    }
}

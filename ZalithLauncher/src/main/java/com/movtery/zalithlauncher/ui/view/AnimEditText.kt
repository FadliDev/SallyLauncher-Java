package com.movtery.zalithlauncher.ui.view

import android.animation.AnimatorInflater
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.ContextCompat
import com.movtery.anim.animations.Animations
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.utils.anim.ViewAnimUtils.Companion.setViewAnim

class AnimEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {
    init {
        stateListAnimator = AnimatorInflater.loadStateListAnimator(context, R.xml.anim_scale)
        ensureVisibleCaret()
    }

    /**
     * Some themes / parent styles (and dialogs created with another theme) leave the caret
     * with a color that blends into the field. Always draw it with the MD3 primary caret.
     */
    private fun ensureVisibleCaret() {
        isCursorVisible = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.getDrawable(context, R.drawable.md_cursor)?.let { textCursorDrawable = it }
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        post {
            pivotX = width / 2f
            pivotY = height / 2f
        }
    }

    override fun setError(error: CharSequence?) {
        super.setError(error)
        error?.let { setErrorAnim() }
    }

    override fun setError(error: CharSequence?, icon: Drawable?) {
        super.setError(error, icon)
        error?.let { setErrorAnim() }
    }

    private fun setErrorAnim() {
        setViewAnim(this, Animations.Shake)
    }
}

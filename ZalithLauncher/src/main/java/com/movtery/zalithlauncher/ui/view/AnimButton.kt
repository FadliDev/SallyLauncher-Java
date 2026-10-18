package com.movtery.zalithlauncher.ui.view

import android.animation.AnimatorInflater
import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import com.movtery.zalithlauncher.R

/**
 * Material Design 3 button.
 * Pick a variant with app:animButtonVariant = tonal | filled | outlined | text | destructive
 * (or use the matching Widget.Zalith.Button.* style). Default is tonal.
 */
open class AnimButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.buttonStyle
) : AppCompatButton(context, attrs, defStyleAttr) {

    init {
        isAllCaps = false
        val a = context.obtainStyledAttributes(attrs, R.styleable.AnimButton, defStyleAttr, 0)
        val variant = a.getInt(R.styleable.AnimButton_animButtonVariant, VARIANT_TONAL)
        a.recycle()
        applyVariant(variant)
        stateListAnimator = AnimatorInflater.loadStateListAnimator(context, R.xml.anim_scale)
        translationZ = 0f
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        post {
            pivotX = width / 2f
            pivotY = height / 2f
        }
    }

    private fun applyVariant(variant: Int) {
        val (bg, text) = when (variant) {
            VARIANT_FILLED -> R.drawable.md_btn_filled to R.color.md_btn_text_filled
            VARIANT_OUTLINED -> R.drawable.md_btn_outlined to R.color.md_btn_text_outlined
            VARIANT_TEXT -> R.drawable.md_btn_text to R.color.md_btn_text_text
            VARIANT_DESTRUCTIVE -> R.drawable.md_btn_destructive to R.color.md_btn_text_destructive
            else -> R.drawable.md_btn_tonal to R.color.md_btn_text_tonal
        }
        background = ContextCompat.getDrawable(context, bg)
        setTextColor(ContextCompat.getColorStateList(context, text))
    }

    companion object {
        const val VARIANT_TONAL = 0
        const val VARIANT_FILLED = 1
        const val VARIANT_OUTLINED = 2
        const val VARIANT_TEXT = 3
        const val VARIANT_DESTRUCTIVE = 4
    }
}

package com.vn.jet.mosco.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.Gravity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.content.ContextCompat;

import com.vn.jet.mosco.R;

/**
 * Custom Button đa năng chuẩn 'Mosco Design System'.
 * Hỗ trợ đầy đủ các biến thể về Style (Primary, Secondary, Ghost, Destructive)
 * và Size (Large, Medium, Small) theo chỉ thị của Lead.
 */
public class MoscoButton extends AppCompatButton {

    // Constants cho Style
    public static final int STYLE_PRIMARY = 0;
    public static final int STYLE_SECONDARY = 1;
    public static final int STYLE_GHOST = 2;
    public static final int STYLE_DESTRUCTIVE = 3;
    public static final int STYLE_WARNING = 4;
    
    // Constants cho Size
    public static final int SIZE_LARGE = 0;
    public static final int SIZE_MEDIUM = 1;
    public static final int SIZE_SMALL = 2;

    private static final long DEBOUNCE_TIME = 600L;
    private long lastClickTime = 0;

    private int currentStyle = STYLE_PRIMARY;
    private int currentSize = SIZE_LARGE;

    public void setMoscoStyle(int style) {
        this.currentStyle = style;
        applyStyleAndSize();
    }

    public void setMoscoStyle(String styleName) {
        if ("primary".equalsIgnoreCase(styleName)) setMoscoStyle(STYLE_PRIMARY);
        else if ("secondary".equalsIgnoreCase(styleName)) setMoscoStyle(STYLE_SECONDARY);
        else if ("ghost".equalsIgnoreCase(styleName)) setMoscoStyle(STYLE_GHOST);
        else if ("destructive".equalsIgnoreCase(styleName)) setMoscoStyle(STYLE_DESTRUCTIVE);
        else if ("warning".equalsIgnoreCase(styleName)) setMoscoStyle(STYLE_WARNING);
    }

    public void setMoscoSize(int size) {
        this.currentSize = size;
        applyStyleAndSize();
    }

    public void setMoscoSize(String sizeName) {
        if ("large".equalsIgnoreCase(sizeName)) setMoscoSize(SIZE_LARGE);
        else if ("medium".equalsIgnoreCase(sizeName)) setMoscoSize(SIZE_MEDIUM);
        else if ("small".equalsIgnoreCase(sizeName)) setMoscoSize(SIZE_SMALL);
    }

    public MoscoButton(@NonNull Context context) {
        super(context);
        init(null);
    }

    public MoscoButton(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public MoscoButton(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    private void init(@Nullable AttributeSet attrs) {
        if (attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.MoscoButton);
            currentStyle = a.getInt(R.styleable.MoscoButton_moscoStyle, STYLE_PRIMARY);
            currentSize = a.getInt(R.styleable.MoscoButton_moscoSize, SIZE_LARGE);
            a.recycle();
        }

        applyStyleAndSize();
        setGravity(Gravity.CENTER);
        setAllCaps(false);
        setTypeface(getTypeface(), android.graphics.Typeface.BOLD);
        
        // Apply Liquid Glass Interaction Animator globally
        android.animation.StateListAnimator animator = android.animation.AnimatorInflater.loadStateListAnimator(getContext(), R.animator.lg_btn_state_animator);
        setStateListAnimator(animator);
    }

    private void applyStyleAndSize() {
        int height;
        float textSize;
        int paddingSide;
        
        switch (currentSize) {
            case SIZE_SMALL:
                height = getResources().getDimensionPixelSize(R.dimen.spacing_32dp);
                textSize = 13f;
                paddingSide = getResources().getDimensionPixelSize(R.dimen.spacing_12dp);
                break;
            case SIZE_MEDIUM:
                height = getResources().getDimensionPixelSize(R.dimen.spacing_48dp);
                textSize = 15f;
                paddingSide = getResources().getDimensionPixelSize(R.dimen.spacing_16dp);
                break;
            case SIZE_LARGE:
            default:
                height = getResources().getDimensionPixelSize(R.dimen.spacing_56dp);
                textSize = 16f;
                paddingSide = getResources().getDimensionPixelSize(R.dimen.spacing_24dp);
                break;
        }

        setHeight(height);
        setMinimumHeight(height);
        setTextSize(textSize);
        setPadding(paddingSide, 0, paddingSide, 0);

        switch (currentStyle) {
            case STYLE_SECONDARY:
                setBackgroundResource(R.drawable.lg_btn_secondary);
                setTextColor(ContextCompat.getColor(getContext(), R.color.lg_text_primary));
                break;
            case STYLE_GHOST:
                setBackgroundResource(R.drawable.lg_btn_ghost);
                setTextColor(ContextCompat.getColor(getContext(), R.color.lg_text_secondary));
                break;
            case STYLE_DESTRUCTIVE:
                setBackgroundResource(R.drawable.bg_btn_destructive); // Keep as fallback if used
                setTextColor(Color.WHITE);
                break;
            case STYLE_WARNING:
                setBackgroundResource(R.drawable.lg_btn_warning);
                setTextColor(ContextCompat.getColor(getContext(), R.color.lg_text_primary));
                break;
            case STYLE_PRIMARY:
            default:
                setBackgroundResource(R.drawable.lg_btn_primary);
                setTextColor(ContextCompat.getColor(getContext(), R.color.lg_text_primary));
                break;
        }
    }

    @Override
    public void setOnClickListener(@Nullable OnClickListener l) {
        if (l == null) {
            super.setOnClickListener(null);
            return;
        }
        super.setOnClickListener(v -> {
            long currentTime = SystemClock.elapsedRealtime();
            if (currentTime - lastClickTime < DEBOUNCE_TIME) return;
            lastClickTime = currentTime;
            l.onClick(v);
        });
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setAlpha(enabled ? 1.0f : 0.5f);
    }
}


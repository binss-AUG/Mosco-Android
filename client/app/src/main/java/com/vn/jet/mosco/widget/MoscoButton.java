package com.vn.jet.mosco.widget;

import android.content.Context;
import android.content.res.TypedArray;
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
 * Hỗ trợ đầy đủ các biến thể về Style (Primary, Secondary, Ghost, Destructive, Warning),
 * Size (Large, Medium, Small) và Shape (Pill, Square) chuẩn hóa theo yêu cầu.
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

    // Constants cho Shape
    public static final int SHAPE_PILL = 0;
    public static final int SHAPE_SQUARE = 1;

    private static final long DEBOUNCE_TIME = 600L;
    private long lastClickTime = 0;

    private int currentStyle = STYLE_PRIMARY;
    private int currentSize = SIZE_LARGE;
    private int currentShape = SHAPE_PILL;

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

    public void setMoscoShape(int shape) {
        this.currentShape = shape;
        applyStyleAndSize();
    }

    public void setMoscoShape(String shapeName) {
        if ("pill".equalsIgnoreCase(shapeName)) setMoscoShape(SHAPE_PILL);
        else if ("square".equalsIgnoreCase(shapeName)) setMoscoShape(SHAPE_SQUARE);
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
            currentShape = a.getInt(R.styleable.MoscoButton_moscoShape, SHAPE_PILL);
            a.recycle();
        }

        applyStyleAndSize();
        setGravity(Gravity.CENTER);
        setAllCaps(false);
        setSingleLine(true);
        setTypeface(getTypeface(), android.graphics.Typeface.BOLD);
        
        // Sử dụng StateListAnimator toàn cục cho hiệu ứng nâng/đè của thiết kế Liquid Glass
        android.animation.StateListAnimator animator = android.animation.AnimatorInflater.loadStateListAnimator(getContext(), R.animator.lg_btn_state_animator);
        setStateListAnimator(animator);
    }

    private void applyStyleAndSize() {
        int height;
        float textSize;
        int paddingSide;
        
        // Cấu hình kích thước chuẩn dựa trên token dimen được định nghĩa trước
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

        boolean isSquare = currentShape == SHAPE_SQUARE;
        int bgResId;
        int textColorResId;

        // Áp dụng Resource Background và ColorStateList tương ứng cho từng Style và Shape
        switch (currentStyle) {
            case STYLE_SECONDARY:
                bgResId = isSquare ? R.drawable.lg_btn_secondary_square : R.drawable.lg_btn_secondary;
                textColorResId = R.color.colors_button_text_secondary;
                break;
            case STYLE_GHOST:
                bgResId = isSquare ? R.drawable.lg_btn_ghost_square : R.drawable.lg_btn_ghost;
                textColorResId = R.color.colors_button_text_ghost;
                break;
            case STYLE_DESTRUCTIVE:
                bgResId = isSquare ? R.drawable.bg_btn_destructive_square : R.drawable.bg_btn_destructive;
                textColorResId = R.color.colors_button_text_destructive;
                break;
            case STYLE_WARNING:
                bgResId = isSquare ? R.drawable.lg_btn_warning_square : R.drawable.lg_btn_warning;
                textColorResId = R.color.colors_button_text_warning;
                break;
            case STYLE_PRIMARY:
            default:
                bgResId = isSquare ? R.drawable.lg_btn_primary_square : R.drawable.lg_btn_primary;
                textColorResId = R.color.colors_button_text_primary;
                break;
        }

        setBackgroundResource(bgResId);
        setTextColor(ContextCompat.getColorStateList(getContext(), textColorResId));
    }

    @Override
    public void setOnClickListener(@Nullable OnClickListener l) {
        if (l == null) {
            super.setOnClickListener(null);
            return;
        }
        // Xử lý Click Debounce tránh việc spam API gây ra lỗi race condition/double-spending
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
        // Thay đổi độ mờ đục để người dùng dễ nhận biết trạng thái vô hiệu hóa
        setAlpha(enabled ? 1.0f : 0.5f);
    }
}

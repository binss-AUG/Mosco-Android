package com.vn.jet.mosco.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import androidx.appcompat.widget.AppCompatImageView;

public class DraggableFab extends AppCompatImageView implements View.OnTouchListener {
    private float dX, dY;
    private long downTime;
    private static final int CLICK_ACTION_THRESHOLD = 200; // milliseconds
    private static final int CLICK_DRAG_TOLERANCE = 10; // pixels

    private float startX, startY;

    public DraggableFab(Context context) {
        super(context);
        init();
    }

    public DraggableFab(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DraggableFab(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setOnTouchListener(this);
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dX = view.getX() - event.getRawX();
                dY = view.getY() - event.getRawY();
                startX = event.getRawX();
                startY = event.getRawY();
                downTime = System.currentTimeMillis();
                view.animate().scaleX(1.1f).scaleY(1.1f).setDuration(100).start();
                return true;

            case MotionEvent.ACTION_MOVE:
                float newX = event.getRawX() + dX;
                float newY = event.getRawY() + dY;
                
                // Boundaries
                View parent = (View) view.getParent();
                if (parent != null) {
                    newX = Math.max(0, Math.min(newX, parent.getWidth() - view.getWidth()));
                    newY = Math.max(0, Math.min(newY, parent.getHeight() - view.getHeight()));
                }
                
                view.animate()
                    .x(newX)
                    .y(newY)
                    .setDuration(0)
                    .start();
                return true;

            case MotionEvent.ACTION_UP:
                view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
                long clickDuration = System.currentTimeMillis() - downTime;
                float dragDistanceX = Math.abs(event.getRawX() - startX);
                float dragDistanceY = Math.abs(event.getRawY() - startY);
                
                if (clickDuration < CLICK_ACTION_THRESHOLD && dragDistanceX < CLICK_DRAG_TOLERANCE && dragDistanceY < CLICK_DRAG_TOLERANCE) {
                    performClick();
                } else {
                    // Snap to edge
                    View p = (View) view.getParent();
                    if (p != null) {
                        float finalX = (view.getX() + view.getWidth() / 2f < p.getWidth() / 2f) ? 0 : p.getWidth() - view.getWidth();
                        view.animate().x(finalX).setDuration(250).start();
                    }
                }
                return true;

            default:
                return super.onTouchEvent(event);
        }
    }
}

package com.vn.jet.mosco.utils;

import android.view.MotionEvent;

public class RotationGestureDetector {
    private static final int INVALID_POINTER_ID = -1;
    private float fX, fY, sX, sY;
    private int ptrID1, ptrID2;
    private float angle;
    private boolean inProgress;

    private OnRotationGestureListener listener;

    public RotationGestureDetector(OnRotationGestureListener listener) {
        this.listener = listener;
        ptrID1 = INVALID_POINTER_ID;
        ptrID2 = INVALID_POINTER_ID;
    }

    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                ptrID1 = event.getPointerId(event.getActionIndex());
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                ptrID2 = event.getPointerId(event.getActionIndex());
                sX = event.getX(event.findPointerIndex(ptrID1));
                sY = event.getY(event.findPointerIndex(ptrID1));
                fX = event.getX(event.findPointerIndex(ptrID2));
                fY = event.getY(event.findPointerIndex(ptrID2));
                inProgress = true;
                break;
            case MotionEvent.ACTION_MOVE:
                if (ptrID1 != INVALID_POINTER_ID && ptrID2 != INVALID_POINTER_ID) {
                    float nsX = event.getX(event.findPointerIndex(ptrID1));
                    float nsY = event.getY(event.findPointerIndex(ptrID1));
                    float nfX = event.getX(event.findPointerIndex(ptrID2));
                    float nfY = event.getY(event.findPointerIndex(ptrID2));

                    angle = angleBetweenLines(fX, fY, sX, sY, nfX, nfY, nsX, nsY);

                    if (listener != null) {
                        listener.onRotation(this);
                    }
                    
                    // Cập nhật lại các điểm gốc để tính góc chênh lệch (delta) cho frame tiếp theo
                    fX = nfX;
                    fY = nfY;
                    sX = nsX;
                    sY = nsY;
                }
                break;
            case MotionEvent.ACTION_UP:
                ptrID1 = INVALID_POINTER_ID;
                inProgress = false;
                break;
            case MotionEvent.ACTION_POINTER_UP:
                ptrID2 = INVALID_POINTER_ID;
                inProgress = false;
                break;
            case MotionEvent.ACTION_CANCEL:
                ptrID1 = INVALID_POINTER_ID;
                ptrID2 = INVALID_POINTER_ID;
                inProgress = false;
                break;
        }
        return true;
    }

    private float angleBetweenLines(float fX, float fY, float sX, float sY, float nfX, float nfY, float nsX, float nsY) {
        float angle1 = (float) Math.atan2((fY - sY), (fX - sX));
        float angle2 = (float) Math.atan2((nfY - nsY), (nfX - nsX));
        float angle = ((float) Math.toDegrees(angle2 - angle1)) % 360;
        if (angle < -180.f) angle += 360.0f;
        if (angle > 180.f) angle -= 360.0f;
        return angle;
    }

    public float getAngle() {
        return angle;
    }

    public boolean isInProgress() {
        return inProgress;
    }

    public interface OnRotationGestureListener {
        void onRotation(RotationGestureDetector rotationDetector);
    }
}

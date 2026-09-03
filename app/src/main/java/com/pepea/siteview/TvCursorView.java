package com.pepea.siteview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

final class TvCursorView extends View {
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bevelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path pointerPath = new Path();
    private final Path bevelPath = new Path();
    private boolean pressed;
    private final Runnable releasePulse = () -> {
        pressed = false;
        invalidate();
    };

    TvCursorView(Context context) {
        super(context);
        setFocusable(false);
        setClickable(false);

        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(Color.rgb(235, 242, 249));

        edgePaint.setStyle(Paint.Style.STROKE);
        edgePaint.setStrokeJoin(Paint.Join.ROUND);
        edgePaint.setStrokeCap(Paint.Cap.ROUND);
        edgePaint.setStrokeWidth(dp(5));
        edgePaint.setColor(Color.rgb(90, 108, 130));

        bevelPaint.setStyle(Paint.Style.STROKE);
        bevelPaint.setStrokeJoin(Paint.Join.ROUND);
        bevelPaint.setStrokeCap(Paint.Cap.ROUND);
        bevelPaint.setStrokeWidth(dp(2));
        bevelPaint.setColor(Color.rgb(255, 126, 24));
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        pointerPath.reset();
        pointerPath.moveTo(dp(14), dp(8));
        pointerPath.lineTo(dp(14), dp(43));
        pointerPath.lineTo(dp(23), dp(35));
        pointerPath.lineTo(dp(31), dp(49));
        pointerPath.lineTo(dp(40), dp(44));
        pointerPath.lineTo(dp(32), dp(31));
        pointerPath.lineTo(dp(45), dp(29));
        pointerPath.close();

        bevelPath.reset();
        bevelPath.moveTo(dp(18), dp(17));
        bevelPath.lineTo(dp(18), dp(35));
        bevelPath.lineTo(dp(24), dp(29));
        bevelPath.lineTo(dp(34), dp(43));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        fillPaint.setColor(pressed
                ? Color.rgb(255, 126, 24)
                : Color.rgb(235, 242, 249));
        bevelPaint.setColor(pressed
                ? Color.rgb(255, 245, 236)
                : Color.rgb(255, 126, 24));

        canvas.drawPath(pointerPath, edgePaint);
        canvas.drawPath(pointerPath, fillPaint);
        canvas.drawPath(pointerPath, bevelPaint);
        canvas.drawPath(bevelPath, bevelPaint);
    }

    void setCursorPosition(float x, float y) {
        setTranslationX(x - dp(14));
        setTranslationY(y - dp(8));
    }

    void pulse() {
        removeCallbacks(releasePulse);
        pressed = true;
        invalidate();
        postDelayed(releasePulse, 140);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}

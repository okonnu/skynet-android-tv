package com.pepea.siteview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

final class TvCursorView extends View {
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path pointerPath = new Path();

    TvCursorView(Context context) {
        super(context);
        setFocusable(false);
        setClickable(false);

        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(Color.rgb(20, 20, 20));

        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeJoin(Paint.Join.ROUND);
        outlinePaint.setStrokeCap(Paint.Cap.ROUND);
        outlinePaint.setStrokeWidth(dp(1.25f));
        outlinePaint.setColor(Color.WHITE);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        pointerPath.reset();
        pointerPath.moveTo(dp(6), dp(4));
        pointerPath.lineTo(dp(6), dp(37));
        pointerPath.lineTo(dp(15), dp(28));
        pointerPath.lineTo(dp(24), dp(43));
        pointerPath.lineTo(dp(31), dp(39));
        pointerPath.lineTo(dp(22), dp(24));
        pointerPath.lineTo(dp(37), dp(24));
        pointerPath.close();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawPath(pointerPath, outlinePaint);
        canvas.drawPath(pointerPath, fillPaint);
    }

    void setCursorPosition(float x, float y) {
        setTranslationX(x - dp(6));
        setTranslationY(y - dp(4));
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}

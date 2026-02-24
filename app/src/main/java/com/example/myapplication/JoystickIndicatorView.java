package com.example.myapplication;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class JoystickIndicatorView extends View {

    private static final int MAX_VALUE = 450;

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint crossPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int xValue = 0; // -450 ~ 450, 右正左负
    private int yValue = 0; // -450 ~ 450, 上正下负

    public JoystickIndicatorView(Context context) {
        super(context);
        init();
    }

    public JoystickIndicatorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        // 80% 不透明白色圆形背景
        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setColor(Color.argb(204, 255, 255, 255));

        // 箭头文字（深色）
        arrowPaint.setStyle(Paint.Style.FILL);
        arrowPaint.setColor(Color.argb(160, 30, 30, 30));
        arrowPaint.setTextAlign(Paint.Align.CENTER);

        // 移动指示点（橙色实心）
        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(Color.argb(230, 255, 120, 0));
    }

    /** 外部调用：更新摇杆值，x 左右，y 上下（上正） */
    public void setValues(int x, int y) {
        xValue = clamp(x, -MAX_VALUE, MAX_VALUE);
        yValue = clamp(y, -MAX_VALUE, MAX_VALUE);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float w = getWidth();
        float h = getHeight();
        float cx = w / 2f;
        float cy = h / 2f;
        float radius = Math.min(w, h) / 2f * 0.88f;

        // ① 半透明白色圆形背景
        canvas.drawCircle(cx, cy, radius, bgPaint);

        // ② 十字辅助线
        // canvas.drawLine(cx, cy - radius * 0.75f, cx, cy + radius * 0.75f, crossPaint);
        // canvas.drawLine(cx - radius * 0.75f, cy, cx + radius * 0.75f, cy, crossPaint);

        // ③ 四向箭头（放在圆周内侧）
        float arrowSize = radius * 0.38f;
        arrowPaint.setTextSize(arrowSize);
        arrowPaint.setColor(Color.WHITE);
        float offset = radius * 0.62f;
        // 竖排文字基线偏移修正
        float baselineAdj = arrowSize * 0.3f;
        canvas.drawText("^", cx, cy - offset + baselineAdj, arrowPaint);
        canvas.drawText("v", cx, cy + offset + baselineAdj, arrowPaint);
        canvas.drawText("<", cx - offset, cy + baselineAdj, arrowPaint);
        canvas.drawText(">", cx + offset, cy + baselineAdj, arrowPaint);

        // ④ 移动指示点
        float maxOffset = radius * 0.52f;
        float dotX = cx + (xValue / (float) MAX_VALUE) * maxOffset;
        float dotY = cy - (yValue / (float) MAX_VALUE) * maxOffset; // y 上正，canvas 向下为正
        float dotRadius = radius * 0.17f;
        canvas.drawCircle(dotX, dotY, dotRadius, dotPaint);
    }

    private static int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }
}

package com.example.myapplication;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

public class AngleGaugeView extends View {

    private final Paint labelPaint;
    private final Paint valuePaint;

    private float angle = 0f;
    private String label = "BOOM:";

    public AngleGaugeView(Context context) {
        this(context, null);
    }

    public AngleGaugeView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AngleGaugeView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(Color.parseColor("#AAAAAA"));
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTypeface(Typeface.DEFAULT_BOLD);

        valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        valuePaint.setColor(Color.WHITE);
        valuePaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setTypeface(Typeface.DEFAULT_BOLD);
    }

    public void setAngle(float angle) {
        this.angle = angle;
        invalidate();
    }

    public void setLabel(String label) {
        this.label = label;
        invalidate();
    }

    /** 保留接口兼容性，颜色不再使用 */
    public void setArcColor(int color) { }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        float cx = w / 2f;

        labelPaint.setTextSize(h * 0.20f);
        canvas.drawText(label, cx, h * 0.38f, labelPaint);

        String sign = angle >= 0 ? "+" : "";
        valuePaint.setTextSize(h * 0.36f);
        canvas.drawText(sign + String.format("%.1f°", angle), cx, h * 0.78f, valuePaint);
    }
}

package com.example.myapplication;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

/**
 * 角度仪表盘视图 - 显示圆弧进度和角度值
 * 用于展示 BOOM / STICK / BUCKET 的角度信息
 */
public class AngleGaugeView extends View {

    private Paint bgPaint;
    private Paint trackPaint;
    private Paint arcPaint;
    private Paint textPaint;
    private Paint labelPaint;
    private Paint arrowPaint;

    private float angle = 0f;
    private String label = "BOOM:";
    private int arcColor = Color.parseColor("#FFD700");

    // 圆弧参数：从底部左侧 (135°) 开始，顺时针扫过 270°
    private static final float START_ANGLE = 135f;
    private static final float TOTAL_SWEEP = 270f;

    // 角度映射范围
    private static final float MIN_ANGLE = -90f;
    private static final float MAX_ANGLE = 90f;

    public AngleGaugeView(Context context) {
        super(context);
        init();
    }

    public AngleGaugeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AngleGaugeView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setStyle(Paint.Style.FILL);

        trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trackPaint.setColor(Color.parseColor("#3A3A3A"));
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);

        arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        arcPaint.setColor(arcColor);
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);

        labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(Color.parseColor("#AAAAAA"));
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTypeface(Typeface.DEFAULT_BOLD);

        arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        arrowPaint.setColor(arcColor);
        arrowPaint.setStyle(Paint.Style.STROKE);
        arrowPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    /**
     * 设置当前角度值
     */
    public void setAngle(float angle) {
        this.angle = angle;
        invalidate();
    }

    /**
     * 设置标签文字（如 "BOOM:", "STICK:", "BUCKET:"）
     */
    public void setLabel(String label) {
        this.label = label;
        invalidate();
    }

    /**
     * 设置圆弧颜色
     */
    public void setArcColor(int color) {
        this.arcColor = color;
        arcPaint.setColor(color);
        arrowPaint.setColor(color);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        float size = Math.min(w, h);
        float cx = w / 2f;
        float cy = h * 0.44f;
        float radius = size * 0.30f;
        float strokeWidth = size * 0.055f;

        trackPaint.setStrokeWidth(strokeWidth);
        arcPaint.setStrokeWidth(strokeWidth);

        // 绘制背景圆
        bgPaint.setColor(Color.parseColor("#252525"));
        canvas.drawCircle(cx, cy, radius + strokeWidth * 1.3f, bgPaint);

        // 绘制轨道弧线（完整 270°）
        RectF arcRect = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
        canvas.drawArc(arcRect, START_ANGLE, TOTAL_SWEEP, false, trackPaint);

        // 计算并绘制数值弧线
        float clampedAngle = Math.max(MIN_ANGLE, Math.min(MAX_ANGLE, angle));
        float normalized = (clampedAngle - MIN_ANGLE) / (MAX_ANGLE - MIN_ANGLE);
        float sweepAngle = normalized * TOTAL_SWEEP;
        if (sweepAngle > 0.5f) {
            canvas.drawArc(arcRect, START_ANGLE, sweepAngle, false, arcPaint);
        }

        // 绘制标签文字
        labelPaint.setTextSize(size * 0.10f);
        canvas.drawText(label, cx, cy - size * 0.02f, labelPaint);

        // 绘制角度值
        textPaint.setTextSize(size * 0.14f);
        String valueText = String.format("%.2f°", angle);
        canvas.drawText(valueText, cx, cy + size * 0.13f, textPaint);

        // 绘制旋转箭头指示器
        drawRotationArrow(canvas, cx, cy, radius, size);
    }

    /**
     * 绘制小旋转箭头图标
     */
    private void drawRotationArrow(Canvas canvas, float cx, float cy, float radius, float size) {
        float arrowRadius = size * 0.05f;
        float arrowCenterX = cx + radius * 0.4f;
        float arrowCenterY = cy + radius * 0.75f;

        arrowPaint.setStrokeWidth(size * 0.016f);

        // 画小弧线
        RectF arrowRect = new RectF(
            arrowCenterX - arrowRadius, arrowCenterY - arrowRadius,
            arrowCenterX + arrowRadius, arrowCenterY + arrowRadius
        );
        canvas.drawArc(arrowRect, 200f, 200f, false, arrowPaint);

        // 画箭头尖端
        float arrowTipAngle = (float) Math.toRadians(200f + 200f);
        float tipX = arrowCenterX + (float)(Math.cos(arrowTipAngle) * arrowRadius);
        float tipY = arrowCenterY + (float)(Math.sin(arrowTipAngle) * arrowRadius);
        float arrowHeadLen = size * 0.022f;

        Path arrowHead = new Path();
        arrowHead.moveTo(tipX, tipY);
        arrowHead.lineTo(tipX + arrowHeadLen, tipY + arrowHeadLen * 0.5f);
        arrowHead.moveTo(tipX, tipY);
        arrowHead.lineTo(tipX + arrowHeadLen * 0.3f, tipY - arrowHeadLen * 0.8f);
        canvas.drawPath(arrowHead, arrowPaint);
    }
}

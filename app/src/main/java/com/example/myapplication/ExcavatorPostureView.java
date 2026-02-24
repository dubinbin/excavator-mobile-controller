package com.example.myapplication;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

public class ExcavatorPostureView extends View {
    private Paint paint;
    private Paint borderPaint;
    
    // 颜色定义（卡通风格，实体填充）
    private static final int YELLOW_MAIN = Color.parseColor("#FFEB3B");      // 亮黄色
    private static final int BLACK = Color.parseColor("#000000");           // 黑色边框
    
    private float boomAngle = 0f;  // 初始角度0度
    private float stickAngle = 0f;  // 初始角度0度
    private float bucketAngle = 0f;  // 初始角度0度
    
    private float boomLength = 0.35f;  //大臂
    private float stickLength = 0.2f;
    private float bucketLength = 0.18f;
    
    public ExcavatorPostureView(Context context) {
        super(context);
        init();
    }
    
    public ExcavatorPostureView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    private void init() {
        // 填充画笔
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);
        
        // 边框画笔（粗黑线）
        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setColor(BLACK);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(4f);
        borderPaint.setStrokeJoin(Paint.Join.ROUND);
        borderPaint.setStrokeCap(Paint.Cap.ROUND);
    }
    
    public void setAngles(float boom, float stick, float bucket) {
        this.boomAngle = boom;
        this.stickAngle = stick;
        this.bucketAngle = bucket;
        invalidate();
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        int width = getWidth();
        int height = getHeight();
        
        // 整体图像居中显示，放大比例
        float centerX = width * 0.5f;  // 水平居中
        float centerY = height * 0.65f;  // 垂直偏上
        float scale = Math.min(width, height) * 1.15f;  // 放大显示
        
        canvas.save();
        
        // 绘制履带和底盘
        drawTracksCartoon(canvas, centerX, centerY, scale);
        
        // 绘制主车身
        drawMainBodyCartoon(canvas, centerX, centerY - scale * 0.05f, scale);
        
        // 绘制驾驶室
        drawCabCartoon(canvas, centerX, centerY - scale * 0.05f, scale);
        
        // 绘制大臂（0度垂直向上，正值继续向上，负值向下）
        // 由于机械臂在车身左边，需要加90度偏移使0度时垂直向上
        float boomAngleTotal = - boomAngle - 180f -25f;  // 0度时垂直向上
        float boomEndX = centerX + (float) (Math.cos(Math.toRadians(boomAngleTotal)) * boomLength * scale);
        float boomEndY = centerY - scale * 0.05f - (float) (Math.sin(Math.toRadians(boomAngleTotal)) * boomLength * scale);
        drawBoomCartoon(canvas, centerX, centerY - scale * 0.05f, boomEndX, boomEndY, scale);
        
        // 绘制小臂（0度=向上，负值=逆时针向左转，正值=顺时针向右转，±180度=向下）
        // 转换为Canvas坐标系：用户的0度（向上）对应Canvas的-90度
        float stickAngleTotal = stickAngle + 90f;
        float stickStartX = boomEndX;
        float stickStartY = boomEndY;
        float stickEndX = stickStartX + (float) (Math.cos(Math.toRadians(stickAngleTotal)) * stickLength * scale);
        float stickEndY = stickStartY - (float) (Math.sin(Math.toRadians(stickAngleTotal)) * stickLength * scale);
        drawStickCartoon(canvas, stickStartX, stickStartY, stickEndX, stickEndY, scale);
        
        // 绘制铲斗（IMU角度系统：0度=向上，负值=逆时针向左转，正值=顺时针向右转，±180度=向下）
        float offsetAngle = 90f;
        float bucketAngleTotal = bucketAngle+offsetAngle;
        float bucketStartX = stickEndX;
        float bucketStartY = stickEndY;
        drawBucketCartoon(canvas, bucketStartX, bucketStartY, bucketAngleTotal, scale);
        
        // 绘制液压缸（简单直线）
        drawHydraulicLines(canvas, centerX, centerY - scale * 0.05f, 
                          boomEndX, boomEndY, stickEndX, stickEndY, scale);
        
        canvas.restore();
    }
    
    private void drawTracksCartoon(Canvas canvas, float x, float y, float scale) {
        // 履带主体（矩形，实体填充）
        float trackWidth = scale * 0.3f;
        float trackHeight = scale * 0.12f;
        
        Path trackPath = new Path();
        trackPath.addRect(
            x - trackWidth/2, y - trackHeight/2,
            x + trackWidth/2, y + trackHeight/2,
            Path.Direction.CW
        );
        
        // 填充履带（深灰色）
        paint.setColor(Color.parseColor("#333333"));
        canvas.drawPath(trackPath, paint);
        
        // 履带边框
        canvas.drawPath(trackPath, borderPaint);
        
        // 履带中间的水平线
        paint.setColor(BLACK);
        paint.setStrokeWidth(3f);
        canvas.drawLine(x - trackWidth/2, y, x + trackWidth/2, y, paint);
        
        // 三个圆形轮子（等间距，实体填充）
        paint.setColor(Color.parseColor("#444444"));
        float wheelRadius = scale * 0.04f;
        float wheelSpacing = trackWidth / 4;
        for (int i = 0; i < 3; i++) {
            float wheelX = x - trackWidth/4 + i * wheelSpacing;
            canvas.drawCircle(wheelX, y, wheelRadius, paint);
            // 轮子边框
            borderPaint.setStrokeWidth(3f);
            canvas.drawCircle(wheelX, y, wheelRadius, borderPaint);
        }
    }
    
    private void drawMainBodyCartoon(Canvas canvas, float x, float y, float scale) {
        // 主车身（梯形，底部宽，顶部窄，实体填充）
        float bodyWidthBottom = scale * 0.24f;
        float bodyWidthTop = scale * 0.2f;
        float bodyHeight = scale * 0.14f;
        
        Path bodyPath = new Path();
        bodyPath.moveTo(x - bodyWidthBottom/2, y);
        bodyPath.lineTo(x + bodyWidthBottom/2, y);
        bodyPath.lineTo(x + bodyWidthTop/2, y - bodyHeight);
        bodyPath.lineTo(x - bodyWidthTop/2, y - bodyHeight);
        bodyPath.close();
        
        // 填充（黄色）
        paint.setColor(YELLOW_MAIN);
        canvas.drawPath(bodyPath, paint);
        
        // 边框
        borderPaint.setStrokeWidth(4f);
        canvas.drawPath(bodyPath, borderPaint);
        
        // 后部（发动机舱/配重，实体填充）
        float rearWidth = scale * 0.12f;
        float rearHeight = scale * 0.1f;
        Path rearPath = new Path();
        rearPath.addRect(
            x + bodyWidthTop/2, y - bodyHeight,
            x + bodyWidthTop/2 + rearWidth, y - bodyHeight + rearHeight,
            Path.Direction.CW
        );
        
        paint.setColor(YELLOW_MAIN);
        canvas.drawPath(rearPath, paint);
        canvas.drawPath(rearPath, borderPaint);
    }
    
    private void drawCabCartoon(Canvas canvas, float x, float y, float scale) {
        // 驾驶室（圆角矩形，实体填充）
        float cabWidth = scale * 0.16f;
        float cabHeight = scale * 0.14f;
        float cabX = x - scale * 0.06f;  // 稍微偏左
        float cabY = y - scale * 0.08f;
        
        Path cabPath = new Path();
        cabPath.addRoundRect(
            cabX - cabWidth/2, cabY - cabHeight,
            cabX + cabWidth/2, cabY,
            6, 6, Path.Direction.CW
        );
        
        // 填充（黄色）
        paint.setColor(YELLOW_MAIN);
        canvas.drawPath(cabPath, paint);
        
        // 边框
        borderPaint.setStrokeWidth(4f);
        canvas.drawPath(cabPath, borderPaint);
        
        // 窗户（小矩形，深色填充）
        float windowWidth = cabWidth * 0.6f;
        float windowHeight = cabHeight * 0.5f;
        Path windowPath = new Path();
        windowPath.addRect(
            cabX - windowWidth/2, cabY - cabHeight * 0.65f,
            cabX + windowWidth/2, cabY - cabHeight * 0.65f + windowHeight,
            Path.Direction.CW
        );
        
        // 窗户填充（深色）
        paint.setColor(Color.parseColor("#1A1A1A"));
        canvas.drawPath(windowPath, paint);
        
        // 窗户边框
        borderPaint.setStrokeWidth(3f);
        canvas.drawPath(windowPath, borderPaint);
    }

    private void drawBoomCartoon(Canvas canvas, float startX, float startY, float endX, float endY, float scale) {
        // 大臂（折线，转折点在中点，向上折线）
        float baseWidth = scale * 0.08f;
        float tipWidth = scale * 0.06f;

        // 计算大臂的总长度和方向
        float dx = endX - startX;
        float dy = endY - startY;
        double totalAngle = Math.atan2(-dy, dx);

        // 转折点位置：在起点到终点的中点（50%处）
        float turnPointRatio = 0.5f;  // 转折点距离起点的比例，放在中点
        float turnPointX = startX + dx * turnPointRatio;
        float turnPointY = startY + dy * turnPointRatio;

        // 转折点向上偏移，形成明显的折线效果
        // 偏移方向：垂直于大臂方向，向上（相对于大臂方向）
        float perpAngle = (float)(totalAngle + Math.PI/2);  // 垂直于大臂的方向
        float turnOffset = scale * 0.08f;  // 偏移量，让折线更明显
        // 向下偏移：
        turnPointX -= (float)(Math.cos(perpAngle) * turnOffset);
        turnPointY += (float)(Math.sin(perpAngle) * turnOffset);

        // 计算起点到转折点的角度（转折后的实际角度）
        float dx1 = turnPointX - startX;
        float dy1 = turnPointY - startY;
        double angle1 = Math.atan2(-dy1, dx1);
        float perpAngle1 = (float)(angle1 + Math.PI/2);
        float basePerpX = (float)(Math.cos(perpAngle1) * baseWidth/2);
        float basePerpY = (float)(-Math.sin(perpAngle1) * baseWidth/2);

        // 计算转折点到终点的角度（转折后的实际角度）
        float dx2 = endX - turnPointX;
        float dy2 = endY - turnPointY;
        double angle2 = Math.atan2(-dy2, dx2);
        float perpAngle2 = (float)(angle2 + Math.PI/2);
        float midPerpX = (float)(Math.cos(perpAngle2) * (baseWidth + tipWidth)/4);
        float midPerpY = (float)(-Math.sin(perpAngle2) * (baseWidth + tipWidth)/4);
        float tipPerpX = (float)(Math.cos(perpAngle2) * tipWidth/2);
        float tipPerpY = (float)(-Math.sin(perpAngle2) * tipWidth/2);

        // 构建平滑折线路径（使用曲线连接转折处）
        Path boomPath = new Path();
        // 起点左侧
        boomPath.moveTo(startX + basePerpX, startY + basePerpY);

        // 到转折点左侧（使用二次贝塞尔曲线平滑连接）
        float smoothRadius = scale * 0.025f;  // 增大平滑半径，让过渡更圆滑
        float smoothDist = scale * 0.03f;  // 控制点距离转折点的距离，让转折处更平滑
        float controlX1 = turnPointX + midPerpX - (float)(Math.cos(angle1) * smoothDist);
        float controlY1 = turnPointY + midPerpY - (float)(-Math.sin(angle1) * smoothDist);
        boomPath.quadTo(controlX1, controlY1, turnPointX + midPerpX, turnPointY + midPerpY);

        // 从转折点到终点左侧（使用二次贝塞尔曲线平滑连接）
        float controlX2 = turnPointX + midPerpX + (float)(Math.cos(angle2) * smoothDist);
        float controlY2 = turnPointY + midPerpY + (float)(-Math.sin(angle2) * smoothDist);
        boomPath.quadTo(controlX2, controlY2, endX + tipPerpX, endY + tipPerpY);

        // 到终点右侧（使用曲线）
        float controlX3 = endX - tipPerpX + (float)(Math.cos(angle2) * smoothRadius);
        float controlY3 = endY - tipPerpY + (float)(-Math.sin(angle2) * smoothRadius);
        boomPath.quadTo(controlX3, controlY3, endX - tipPerpX, endY - tipPerpY);

        // 从终点回到转折点右侧（使用曲线）
        float controlX4 = turnPointX - midPerpX + (float)(Math.cos(angle2) * smoothDist);
        float controlY4 = turnPointY - midPerpY + (float)(-Math.sin(angle2) * smoothDist);
        boomPath.quadTo(controlX4, controlY4, turnPointX - midPerpX, turnPointY - midPerpY);

        // 从转折点回到起点右侧（使用曲线）
        float controlX5 = turnPointX - midPerpX - (float)(Math.cos(angle1) * smoothDist);
        float controlY5 = turnPointY - midPerpY - (float)(-Math.sin(angle1) * smoothDist);
        boomPath.quadTo(controlX5, controlY5, startX - basePerpX, startY - basePerpY);

        boomPath.close();

        // 填充（黄色）
        paint.setColor(YELLOW_MAIN);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawPath(boomPath, paint);

        // 边框
        borderPaint.setStrokeWidth(4f);
        borderPaint.setStyle(Paint.Style.STROKE);
        canvas.drawPath(boomPath, borderPaint);

        // 连接点（小圆形，灰色填充）
        float jointRadius = baseWidth * 0.7f;
        paint.setColor(Color.parseColor("#888888"));
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(startX, startY, jointRadius, paint);
        borderPaint.setStrokeWidth(3f);
        borderPaint.setStyle(Paint.Style.STROKE);
        canvas.drawCircle(startX, startY, jointRadius, borderPaint);

        // 末端连接点
        float tipRadius = tipWidth * 0.7f;
        canvas.drawCircle(endX, endY, tipRadius, paint);
        canvas.drawCircle(endX, endY, tipRadius, borderPaint);
    }
    
    private void drawStickCartoon(Canvas canvas, float startX, float startY, float endX, float endY, float scale) {
        // 小臂（梯形，比大臂窄，实体填充）
        float baseWidth = scale * 0.055f;
        float tipWidth = scale * 0.04f;
        
        double angle = Math.atan2(startY - endY, endX - startX);
        float perpAngle = (float)(angle + Math.PI/2);
        float basePerpX = (float)(Math.cos(perpAngle) * baseWidth/2);
        float basePerpY = (float)(-Math.sin(perpAngle) * baseWidth/2);
        float tipPerpX = (float)(Math.cos(perpAngle) * tipWidth/2);
        float tipPerpY = (float)(-Math.sin(perpAngle) * tipWidth/2);
        
        Path stickPath = new Path();
        stickPath.moveTo(startX + basePerpX, startY + basePerpY);
        stickPath.lineTo(endX + tipPerpX, endY + tipPerpY);
        stickPath.lineTo(endX - tipPerpX, endY - tipPerpY);
        stickPath.lineTo(startX - basePerpX, startY - basePerpY);
        stickPath.close();
        
        // 填充（黄色）
        paint.setColor(YELLOW_MAIN);
        canvas.drawPath(stickPath, paint);
        
        // 边框
        borderPaint.setStrokeWidth(4f);
        canvas.drawPath(stickPath, borderPaint);
        
        // 连接点（灰色填充）
        float jointRadius = baseWidth * 0.7f;
        paint.setColor(Color.parseColor("#888888"));
        canvas.drawCircle(startX, startY, jointRadius, paint);
        borderPaint.setStrokeWidth(3f);
        canvas.drawCircle(startX, startY, jointRadius, borderPaint);
        
        // 末端连接点
        float tipRadius = tipWidth * 0.7f;
        canvas.drawCircle(endX, endY, tipRadius, paint);
        canvas.drawCircle(endX, endY, tipRadius, borderPaint);
    }
    
    private void drawBucketCartoon(Canvas canvas, float startX, float startY, float angle, float scale) {
        // 铲斗：像挖掘机铲斗，前端尖，不那么扁
        float bucketLen = bucketLength * scale;
        float bucketHeight = scale * 0.4f;  // 整体增大一点（从0.35f改为0.4f）
        
        // 保存canvas状态
        canvas.save();
        
        // 移动到连接点并旋转
        canvas.translate(startX, startY);
        canvas.scale(-1f,1f);
        canvas.rotate(angle);
        
        // 在局部坐标系中绘制铲斗（连接点在右侧，前端在左侧）
        // 连接点位置（右侧，原点）
        float connectX = 0;
        float connectY = 0;
        
        // 铲斗顶部宽度（连接板
        float topWidth = bucketHeight * 0.25f;  // 进一步收窄
        
        // 铲斗前端
        float frontX = -bucketLen * 1.1f;  // 更靠左（更靠前），让顶点向前移动
        float frontY = -bucketHeight * 0.2f;  // 前端更向上，增大凸起角度
        
        // 铲斗顶部
        float topMidX = -bucketLen * 0.35f;
        float topMidY = -topWidth * 0.6f;  // 更向上，增大凸起角度
        
        // 铲斗底部
        float bottomMidX = -bucketLen * 0.55f;
        float bottomMidY = bucketHeight * 0.55f;  // 底部更深，形成更明显的凹形
        
        // 构建路径：从连接点开始，形成尖的铲斗形状
        Path bucketPath = new Path();
        // 从连接点本身开始，一条完整的曲线到前端
        bucketPath.moveTo(connectX, 0);
        // 从连接点开始，一条完整的曲线到前端
        float topCurveX = -bucketLen * 0.5f;  // 让顶点更靠近曲线中间
        float topCurveY = topWidth * 1.8f;     // 让顶部曲线最高点更高
        bucketPath.quadTo(
            topCurveX, topCurveY,
            frontX, frontY
        );
        // 底部曲线
        float bottomCurveX = -bucketLen * 0.3f;  // 控制点位置
        float bottomCurveY = bucketHeight * 0.01f;  // 再平一点，减小弯曲程度
        bucketPath.quadTo(
            bottomCurveX, bottomCurveY,
            connectX, -topWidth / 2f
        );
        bucketPath.close();
        
        // 填充（黄色）
        paint.setColor(YELLOW_MAIN);
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(true);
        canvas.drawPath(bucketPath, paint);
        
        // 边框
        borderPaint.setStrokeWidth(4f);
        borderPaint.setStyle(Paint.Style.STROKE);
        canvas.drawPath(bucketPath, borderPaint);
        
        // 恢复canvas状态
        canvas.restore();
        
        // 连接点（两个圆形，灰色填充）
        float jointRadius = scale * 0.015f;
        paint.setColor(Color.parseColor("#888888"));
        paint.setStyle(Paint.Style.FILL);
        
        // 计算连接点在旋转后的位置
        float jointOffsetX = (float) (Math.cos(Math.toRadians(angle)) * scale * 0.02f);
        float jointOffsetY = (float) (-Math.sin(Math.toRadians(angle)) * scale * 0.02f);
        float jointPerpX = (float) (Math.cos(Math.toRadians(angle + 90)) * scale * 0.01f);
        float jointPerpY = (float) (-Math.sin(Math.toRadians(angle + 90)) * scale * 0.01f);
        
        // 第一个连接点（上方）
        float joint1X = startX + jointOffsetX - jointPerpX;
        float joint1Y = startY + jointOffsetY - jointPerpY;
        canvas.drawCircle(joint1X, joint1Y, jointRadius, paint);
        borderPaint.setStrokeWidth(3f);
        borderPaint.setStyle(Paint.Style.STROKE);
        canvas.drawCircle(joint1X, joint1Y, jointRadius, borderPaint);
        
        // 第二个连接点（下方）
        float joint2X = startX + jointOffsetX + jointPerpX;
        float joint2Y = startY + jointOffsetY + jointPerpY;
        canvas.drawCircle(joint2X, joint2Y, jointRadius, paint);
        canvas.drawCircle(joint2X, joint2Y, jointRadius, borderPaint);
    }
    
    // 绘制液压缸（简单直线）
    private void drawHydraulicLines(Canvas canvas, float bodyX, float bodyY,
                                   float boomEndX, float boomEndY,
                                   float stickEndX, float stickEndY, float scale) {
        paint.setColor(BLACK);
        paint.setStrokeWidth(3f);
        
        // 主车身到大臂的液压线
        float boomStartX = bodyX + scale * 0.08f;
        float boomStartY = bodyY - scale * 0.02f;
        canvas.drawLine(boomStartX, boomStartY, boomEndX, boomEndY, paint);
        
        // 大臂到小臂的液压线
        canvas.drawLine(boomEndX, boomEndY, stickEndX, stickEndY, paint);
        
        // 小臂到铲斗的液压线（稍微短一点）
        float bucketConnX = stickEndX + (float)(Math.cos(Math.toRadians(boomAngle + stickAngle + bucketAngle)) * scale * 0.05f);
        float bucketConnY = stickEndY - (float)(Math.sin(Math.toRadians(boomAngle + stickAngle + bucketAngle)) * scale * 0.05f);
        canvas.drawLine(stickEndX, stickEndY, bucketConnX, bucketConnY, paint);
    }
}

package com.example.pronunciation.ui.game;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;

/**
 * A fish that swims left to right above the passage, one step per sentence read correctly.
 *
 * <p>It only ever moves forward, and only when {@link #swimTo} is called — the whole point is
 * that progress is earned. A wrong attempt leaves it exactly where it was, which is the clearest
 * possible signal that the reader has not passed yet.
 */
public class FishProgressView extends View {

    private static final long SWIM_MS = 650;
    private static final float FISH_RADIUS_DP = 13f;

    private final Paint water = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eye = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF oval = new RectF();

    private float progress = 0f;
    private float density = 3f;
    private int steps = 0;
    @Nullable
    private ValueAnimator animator;

    public FishProgressView(Context context) {
        this(context, null);
    }

    public FishProgressView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;

        water.setStyle(Paint.Style.STROKE);
        water.setStrokeWidth(2f * density);
        water.setStrokeCap(Paint.Cap.ROUND);

        tick.setStyle(Paint.Style.FILL);

        body.setStyle(Paint.Style.FILL);
        eye.setColor(Color.WHITE);
        eye.setStyle(Paint.Style.FILL);
    }

    /** Colours come from the theme, so the fish works in light and dark. */
    public void setColors(int fishColor, int waterColor) {
        body.setColor(fishColor);
        water.setColor(waterColor);
        tick.setColor(waterColor);
        invalidate();
    }

    /** How many sentences this passage has; drawn as ticks along the water line. */
    public void setSteps(int steps) {
        this.steps = steps;
        invalidate();
    }

    /** Jumps without animating — for starting a new passage. */
    public void resetTo(float value) {
        cancelAnimation();
        progress = clamp(value);
        invalidate();
    }

    /** Swims to a new position in [0, 1]. */
    public void swimTo(float value) {
        cancelAnimation();
        float target = clamp(value);

        animator = ValueAnimator.ofFloat(progress, target);
        animator.setDuration(SWIM_MS);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    private void cancelAnimation() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        cancelAnimation();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        int height = (int) (44 * density);
        setMeasuredDimension(width, resolveSize(height, heightSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float r = FISH_RADIUS_DP * density;
        float lineY = getHeight() - r * 0.55f;
        float left = r * 1.6f;
        float right = getWidth() - r * 1.6f;
        if (right <= left) return;

        canvas.drawLine(left, lineY, right, lineY, water);

        // One tick per sentence, so the reader can see how far there is to go.
        if (steps > 1) {
            for (int i = 0; i <= steps; i++) {
                float x = left + (right - left) * i / (float) steps;
                canvas.drawCircle(x, lineY, 1.6f * density, tick);
            }
        }

        float cx = left + (right - left) * progress;
        float cy = lineY - r * 1.15f;
        drawFish(canvas, cx, cy, r);
    }

    private void drawFish(Canvas canvas, float cx, float cy, float r) {
        // Tail first, so the body overlaps its base.
        path.reset();
        path.moveTo(cx - r * 0.85f, cy);
        path.lineTo(cx - r * 1.55f, cy - r * 0.55f);
        path.lineTo(cx - r * 1.55f, cy + r * 0.55f);
        path.close();
        canvas.drawPath(path, body);

        oval.set(cx - r, cy - r * 0.62f, cx + r, cy + r * 0.62f);
        canvas.drawOval(oval, body);

        canvas.drawCircle(cx + r * 0.45f, cy - r * 0.14f, r * 0.16f, eye);
    }
}

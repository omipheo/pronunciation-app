package com.example.pronunciation.ui.game;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.Layout;
import android.util.AttributeSet;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;

/**
 * The game passage, with the progress fish drawn directly above the sentence being read.
 *
 * <p>Drawing the fish inside the text view rather than overlaying a separate one avoids any
 * coordination problem: this view owns the {@link Layout}, so it always knows exactly where a
 * character offset sits, even after a re-wrap, a font-size change or a rotation.
 *
 * <p>The line spacing in the layout leaves the gap the fish swims in — see
 * {@code lineSpacingExtra} on the view.
 */
public class PassageTextView extends AppCompatTextView {

    private static final long SWIM_MS = 600;
    private static final float FISH_RADIUS_DP = 8f;

    private final Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eye = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF oval = new RectF();

    private final float density;

    /** Character offset the fish is heading for; -1 means "nothing to show". */
    private int targetOffset = -1;
    private float fishX = Float.NaN;
    private float fishY = Float.NaN;
    @Nullable
    private ValueAnimator animator;

    public PassageTextView(Context context) {
        this(context, null);
    }

    public PassageTextView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        body.setStyle(Paint.Style.FILL);
        eye.setColor(Color.WHITE);
        eye.setStyle(Paint.Style.FILL);
    }

    public void setFishColor(int color) {
        body.setColor(color);
        invalidate();
    }

    /** Places the fish without animating — for the first sentence of a new passage. */
    public void jumpTo(int charOffset) {
        cancelAnimation();
        targetOffset = charOffset;
        fishX = Float.NaN;          // resolved on the next draw, once a Layout exists
        invalidate();
    }

    /** Swims to the sentence starting at {@code charOffset}. */
    public void swimTo(int charOffset) {
        cancelAnimation();

        float[] target = positionOf(charOffset);
        targetOffset = charOffset;

        if (target == null || Float.isNaN(fishX)) {
            invalidate();           // no layout yet, or nothing to animate from
            return;
        }

        final float fromX = fishX;
        final float fromY = fishY;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(SWIM_MS);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            float[] to = positionOf(targetOffset);
            if (to == null) return;
            fishX = fromX + (to[0] - fromX) * t;
            fishY = fromY + (to[1] - fromY) * t;
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

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        cancelAnimation();
    }

    /** Vertical scroll position that brings the current sentence into view, with context above. */
    public int scrollTargetFor(int charOffset) {
        Layout layout = getLayout();
        if (layout == null || charOffset < 0 || charOffset > getText().length()) return 0;

        int line = layout.getLineForOffset(charOffset);
        int top = layout.getLineTop(line) + getTotalPaddingTop();
        return Math.max(0, top - (int) (56 * density));
    }

    /** @return {x, y} for the fish body centre above {@code offset}, or null without a layout */
    @Nullable
    private float[] positionOf(int offset) {
        Layout layout = getLayout();
        if (layout == null || offset < 0 || offset > getText().length()) return null;

        int line = layout.getLineForOffset(offset);
        float r = FISH_RADIUS_DP * density;

        float x = layout.getPrimaryHorizontal(offset) + getTotalPaddingLeft() + r * 0.6f;
        // Sit in the gap above the line, not on top of the glyphs.
        float y = layout.getLineTop(line) + getTotalPaddingTop() - r * 0.75f;
        return new float[]{x, y};
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (targetOffset < 0) return;

        if (Float.isNaN(fishX)) {
            float[] p = positionOf(targetOffset);
            if (p == null) return;
            fishX = p[0];
            fishY = p[1];
        }
        FishDrawing.draw(canvas, fishX, fishY, FISH_RADIUS_DP * density, body, eye, path, oval);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        // Re-wrapping moves every offset, so snap to the new position rather than animating
        // from a coordinate that no longer means anything.
        if (changed && targetOffset >= 0 && animator == null) {
            float[] p = positionOf(targetOffset);
            if (p != null) {
                fishX = p[0];
                fishY = p[1];
            }
        }
    }
}

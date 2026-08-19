package com.example.pronunciation.ui.game;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * Draws the progress fish. Shared by the idle screen's water line and the passage view, so the
 * two cannot drift apart.
 */
final class FishDrawing {

    private FishDrawing() {
    }

    /**
     * @param cx   centre of the body
     * @param cy   centre of the body
     * @param r    body half-length; the fish spans roughly 3.1r wide and 1.24r tall
     * @param path scratch, to avoid allocating while drawing
     * @param oval scratch
     */
    static void draw(Canvas canvas, float cx, float cy, float r,
                     Paint body, Paint eye, Path path, RectF oval) {
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

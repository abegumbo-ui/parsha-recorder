package com.parsharec;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Tiny vector icons drawn with Canvas so the app needs no icon assets or fonts. */
public final class Glyph extends View {
    public static final int REC = 0, PAUSE = 1, PLAY = 2, STOP = 3, BACK = 4, MORE = 5, CHEVRON = 6, MIC = 7;

    private final int type;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();

    public Glyph(Context c, int type, int color) {
        super(c);
        this.type = type;
        p.setColor(color);
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), s = Math.min(w, h), cx = w / 2, cy = h / 2;
        p.setStyle(Paint.Style.FILL);
        switch (type) {
            case REC: {
                c.drawCircle(cx, cy, s / 2, p);
                int old = p.getColor();
                p.setColor(0xFFFFFFFF);
                c.drawCircle(cx, cy, s * 0.22f, p);
                p.setColor(old);
                break;
            }
            case MIC: {
                r.set(cx - s * 0.14f, cy - s * 0.3f, cx + s * 0.14f, cy + s * 0.1f);
                c.drawRoundRect(r, s * 0.14f, s * 0.14f, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(s * 0.06f);
                r.set(cx - s * 0.24f, cy - s * 0.2f, cx + s * 0.24f, cy + s * 0.22f);
                c.drawArc(r, 0, 180, false, p);
                c.drawLine(cx, cy + s * 0.22f, cx, cy + s * 0.34f, p);
                c.drawLine(cx - s * 0.12f, cy + s * 0.34f, cx + s * 0.12f, cy + s * 0.34f, p);
                break;
            }
            case PAUSE: {
                r.set(cx - s * 0.28f, cy - s * 0.3f, cx - s * 0.08f, cy + s * 0.3f);
                c.drawRoundRect(r, s * 0.05f, s * 0.05f, p);
                r.set(cx + s * 0.08f, cy - s * 0.3f, cx + s * 0.28f, cy + s * 0.3f);
                c.drawRoundRect(r, s * 0.05f, s * 0.05f, p);
                break;
            }
            case PLAY: {
                path.reset();
                path.moveTo(cx - s * 0.2f, cy - s * 0.32f);
                path.lineTo(cx + s * 0.34f, cy);
                path.lineTo(cx - s * 0.2f, cy + s * 0.32f);
                path.close();
                c.drawPath(path, p);
                break;
            }
            case STOP: {
                r.set(cx - s * 0.28f, cy - s * 0.28f, cx + s * 0.28f, cy + s * 0.28f);
                c.drawRoundRect(r, s * 0.08f, s * 0.08f, p);
                break;
            }
            case BACK:
            case CHEVRON: {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(s * 0.1f);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setStrokeJoin(Paint.Join.ROUND);
                float d = type == BACK ? -1 : 1;
                path.reset();
                path.moveTo(cx - d * s * 0.12f, cy - s * 0.26f);
                path.lineTo(cx + d * s * 0.14f, cy);
                path.lineTo(cx - d * s * 0.12f, cy + s * 0.26f);
                c.drawPath(path, p);
                break;
            }
            case MORE: {
                for (int i = -1; i <= 1; i++) c.drawCircle(cx, cy + i * s * 0.26f, s * 0.07f, p);
                break;
            }
        }
    }
}

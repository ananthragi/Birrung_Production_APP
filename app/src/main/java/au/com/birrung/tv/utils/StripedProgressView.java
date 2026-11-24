package au.com.birrung.tv.utils;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * A horizontal, rounded progress bar with an animated diagonal-stripe ("candy stripe") green
 * fill and the percentage drawn in the centre — the loader style the client requested.
 * The stripes drift continuously to convey activity; the fill width tracks {@link #setProgress}.
 */
public class StripedProgressView extends View {

    private float progress = 0f;    // 0..100
    private float stripePhase = 0f; // animated offset for the drifting stripes
    private boolean indeterminate = false; // true = full-width drifting stripes, no % (unknown progress)

    private int fillColor   = Color.parseColor("#4CAF50"); // green
    private int stripeColor = Color.parseColor("#66BB6A"); // lighter green stripe
    private int trackColor  = Color.parseColor("#E3E3E3");
    private int textColor   = Color.WHITE;

    private final Paint trackPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stripePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rectF = new RectF();
    private final Path  clip  = new Path();
    private final Rect  textBounds = new Rect();

    private ValueAnimator stripeAnim;

    public StripedProgressView(Context c) { super(c); init(); }
    public StripedProgressView(Context c, AttributeSet a) { super(c, a); init(); }
    public StripedProgressView(Context c, AttributeSet a, int s) { super(c, a, s); init(); }

    private void init() {
        trackPaint.setStyle(Paint.Style.FILL);
        fillPaint.setStyle(Paint.Style.FILL);
        stripePaint.setStyle(Paint.Style.STROKE);
        stripePaint.setStrokeCap(Paint.Cap.BUTT);
        textPaint.setColor(textColor);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        // Soft shadow so the % stays readable over both the green fill and the light track.
        textPaint.setShadowLayer(4f, 0f, 1f, Color.parseColor("#66000000"));
        applyColors();
    }

    private void applyColors() {
        trackPaint.setColor(trackColor);
        fillPaint.setColor(fillColor);
        stripePaint.setColor(stripeColor);
        textPaint.setColor(textColor);
    }

    public void setProgress(float p) {
        progress = Math.max(0f, Math.min(100f, p));
        invalidate();
    }

    public float getProgress() { return progress; }

    /** Indeterminate = the whole bar is animated stripes with NO percentage (progress unknown). */
    public void setIndeterminate(boolean value) {
        indeterminate = value;
        invalidate();
    }

    public void setColors(int fill, int stripe, int track, int text) {
        fillColor = fill; stripeColor = stripe; trackColor = track; textColor = text;
        applyColors();
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        stripeAnim = ValueAnimator.ofFloat(0f, 1f);
        stripeAnim.setDuration(800);
        stripeAnim.setRepeatCount(ValueAnimator.INFINITE);
        stripeAnim.setInterpolator(new LinearInterpolator());
        stripeAnim.addUpdateListener(a -> {
            stripePhase = (float) a.getAnimatedValue();
            invalidate();
        });
        stripeAnim.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (stripeAnim != null) { stripeAnim.cancel(); stripeAnim = null; }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        float r = h / 2f;

        // Track
        rectF.set(0, 0, w, h);
        canvas.drawRoundRect(rectF, r, r, trackPaint);

        // Fill (rounded), clipped so the stripes stay inside the pill.
        // Indeterminate → fill the WHOLE bar with drifting stripes (progress unknown, no %).
        float fw = indeterminate ? w : (w * progress / 100f);
        if (fw > 0.5f) {
            int save = canvas.save();
            clip.reset();
            clip.addRoundRect(new RectF(0, 0, Math.max(fw, h * 0.6f), h), r, r, Path.Direction.CW);
            canvas.clipPath(clip);

            canvas.drawRect(0, 0, fw, h, fillPaint);

            // Diagonal stripes drifting to the right.
            float period = h;                 // one stripe + one gap
            stripePaint.setStrokeWidth(h * 0.55f);
            float offset = stripePhase * period;
            for (float x = -h + offset; x < fw + h; x += period) {
                canvas.drawLine(x, h, x + h, 0, stripePaint); // 45° line
            }
            canvas.restoreToCount(save);
        }

        // Indeterminate mode shows NO percentage (progress is unknown) — just the moving stripes.
        if (indeterminate) return;

        // Percentage sits on the GREEN fill (centred within the filled portion) so it is always
        // white-on-green and readable. As the fill grows, the text moves right, ending up centred
        // in the whole loader at 100%. Clamped so it never clips the left/right edges.
        String t = Math.round(progress) + "%";
        textPaint.setTextSize(h * 0.5f);
        textPaint.getTextBounds(t, 0, t.length(), textBounds);
        float halfText = textBounds.width() / 2f + h * 0.18f; // half text + padding
        float tx = fw / 2f;                                   // centre of the green fill
        if (tx < halfText) tx = halfText;                     // don't clip the left edge
        if (tx > w - halfText) tx = w - halfText;             // don't clip the right edge
        canvas.drawText(t, tx, h / 2f - textBounds.exactCenterY(), textPaint);
    }
}

package com.example.pronunciation.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;

import androidx.core.content.ContextCompat;

import com.example.pronunciation.R;
import com.example.pronunciation.speech.PhonemeScore;
import com.example.pronunciation.speech.UtteranceScore;
import com.example.pronunciation.speech.WordScore;

/** Turns scores into the coloured text the learner actually reads. */
public final class ScoreFormatter {

    private ScoreFormatter() {
    }

    /**
     * The expected IPA for a word, coloured per phoneme: green where correct, red where a different
     * sound came out, struck through where nothing came out at all.
     */
    public static CharSequence colouredIpa(Context context, WordScore word) {
        SpannableStringBuilder sb = new SpannableStringBuilder();

        int correct = ContextCompat.getColor(context, R.color.score_good);
        int wrong = ContextCompat.getColor(context, R.color.score_bad);
        int missing = ContextCompat.getColor(context, R.color.score_missing);

        for (PhonemeScore p : word.phonemes) {
            int start = sb.length();
            sb.append(p.expected);
            int end = sb.length();

            switch (p.status) {
                case CORRECT:
                    sb.setSpan(new ForegroundColorSpan(correct), start, end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    break;
                case SUBSTITUTED:
                    sb.setSpan(new ForegroundColorSpan(wrong), start, end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    sb.setSpan(new StyleSpan(Typeface.BOLD), start, end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    break;
                case MISSING:
                    sb.setSpan(new ForegroundColorSpan(missing), start, end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    sb.setSpan(new StrikethroughSpan(), start, end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    break;
            }
        }
        return sb;
    }

    /** What actually came out where a phoneme was substituted, e.g. "heard s instead of θ". */
    public static String substitutionHint(WordScore word) {
        for (PhonemeScore p : word.phonemes) {
            if (p.status == PhonemeScore.Status.SUBSTITUTED && p.actual != null) {
                return "heard /" + p.actual + "/ instead of /" + p.expected + "/";
            }
        }
        for (PhonemeScore p : word.phonemes) {
            if (p.status == PhonemeScore.Status.MISSING) {
                return "/" + p.expected + "/ was not pronounced";
            }
        }
        return null;
    }

    public static int colourForPercent(Context context, int percent) {
        int res = percent >= 85 ? R.color.score_good
                : percent >= 60 ? R.color.score_ok
                : R.color.score_bad;
        return ContextCompat.getColor(context, res);
    }

    /** One-line verdict under the overall score. */
    public static String verdict(UtteranceScore score) {
        if (score.isEmpty()) {
            return "Nothing was picked up — try speaking closer to the microphone.";
        }

        WordScore weakest = score.weakestWord();
        if (score.overallPercent >= 90) {
            return "Excellent — that sounded natural.";
        }
        if (score.overallPercent >= 75) {
            return weakest != null && weakest.percent < 70
                    ? "Good. Work on \"" + weakest.word + "\"."
                    : "Good — a few sounds to tighten up.";
        }
        if (score.overallPercent >= 50) {
            return weakest != null
                    ? "Getting there. \"" + weakest.word + "\" needs the most work."
                    : "Getting there — listen and try again.";
        }
        return "Listen to the example, then try again slowly.";
    }
}

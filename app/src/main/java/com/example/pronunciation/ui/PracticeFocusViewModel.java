package com.example.pronunciation.ui;

import androidx.lifecycle.ViewModel;

/**
 * Carries "practise this sound" from the Main tab to the Training tab.
 *
 * <p>Activity-scoped, because the two screens are sibling bottom-navigation destinations with no
 * direct navigation between them to attach arguments to.
 *
 * <p>{@link #consume()} clears the request. Without that a stale focus would silently reapply
 * every time the user came back to Training, long after they had moved on.
 */
public class PracticeFocusViewModel extends ViewModel {

    private String pendingPhoneme;

    public void request(String phoneme) {
        this.pendingPhoneme = phoneme;
    }

    /** @return the requested phoneme exactly once, or null if there is no pending request */
    public String consume() {
        String p = pendingPhoneme;
        pendingPhoneme = null;
        return p;
    }
}

package com.capstone.excavator;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewGroup;

/**
 * Binds the shared expand/collapse interaction used by the two main-screen cards.
 */
final class CollapsibleCardController {

    private static final long ANIMATION_DURATION_MS = 220L;

    private CollapsibleCardController() {
    }

    static void bind(View container, View header, View body, View arrow) {
        if (container == null || header == null || body == null) {
            return;
        }

        State state = new State();
        container.post(() -> {
            state.expandedHeight = container.getHeight();
            state.collapsedHeight = header.getHeight();
        });

        Runnable expand = () -> {
            if (!state.collapsed) {
                return;
            }
            state.collapsed = false;
            body.setVisibility(View.VISIBLE);
            int from = container.getLayoutParams().height > 0
                    ? container.getLayoutParams().height
                    : state.collapsedHeight;
            int to = state.expandedHeight > 0 ? state.expandedHeight : container.getHeight();
            animateHeight(container, from, to, null);
            if (arrow != null) {
                arrow.animate().rotation(0f).setDuration(180L).start();
            }
        };

        Runnable collapse = () -> {
            if (state.collapsed) {
                return;
            }
            state.collapsed = true;
            int from = container.getHeight();
            int to = state.collapsedHeight > 0 ? state.collapsedHeight : header.getHeight();
            animateHeight(container, from, to, () -> body.setVisibility(View.GONE));
            if (arrow != null) {
                arrow.animate().rotation(180f).setDuration(180L).start();
            }
        };

        if (arrow != null) {
            arrow.setOnClickListener(view -> {
                if (state.collapsed) {
                    expand.run();
                } else {
                    collapse.run();
                }
            });
        }
        header.setOnClickListener(view -> {
            if (state.collapsed) {
                expand.run();
            }
        });
    }

    private static void animateHeight(View view, int from, int to, Runnable endAction) {
        if (from == to) {
            if (endAction != null) {
                endAction.run();
            }
            return;
        }

        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        ValueAnimator animator = ValueAnimator.ofInt(from, to);
        animator.setDuration(ANIMATION_DURATION_MS);
        animator.addUpdateListener(animation -> {
            layoutParams.height = (int) animation.getAnimatedValue();
            view.setLayoutParams(layoutParams);
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (endAction != null) {
                    endAction.run();
                }
            }
        });
        animator.start();
    }

    private static final class State {
        boolean collapsed;
        int expandedHeight = -1;
        int collapsedHeight = -1;
    }
}

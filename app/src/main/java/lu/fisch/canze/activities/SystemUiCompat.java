package lu.fisch.canze.activities;

import android.content.res.Configuration;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

final class SystemUiCompat {

    private SystemUiCompat() {
    }

    /**
     * Applies safe system bars with automatic light/dark status bar detection based on current theme.
     */
    static void applySafeSystemBars(AppCompatActivity activity) {
        boolean isLightMode = isLightTheme(activity);
        applySafeSystemBars(activity, isLightMode);
    }

    /**
     * Applies safe system bars with explicit light/dark status bar setting.
     *
     * @param activity Activity to apply insets to
     * @param isLight True for light status bar (dark icons), false for dark status bar (light icons)
     */
    static void applySafeSystemBars(AppCompatActivity activity, boolean isLight) {
        ViewCompat.setOnApplyWindowInsetsListener(activity.getWindow().getDecorView(), new OnApplyWindowInsetsListener() {
            @NonNull
            @Override
            public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat insets) {
                Insets bars = insets.getInsets(
                        WindowInsetsCompat.Type.systemBars()
                                | WindowInsetsCompat.Type.displayCutout()
                );
                WindowCompat.getInsetsController(activity.getWindow(), activity.getWindow().getDecorView())
                        .setAppearanceLightStatusBars(isLight);
                v.setPadding(
                        bars.left,
                        bars.top,
                        bars.right,
                        bars.bottom
                );
                return WindowInsetsCompat.CONSUMED;
            }
        });
        // Keep content below status/navigation bars for legacy screens.
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), true);
    }

    /**
     * Detects if the current theme is light or dark mode.
     *
     * @param activity Activity to check theme for
     * @return True if light theme, false if dark theme
     */
    private static boolean isLightTheme(AppCompatActivity activity) {
        int nightModeFlags = activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightModeFlags == Configuration.UI_MODE_NIGHT_NO;
    }
}

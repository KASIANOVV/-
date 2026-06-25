package ru.kasianov.phone;

import android.Manifest;
import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.telecom.TelecomManager;
import android.text.InputType;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends Activity {
    private static final int REQUEST_PHONE_PERMISSIONS = 100;
    private static final int REQUEST_DIALER_ROLE = 101;
    private static final String PREFS = "dialer_prefs";
    private static final String RECENTS = "recent_numbers";

    private EditText numberInput;
    private LinearLayout recentContainer;
    private final int bg = Color.rgb(8, 10, 15);
    private final int panel = Color.rgb(22, 25, 34);
    private final int panelLight = Color.rgb(35, 39, 50);
    private final int accent = Color.rgb(118, 255, 169);
    private final int muted = Color.rgb(151, 158, 174);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();
        setContentView(buildUi());
        handleDialIntent(getIntent());
        requestPhonePermissions();
        requestDialerRole();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleDialIntent(intent);
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(bg);
        window.setNavigationBarColor(bg);
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.setSystemBarsAppearance(0,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS |
                                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            }
        } else {
            window.getDecorView().setSystemUiVisibility(0);
        }
    }

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(bg);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(22), dp(22), dp(22));
        FrameLayout.LayoutParams contentParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        root.addView(content, contentParams);

        TextView brand = text("KASYANOV", 13, accent, Typeface.BOLD);
        brand.setLetterSpacing(0.28f);
        content.addView(brand);

        TextView title = text("Телефон", 34, Color.WHITE, Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = dp(3);
        content.addView(title, titleParams);

        TextView subtitle = text("Звонки через вашу SIM-карту", 14, muted, Typeface.NORMAL);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dp(2);
        content.addView(subtitle, subtitleParams);

        LinearLayout inputCard = new LinearLayout(this);
        inputCard.setOrientation(LinearLayout.HORIZONTAL);
        inputCard.setGravity(Gravity.CENTER_VERTICAL);
        inputCard.setPadding(dp(18), dp(5), dp(8), dp(5));
        inputCard.setBackground(rounded(panel, 24, 0, 0));
        LinearLayout.LayoutParams inputCardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(74));
        inputCardParams.topMargin = dp(24);
        content.addView(inputCard, inputCardParams);

        numberInput = new EditText(this);
        numberInput.setTextColor(Color.WHITE);
        numberInput.setHintTextColor(Color.rgb(92, 98, 113));
        numberInput.setHint("Введите номер");
        numberInput.setTextSize(25);
        numberInput.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        numberInput.setSingleLine(true);
        numberInput.setInputType(InputType.TYPE_CLASS_PHONE);
        numberInput.setBackgroundColor(Color.TRANSPARENT);
        numberInput.setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams numberParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        inputCard.addView(numberInput, numberParams);

        TextView delete = text("⌫", 29, muted, Typeface.NORMAL);
        delete.setGravity(Gravity.CENTER);
        delete.setBackground(rippleLike(panelLight, 19));
        delete.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            int start = numberInput.getSelectionStart();
            int end = numberInput.getSelectionEnd();
            if (start != end) {
                numberInput.getText().delete(Math.min(start, end), Math.max(start, end));
            } else if (start > 0) {
                numberInput.getText().delete(start - 1, start);
            }
        });
        delete.setOnLongClickListener(v -> {
            numberInput.setText("");
            return true;
        });
        inputCard.addView(delete, new LinearLayout.LayoutParams(dp(50), dp(50)));

        TextView recentTitle = text("НЕДАВНИЕ", 11, muted, Typeface.BOLD);
        recentTitle.setLetterSpacing(0.18f);
        LinearLayout.LayoutParams recentTitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        recentTitleParams.topMargin = dp(18);
        content.addView(recentTitle, recentTitleParams);

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        recentContainer = new LinearLayout(this);
        recentContainer.setOrientation(LinearLayout.HORIZONTAL);
        scroll.addView(recentContainer);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        scrollParams.topMargin = dp(8);
        content.addView(scroll, scrollParams);
        renderRecents();

        Space spacer = new Space(this);
        content.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));

        String[][] keys = {
                {"1", ""}, {"2", "ABC"}, {"3", "DEF"},
                {"4", "GHI"}, {"5", "JKL"}, {"6", "MNO"},
                {"7", "PQRS"}, {"8", "TUV"}, {"9", "WXYZ"},
                {"*", ""}, {"0", "+"}, {"#", ""}
        };

        for (int row = 0; row < 4; row++) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setGravity(Gravity.CENTER);
            for (int col = 0; col < 3; col++) {
                int index = row * 3 + col;
                View key = createKey(keys[index][0], keys[index][1]);
                LinearLayout.LayoutParams keyParams = new LinearLayout.LayoutParams(dp(78), dp(68));
                keyParams.leftMargin = dp(10);
                keyParams.rightMargin = dp(10);
                keyParams.topMargin = dp(5);
                keyParams.bottomMargin = dp(5);
                rowLayout.addView(key, keyParams);
            }
            content.addView(rowLayout);
        }

        TextView callButton = text("☎", 34, bg, Typeface.BOLD);
        callButton.setGravity(Gravity.CENTER);
        callButton.setBackground(rounded(accent, 36, 0, 0));
        callButton.setElevation(dp(10));
        callButton.setContentDescription("Позвонить");
        callButton.setOnClickListener(v -> placeCall());
        LinearLayout.LayoutParams callParams = new LinearLayout.LayoutParams(dp(76), dp(76));
        callParams.gravity = Gravity.CENTER_HORIZONTAL;
        callParams.topMargin = dp(10);
        callParams.bottomMargin = dp(4);
        content.addView(callButton, callParams);

        return root;
    }

    private View createKey(String digit, String letters) {
        LinearLayout key = new LinearLayout(this);
        key.setOrientation(LinearLayout.VERTICAL);
        key.setGravity(Gravity.CENTER);
        key.setBackground(rippleLike(panel, 24));
        key.setClickable(true);
        key.setFocusable(true);

        TextView digitView = text(digit, 27, Color.WHITE, Typeface.BOLD);
        digitView.setGravity(Gravity.CENTER);
        key.addView(digitView);

        if (!letters.isEmpty()) {
            TextView lettersView = text(letters, 9, muted, Typeface.BOLD);
            lettersView.setGravity(Gravity.CENTER);
            lettersView.setLetterSpacing(0.16f);
            key.addView(lettersView);
        }

        key.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            int cursor = Math.max(0, numberInput.getSelectionStart());
            numberInput.getText().insert(cursor, digit);
        });
        if ("0".equals(digit)) {
            key.setOnLongClickListener(v -> {
                int cursor = Math.max(0, numberInput.getSelectionStart());
                numberInput.getText().insert(cursor, "+");
                return true;
            });
        }
        return key;
    }

    private void placeCall() {
        String number = numberInput.getText().toString().trim();
        if (number.isEmpty()) {
            Toast.makeText(this, "Введите номер телефона", Toast.LENGTH_SHORT).show();
            return;
        }
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            requestPhonePermissions();
            return;
        }

        saveRecent(number);
        Uri uri = Uri.fromParts("tel", number, null);
        try {
            TelecomManager telecom = (TelecomManager) getSystemService(TELECOM_SERVICE);
            if (telecom != null) {
                telecom.placeCall(uri, new Bundle());
            } else {
                startActivity(new Intent(Intent.ACTION_CALL, uri));
            }
        } catch (SecurityException ex) {
            Toast.makeText(this, "Разрешите приложению совершать звонки", Toast.LENGTH_LONG).show();
            requestPhonePermissions();
        } catch (Exception ex) {
            startActivity(new Intent(Intent.ACTION_DIAL, uri));
        }
    }

    private void requestPhonePermissions() {
        List<String> missing = new ArrayList<>();
        for (String permission : Arrays.asList(
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.ANSWER_PHONE_CALLS)) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                missing.add(permission);
            }
        }
        if (!missing.isEmpty()) {
            requestPermissions(missing.toArray(new String[0]), REQUEST_PHONE_PERMISSIONS);
        }
    }

    private void requestDialerRole() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                RoleManager roleManager = (RoleManager) getSystemService(ROLE_SERVICE);
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)
                        && !roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                    startActivityForResult(roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER),
                            REQUEST_DIALER_ROLE);
                }
            } else {
                TelecomManager telecom = (TelecomManager) getSystemService(TELECOM_SERVICE);
                if (telecom != null && !getPackageName().equals(telecom.getDefaultDialerPackage())) {
                    Intent intent = new Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER);
                    intent.putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME,
                            getPackageName());
                    startActivityForResult(intent, REQUEST_DIALER_ROLE);
                }
            }
        } catch (Exception ignored) {
            Toast.makeText(this,
                    "Назначьте KASYANOV PHONE приложением «Телефон» в настройках Android",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void handleDialIntent(Intent intent) {
        if (intent != null && Intent.ACTION_DIAL.equals(intent.getAction()) && intent.getData() != null) {
            String number = intent.getData().getSchemeSpecificPart();
            if (numberInput != null && number != null) {
                numberInput.setText(number);
                numberInput.setSelection(number.length());
            }
        }
    }

    private void saveRecent(String number) {
        SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String old = prefs.getString(RECENTS, "");
        List<String> list = new ArrayList<>();
        list.add(number);
        if (old != null && !old.isEmpty()) {
            for (String item : old.split("\\|", -1)) {
                if (!item.isEmpty() && !item.equals(number)) list.add(item);
                if (list.size() >= 8) break;
            }
        }
        prefs.edit().putString(RECENTS, String.join("|", list)).apply();
        renderRecents();
    }

    private void renderRecents() {
        if (recentContainer == null) return;
        recentContainer.removeAllViews();
        String saved = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(RECENTS, "");
        if (saved == null || saved.isEmpty()) {
            TextView empty = text("Здесь появятся набранные номера", 13, muted, Typeface.NORMAL);
            empty.setGravity(Gravity.CENTER_VERTICAL);
            recentContainer.addView(empty, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT));
            return;
        }
        for (String number : saved.split("\\|", -1)) {
            if (number.isEmpty()) continue;
            TextView chip = text(number, 14, Color.WHITE, Typeface.BOLD);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(17), 0, dp(17), 0);
            chip.setBackground(rounded(panel, 18, 1, Color.rgb(46, 51, 65)));
            chip.setOnClickListener(v -> {
                numberInput.setText(number);
                numberInput.setSelection(number.length());
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(38));
            params.rightMargin = dp(8);
            recentContainer.addView(chip, params);
        }
    }

    private TextView text(String value, int sizeSp, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif", style));
        view.setIncludeFontPadding(false);
        return view;
    }

    private GradientDrawable rounded(int color, int radiusDp, int strokeDp, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) drawable.setStroke(dp(strokeDp), strokeColor);
        return drawable;
    }

    private GradientDrawable rippleLike(int color, int radiusDp) {
        return rounded(color, radiusDp, 1, Color.rgb(45, 50, 63));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

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
import android.view.WindowInsetsController;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int REQUEST_CALL_PERMISSION = 100;
    private static final int REQUEST_DIALER_ROLE = 101;
    private static final String PREFS = "dialer_prefs";
    private static final String RECENTS = "recent_numbers";

    private final int bg = Color.rgb(8, 10, 15);
    private final int panel = Color.rgb(22, 25, 34);
    private final int panelLight = Color.rgb(35, 39, 50);
    private final int accent = Color.rgb(118, 255, 169);
    private final int muted = Color.rgb(151, 158, 174);

    private EditText numberInput;
    private LinearLayout recentContainer;
    private TextView roleStatus;
    private String pendingNumber;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();
        setContentView(buildUi());
        handleDialIntent(getIntent());
        refreshRoleStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshRoleStatus();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleDialIntent(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_CALL_PERMISSION || pendingNumber == null) return;

        String number = pendingNumber;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            continueCallFlow(number);
        } else {
            pendingNumber = null;
            Toast.makeText(this, "Без разрешения Android не может начать звонок", Toast.LENGTH_LONG).show();
            openSystemDialer(number);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_DIALER_ROLE) return;

        refreshRoleStatus();
        if (pendingNumber != null) {
            String number = pendingNumber;
            pendingNumber = null;
            executeCall(number);
        }
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(bg);
        window.setNavigationBarColor(bg);
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) controller.setSystemBarsAppearance(0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS |
                            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        }
    }

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(bg);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        root.addView(scroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(22), dp(22), dp(28));
        scroll.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        TextView brand = text("KASYANOV", 13, accent, Typeface.BOLD);
        brand.setLetterSpacing(0.28f);
        content.addView(brand);

        TextView title = text("Телефон", 34, Color.WHITE, Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = wrap();
        titleParams.topMargin = dp(3);
        content.addView(title, titleParams);

        TextView subtitle = text("Звонки через вашу SIM-карту", 14, muted, Typeface.NORMAL);
        LinearLayout.LayoutParams subtitleParams = wrap();
        subtitleParams.topMargin = dp(2);
        content.addView(subtitle, subtitleParams);

        LinearLayout roleCard = new LinearLayout(this);
        roleCard.setOrientation(LinearLayout.HORIZONTAL);
        roleCard.setGravity(Gravity.CENTER_VERTICAL);
        roleCard.setPadding(dp(15), 0, dp(15), 0);
        roleCard.setBackground(rounded(panel, 18, 1, Color.rgb(46, 51, 65)));
        roleCard.setClickable(true);
        roleCard.setFocusable(true);
        roleCard.setOnClickListener(v -> requestDialerRole(false));
        LinearLayout.LayoutParams roleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        roleParams.topMargin = dp(16);
        content.addView(roleCard, roleParams);

        roleStatus = text("Проверяем системный экран звонка…", 13, Color.WHITE, Typeface.BOLD);
        roleCard.addView(roleStatus, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView roleArrow = text("›", 28, accent, Typeface.NORMAL);
        roleCard.addView(roleArrow);

        LinearLayout inputCard = new LinearLayout(this);
        inputCard.setOrientation(LinearLayout.HORIZONTAL);
        inputCard.setGravity(Gravity.CENTER_VERTICAL);
        inputCard.setPadding(dp(18), dp(5), dp(8), dp(5));
        inputCard.setBackground(rounded(panel, 24, 0, 0));
        LinearLayout.LayoutParams inputCardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(72));
        inputCardParams.topMargin = dp(16);
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
        inputCard.addView(numberInput, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        TextView delete = text("⌫", 29, muted, Typeface.NORMAL);
        delete.setGravity(Gravity.CENTER);
        delete.setBackground(rounded(panelLight, 19, 1, Color.rgb(45, 50, 63)));
        delete.setOnClickListener(v -> deleteOneCharacter(v));
        delete.setOnLongClickListener(v -> {
            numberInput.setText("");
            return true;
        });
        inputCard.addView(delete, new LinearLayout.LayoutParams(dp(50), dp(50)));

        TextView recentTitle = text("НЕДАВНИЕ", 11, muted, Typeface.BOLD);
        recentTitle.setLetterSpacing(0.18f);
        LinearLayout.LayoutParams recentTitleParams = wrap();
        recentTitleParams.topMargin = dp(15);
        content.addView(recentTitle, recentTitleParams);

        HorizontalScrollView recentScroll = new HorizontalScrollView(this);
        recentScroll.setHorizontalScrollBarEnabled(false);
        recentContainer = new LinearLayout(this);
        recentContainer.setOrientation(LinearLayout.HORIZONTAL);
        recentScroll.addView(recentContainer);
        LinearLayout.LayoutParams recentParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(45));
        recentParams.topMargin = dp(7);
        content.addView(recentScroll, recentParams);
        renderRecents();

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
                LinearLayout.LayoutParams keyParams = new LinearLayout.LayoutParams(dp(78), dp(66));
                keyParams.setMargins(dp(9), dp(4), dp(9), dp(4));
                rowLayout.addView(key, keyParams);
            }
            content.addView(rowLayout);
        }

        TextView callButton = text("☎", 34, bg, Typeface.BOLD);
        callButton.setGravity(Gravity.CENTER);
        callButton.setBackground(rounded(accent, 36, 0, 0));
        callButton.setElevation(dp(10));
        callButton.setContentDescription("Позвонить");
        callButton.setOnClickListener(v -> startCallFlow());
        LinearLayout.LayoutParams callParams = new LinearLayout.LayoutParams(dp(76), dp(76));
        callParams.gravity = Gravity.CENTER_HORIZONTAL;
        callParams.topMargin = dp(10);
        content.addView(callButton, callParams);

        TextView hint = text("Первый запуск: разрешите звонки. Для фирменного экрана выберите приложение телефоном по умолчанию.",
                11, muted, Typeface.NORMAL);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = dp(12);
        content.addView(hint, hintParams);

        return root;
    }

    private View createKey(String digit, String letters) {
        LinearLayout key = new LinearLayout(this);
        key.setOrientation(LinearLayout.VERTICAL);
        key.setGravity(Gravity.CENTER);
        key.setBackground(rounded(panel, 24, 1, Color.rgb(45, 50, 63)));
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

    private void deleteOneCharacter(View source) {
        source.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        int start = numberInput.getSelectionStart();
        int end = numberInput.getSelectionEnd();
        if (start != end) {
            numberInput.getText().delete(Math.min(start, end), Math.max(start, end));
        } else if (start > 0) {
            numberInput.getText().delete(start - 1, start);
        }
    }

    private void startCallFlow() {
        String number = numberInput.getText().toString().trim();
        if (number.isEmpty()) {
            Toast.makeText(this, "Введите номер телефона", Toast.LENGTH_SHORT).show();
            return;
        }
        pendingNumber = number;
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CALL_PHONE}, REQUEST_CALL_PERMISSION);
            return;
        }
        continueCallFlow(number);
    }

    private void continueCallFlow(String number) {
        if (!isDefaultDialer() && requestDialerRole(true)) return;
        pendingNumber = null;
        executeCall(number);
    }

    private void executeCall(String number) {
        saveRecent(number);
        Uri uri = Uri.fromParts("tel", number, null);
        try {
            if (isDefaultDialer()) {
                TelecomManager telecom = (TelecomManager) getSystemService(TELECOM_SERVICE);
                if (telecom != null) {
                    telecom.placeCall(uri, new Bundle());
                    return;
                }
            }
            startActivity(new Intent(Intent.ACTION_CALL, uri));
        } catch (SecurityException error) {
            Toast.makeText(this, "Android запретил прямой звонок — открываю штатный набор номера", Toast.LENGTH_LONG).show();
            openSystemDialer(number);
        } catch (Exception error) {
            openSystemDialer(number);
        }
    }

    private void openSystemDialer(String number) {
        try {
            startActivity(new Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)));
        } catch (Exception ignored) {
            Toast.makeText(this, "На устройстве не найдено приложение для звонков", Toast.LENGTH_LONG).show();
        }
    }

    private boolean requestDialerRole(boolean fromCall) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                RoleManager manager = (RoleManager) getSystemService(ROLE_SERVICE);
                if (manager != null && manager.isRoleAvailable(RoleManager.ROLE_DIALER)
                        && !manager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                    startActivityForResult(manager.createRequestRoleIntent(RoleManager.ROLE_DIALER),
                            REQUEST_DIALER_ROLE);
                    return true;
                }
            } else {
                TelecomManager telecom = (TelecomManager) getSystemService(TELECOM_SERVICE);
                if (telecom != null && !getPackageName().equals(telecom.getDefaultDialerPackage())) {
                    Intent intent = new Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER);
                    intent.putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME,
                            getPackageName());
                    startActivityForResult(intent, REQUEST_DIALER_ROLE);
                    return true;
                }
            }
        } catch (Exception ignored) {
            if (!fromCall) Toast.makeText(this,
                    "Откройте Настройки → Приложения → Приложения по умолчанию → Телефон",
                    Toast.LENGTH_LONG).show();
        }
        if (!fromCall) refreshRoleStatus();
        return false;
    }

    private boolean isDefaultDialer() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                RoleManager manager = (RoleManager) getSystemService(ROLE_SERVICE);
                return manager != null && manager.isRoleHeld(RoleManager.ROLE_DIALER);
            }
            TelecomManager telecom = (TelecomManager) getSystemService(TELECOM_SERVICE);
            return telecom != null && getPackageName().equals(telecom.getDefaultDialerPackage());
        } catch (Exception ignored) {
            return false;
        }
    }

    private void refreshRoleStatus() {
        if (roleStatus == null) return;
        if (isDefaultDialer()) {
            roleStatus.setText("Фирменный экран звонка включён");
            roleStatus.setTextColor(accent);
        } else {
            roleStatus.setText("Включить фирменный экран звонка");
            roleStatus.setTextColor(Color.WHITE);
        }
    }

    private void handleDialIntent(Intent intent) {
        if (intent == null || !Intent.ACTION_DIAL.equals(intent.getAction())) return;
        Uri data = intent.getData();
        if (data == null) return;
        String number = data.getSchemeSpecificPart();
        if (number != null && numberInput != null) {
            numberInput.setText(number);
            numberInput.setSelection(number.length());
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

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

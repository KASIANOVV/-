package ru.kasianov.phone;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQUEST_CALL_PERMISSION = 200;

    private final int bg = Color.rgb(8, 10, 15);
    private final int panel = Color.rgb(24, 27, 36);
    private final int border = Color.rgb(48, 53, 68);
    private final int accent = Color.rgb(104, 255, 161);
    private final int muted = Color.rgb(154, 160, 175);

    private EditText numberInput;
    private String pendingNumber;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);

        try {
            setContentView(buildUi());
        } catch (Throwable error) {
            setContentView(buildEmergencyUi(error));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_CALL_PERMISSION || pendingNumber == null) return;

        String number = pendingNumber;
        pendingNumber = null;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            callNow(number);
        } else {
            openSystemDialer(number);
        }
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(bg);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(24), dp(22), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        TextView brand = makeText("KASYANOV", 13, accent, Typeface.BOLD);
        brand.setLetterSpacing(0.24f);
        root.addView(brand);

        TextView title = makeText("Телефон", 34, Color.WHITE, Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = wrapParams();
        titleParams.topMargin = dp(4);
        root.addView(title, titleParams);

        TextView subtitle = makeText("Стабильная версия для Xiaomi • звонки через SIM", 13, muted, Typeface.NORMAL);
        LinearLayout.LayoutParams subtitleParams = wrapParams();
        subtitleParams.topMargin = dp(4);
        root.addView(subtitle, subtitleParams);

        LinearLayout inputCard = new LinearLayout(this);
        inputCard.setOrientation(LinearLayout.HORIZONTAL);
        inputCard.setGravity(Gravity.CENTER_VERTICAL);
        inputCard.setPadding(dp(18), dp(5), dp(8), dp(5));
        inputCard.setBackground(rounded(panel, 24, 1, border));
        LinearLayout.LayoutParams inputCardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(74));
        inputCardParams.topMargin = dp(24);
        root.addView(inputCard, inputCardParams);

        numberInput = new EditText(this);
        numberInput.setHint("Введите номер");
        numberInput.setHintTextColor(Color.rgb(94, 101, 116));
        numberInput.setTextColor(Color.WHITE);
        numberInput.setTextSize(25);
        numberInput.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        numberInput.setInputType(InputType.TYPE_CLASS_PHONE);
        numberInput.setSingleLine(true);
        numberInput.setBackgroundColor(Color.TRANSPARENT);
        numberInput.setPadding(0, 0, 0, 0);
        inputCard.addView(numberInput, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        TextView clear = makeText("⌫", 28, muted, Typeface.NORMAL);
        clear.setGravity(Gravity.CENTER);
        clear.setBackground(rounded(Color.rgb(34, 38, 48), 19, 1, border));
        clear.setOnClickListener(this::deleteOne);
        clear.setOnLongClickListener(v -> {
            numberInput.setText("");
            return true;
        });
        inputCard.addView(clear, new LinearLayout.LayoutParams(dp(50), dp(50)));

        String[][] keys = {
                {"1", ""}, {"2", "ABC"}, {"3", "DEF"},
                {"4", "GHI"}, {"5", "JKL"}, {"6", "MNO"},
                {"7", "PQRS"}, {"8", "TUV"}, {"9", "WXYZ"},
                {"*", ""}, {"0", "+"}, {"#", ""}
        };

        LinearLayout.LayoutParams keyboardTop = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        keyboardTop.topMargin = dp(20);

        LinearLayout keyboard = new LinearLayout(this);
        keyboard.setOrientation(LinearLayout.VERTICAL);
        root.addView(keyboard, keyboardTop);

        for (int rowIndex = 0; rowIndex < 4; rowIndex++) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER);
            for (int column = 0; column < 3; column++) {
                int index = rowIndex * 3 + column;
                View key = createKey(keys[index][0], keys[index][1]);
                LinearLayout.LayoutParams keyParams = new LinearLayout.LayoutParams(dp(82), dp(70));
                keyParams.setMargins(dp(9), dp(5), dp(9), dp(5));
                row.addView(key, keyParams);
            }
            keyboard.addView(row);
        }

        TextView callButton = makeText("☎", 35, bg, Typeface.BOLD);
        callButton.setGravity(Gravity.CENTER);
        callButton.setBackground(rounded(accent, 38, 0, 0));
        callButton.setElevation(dp(10));
        callButton.setOnClickListener(v -> beginCall());
        LinearLayout.LayoutParams callParams = new LinearLayout.LayoutParams(dp(78), dp(78));
        callParams.gravity = Gravity.CENTER_HORIZONTAL;
        callParams.topMargin = dp(14);
        root.addView(callButton, callParams);

        TextView note = makeText(
                "Экран разговора будет штатным Xiaomi. Это сделано специально, чтобы приложение не закрывалось и звонок работал стабильно.",
                12, muted, Typeface.NORMAL);
        note.setGravity(Gravity.CENTER);
        note.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        noteParams.topMargin = dp(18);
        root.addView(note, noteParams);

        return scroll;
    }

    private View createKey(String digit, String letters) {
        LinearLayout key = new LinearLayout(this);
        key.setOrientation(LinearLayout.VERTICAL);
        key.setGravity(Gravity.CENTER);
        key.setBackground(rounded(panel, 24, 1, border));
        key.setClickable(true);
        key.setFocusable(true);

        TextView digitView = makeText(digit, 27, Color.WHITE, Typeface.BOLD);
        digitView.setGravity(Gravity.CENTER);
        key.addView(digitView);

        if (!letters.isEmpty()) {
            TextView lettersView = makeText(letters, 9, muted, Typeface.BOLD);
            lettersView.setLetterSpacing(0.14f);
            lettersView.setGravity(Gravity.CENTER);
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

    private void deleteOne(View source) {
        source.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        int start = numberInput.getSelectionStart();
        int end = numberInput.getSelectionEnd();
        if (start != end) {
            numberInput.getText().delete(Math.min(start, end), Math.max(start, end));
        } else if (start > 0) {
            numberInput.getText().delete(start - 1, start);
        }
    }

    private void beginCall() {
        String number = numberInput.getText().toString().trim();
        if (number.isEmpty()) {
            Toast.makeText(this, "Введите номер телефона", Toast.LENGTH_SHORT).show();
            return;
        }

        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            pendingNumber = number;
            requestPermissions(new String[]{Manifest.permission.CALL_PHONE}, REQUEST_CALL_PERMISSION);
            return;
        }
        callNow(number);
    }

    private void callNow(String number) {
        try {
            Intent intent = new Intent(Intent.ACTION_CALL);
            intent.setData(Uri.fromParts("tel", number, null));
            startActivity(intent);
        } catch (SecurityException error) {
            openSystemDialer(number);
        } catch (Throwable error) {
            openSystemDialer(number);
        }
    }

    private void openSystemDialer(String number) {
        try {
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(Uri.fromParts("tel", number, null));
            startActivity(intent);
        } catch (Throwable error) {
            Toast.makeText(this, "Не удалось открыть штатный телефон Xiaomi", Toast.LENGTH_LONG).show();
        }
    }

    private View buildEmergencyUi(Throwable error) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));
        root.setBackgroundColor(bg);

        TextView title = makeText("KASYANOV PHONE запущен в безопасном режиме", 22, Color.WHITE, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView message = makeText("Основной интерфейс не загрузился. Перешлите этот код: "
                + error.getClass().getSimpleName(), 14, muted, Typeface.NORMAL);
        message.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(18);
        root.addView(message, params);
        return root;
    }

    private TextView makeText(String value, int sizeSp, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif", style));
        view.setIncludeFontPadding(false);
        return view;
    }

    private LinearLayout.LayoutParams wrapParams() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
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

package ru.kasianov.phone;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telecom.Call;
import android.telecom.VideoProfile;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

public class InCallActivity extends Activity {
    public static final String ACTION_CALL_REMOVED = "ru.kasianov.phone.CALL_REMOVED";

    private final int bg = Color.rgb(8, 10, 15);
    private final int panel = Color.rgb(27, 30, 39);
    private final int accent = Color.rgb(118, 255, 169);
    private final int danger = Color.rgb(255, 76, 92);
    private final int mutedColor = Color.rgb(151, 158, 174);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView statusView;
    private TextView timerView;
    private TextView numberView;
    private LinearLayout actionArea;
    private Call call;
    private long connectedAt = 0L;
    private boolean muted;
    private boolean speaker;

    private final Runnable timerTick = new Runnable() {
        @Override
        public void run() {
            if (connectedAt > 0 && timerView != null) {
                long seconds = (System.currentTimeMillis() - connectedAt) / 1000;
                timerView.setText(String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60));
                handler.postDelayed(this, 1000);
            }
        }
    };

    private final Call.Callback callback = new Call.Callback() {
        @Override
        public void onStateChanged(Call changedCall, int state) {
            runOnUiThread(() -> updateState(state));
        }

        @Override
        public void onDetailsChanged(Call changedCall, Call.Details details) {
            runOnUiThread(InCallActivity.this::updateNumber);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        setContentView(buildUi());
        attachCall();
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        if (ACTION_CALL_REMOVED.equals(intent.getAction())) {
            statusView.setText("Звонок завершён");
            handler.postDelayed(this::finish, 900);
        } else {
            attachCall();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (call != null) call.unregisterCallback(callback);
        super.onDestroy();
    }

    private void attachCall() {
        Call latest = CallService.currentCall;
        if (call == latest && call != null) {
            updateState(call.getState());
            return;
        }
        if (call != null) call.unregisterCallback(callback);
        call = latest;
        if (call == null) {
            statusView.setText("Звонок завершён");
            handler.postDelayed(this::finish, 900);
            return;
        }
        call.registerCallback(callback);
        updateNumber();
        updateState(call.getState());
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(42), dp(24), dp(30));
        root.setBackgroundColor(bg);

        TextView brand = text("KASYANOV PHONE", 12, accent, Typeface.BOLD);
        brand.setLetterSpacing(0.24f);
        root.addView(brand);

        TextView avatar = text("☎", 42, Color.WHITE, Typeface.BOLD);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(gradientCircle());
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(112), dp(112));
        avatarParams.topMargin = dp(38);
        root.addView(avatar, avatarParams);

        numberView = text("Неизвестный номер", 28, Color.WHITE, Typeface.BOLD);
        numberView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams numberParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        numberParams.topMargin = dp(26);
        root.addView(numberView, numberParams);

        statusView = text("Подключение…", 15, mutedColor, Typeface.NORMAL);
        statusView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(8);
        root.addView(statusView, statusParams);

        timerView = text("", 16, accent, Typeface.BOLD);
        timerView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams timerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        timerParams.topMargin = dp(7);
        root.addView(timerView, timerParams);

        View spacer = new View(this);
        root.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));

        actionArea = new LinearLayout(this);
        actionArea.setOrientation(LinearLayout.VERTICAL);
        actionArea.setGravity(Gravity.CENTER);
        root.addView(actionArea, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return root;
    }

    private void updateNumber() {
        if (call == null || call.getDetails() == null) return;
        Uri handle = call.getDetails().getHandle();
        String number = handle == null ? null : handle.getSchemeSpecificPart();
        numberView.setText(number == null || number.isEmpty() ? "Неизвестный номер" : number);
    }

    private void updateState(int state) {
        if (call == null) return;
        switch (state) {
            case Call.STATE_RINGING:
                statusView.setText("Входящий вызов");
                timerView.setText("");
                showIncomingActions();
                break;
            case Call.STATE_DIALING:
                statusView.setText("Вызов…");
                timerView.setText("");
                showActiveActions();
                break;
            case Call.STATE_CONNECTING:
                statusView.setText("Подключение…");
                showActiveActions();
                break;
            case Call.STATE_ACTIVE:
                statusView.setText("На связи");
                if (connectedAt == 0) {
                    connectedAt = System.currentTimeMillis();
                    handler.removeCallbacks(timerTick);
                    handler.post(timerTick);
                }
                showActiveActions();
                break;
            case Call.STATE_HOLDING:
                statusView.setText("Удержание");
                showActiveActions();
                break;
            case Call.STATE_DISCONNECTED:
            case Call.STATE_DISCONNECTING:
                statusView.setText("Звонок завершён");
                handler.removeCallbacks(timerTick);
                handler.postDelayed(this::finish, 1000);
                break;
            default:
                statusView.setText("Телефонный вызов");
                showActiveActions();
                break;
        }
    }

    private void showIncomingActions() {
        actionArea.removeAllViews();
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);

        TextView reject = actionButton("✕", "Отклонить", danger, Color.WHITE);
        reject.setOnClickListener(v -> {
            if (call != null) call.reject(false, null);
        });
        row.addView(reject, actionParams());

        TextView answer = actionButton("☎", "Ответить", accent, bg);
        answer.setOnClickListener(v -> {
            if (call != null) call.answer(VideoProfile.STATE_AUDIO_ONLY);
        });
        row.addView(answer, actionParams());
        actionArea.addView(row);
    }

    private void showActiveActions() {
        actionArea.removeAllViews();

        LinearLayout first = new LinearLayout(this);
        first.setGravity(Gravity.CENTER);
        TextView muteButton = controlButton(muted ? "●\nМикрофон" : "◉\nМикрофон", muted);
        muteButton.setOnClickListener(v -> {
            muted = !muted;
            if (CallService.instance != null) CallService.instance.setMutedCompat(muted);
            showActiveActions();
        });
        first.addView(muteButton, controlParams());

        TextView keypad = controlButton("⌨\nКлавиши", false);
        keypad.setOnClickListener(v -> showDtmfKeypad());
        first.addView(keypad, controlParams());

        TextView speakerButton = controlButton(speaker ? "◖))\nДинамик" : "◖)\nДинамик", speaker);
        speakerButton.setOnClickListener(v -> {
            speaker = !speaker;
            if (CallService.instance != null) CallService.instance.setSpeaker(speaker);
            showActiveActions();
        });
        first.addView(speakerButton, controlParams());
        actionArea.addView(first);

        LinearLayout second = new LinearLayout(this);
        second.setGravity(Gravity.CENTER);
        boolean holding = call != null && call.getState() == Call.STATE_HOLDING;
        TextView hold = controlButton(holding ? "▶\nПродолжить" : "Ⅱ\nУдержать", holding);
        hold.setOnClickListener(v -> {
            if (call == null) return;
            if (call.getState() == Call.STATE_HOLDING) call.unhold();
            else call.hold();
        });
        second.addView(hold, controlParams());
        actionArea.addView(second);

        TextView end = text("☎", 31, Color.WHITE, Typeface.BOLD);
        end.setRotation(135f);
        end.setGravity(Gravity.CENTER);
        end.setBackground(rounded(danger, 37, 0, 0));
        end.setOnClickListener(v -> {
            if (call != null) call.disconnect();
        });
        LinearLayout.LayoutParams endParams = new LinearLayout.LayoutParams(dp(76), dp(76));
        endParams.gravity = Gravity.CENTER_HORIZONTAL;
        endParams.topMargin = dp(22);
        endParams.bottomMargin = dp(8);
        actionArea.addView(end, endParams);
    }

    private TextView controlButton(String label, boolean selected) {
        TextView view = text(label, 13, selected ? bg : Color.WHITE, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setLineSpacing(dp(3), 1f);
        view.setBackground(rounded(selected ? accent : panel, 23, 1,
                selected ? accent : Color.rgb(49, 54, 68)));
        return view;
    }

    private TextView actionButton(String icon, String label, int color, int textColor) {
        TextView view = text(icon + "\n" + label, 17, textColor, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setLineSpacing(dp(5), 1f);
        view.setBackground(rounded(color, 30, 0, 0));
        return view;
    }

    private LinearLayout.LayoutParams controlParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(96), dp(82));
        params.setMargins(dp(5), dp(5), dp(5), dp(5));
        return params;
    }

    private LinearLayout.LayoutParams actionParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(138), dp(94));
        params.setMargins(dp(10), dp(8), dp(10), dp(8));
        return params;
    }

    private void showDtmfKeypad() {
        Dialog dialog = new Dialog(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(18), dp(18), dp(18));
        box.setBackground(rounded(Color.rgb(18, 21, 29), 28, 1, Color.rgb(48, 53, 67)));

        String[] digits = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#"};
        for (int rowIndex = 0; rowIndex < 4; rowIndex++) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER);
            for (int column = 0; column < 3; column++) {
                String digit = digits[rowIndex * 3 + column];
                TextView key = text(digit, 24, Color.WHITE, Typeface.BOLD);
                key.setGravity(Gravity.CENTER);
                key.setBackground(rounded(panel, 20, 1, Color.rgb(50, 55, 69)));
                key.setOnClickListener(v -> sendDtmf(digit.charAt(0)));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(72), dp(60));
                params.setMargins(dp(5), dp(5), dp(5), dp(5));
                row.addView(key, params);
            }
            box.addView(row);
        }
        TextView close = text("Готово", 15, accent, Typeface.BOLD);
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(50));
        closeParams.topMargin = dp(8);
        box.addView(close, closeParams);

        dialog.setContentView(box);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();
        if (window != null) window.setLayout(dp(300), WindowManager.LayoutParams.WRAP_CONTENT);
    }

    private void sendDtmf(char digit) {
        if (call == null) return;
        call.playDtmfTone(digit);
        handler.postDelayed(() -> {
            if (call != null) call.stopDtmfTone();
        }, 160);
    }

    private GradientDrawable gradientCircle() {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(57, 71, 91), Color.rgb(25, 30, 43)});
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setStroke(dp(2), Color.rgb(74, 84, 104));
        return drawable;
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

package ru.kasianov.phone;

import android.content.Intent;
import android.os.Build;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.InCallService;

public class CallService extends InCallService {
    public static volatile CallService instance;
    public static volatile Call currentCall;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }

    @Override
    public void onCallAdded(Call call) {
        super.onCallAdded(call);
        currentCall = call;
        launchCallScreen();
    }

    @Override
    public void onCallRemoved(Call call) {
        super.onCallRemoved(call);
        if (currentCall == call) currentCall = null;
        Intent intent = new Intent(this, InCallActivity.class);
        intent.setAction(InCallActivity.ACTION_CALL_REMOVED);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    public void setMutedCompat(boolean muted) {
        setMuted(muted);
    }

    public void setSpeaker(boolean enabled) {
        if (Build.VERSION.SDK_INT >= 34) {
            requestCallEndpointChange(
                    enabled ? findEndpoint(CallAudioState.ROUTE_SPEAKER) : findEndpoint(CallAudioState.ROUTE_EARPIECE),
                    getMainExecutor(),
                    outcome -> { }
            );
        } else {
            setAudioRoute(enabled ? CallAudioState.ROUTE_SPEAKER : CallAudioState.ROUTE_EARPIECE);
        }
    }

    @SuppressWarnings("deprecation")
    private android.telecom.CallEndpoint findEndpoint(int route) {
        if (Build.VERSION.SDK_INT < 34) return null;
        for (android.telecom.CallEndpoint endpoint : getAvailableCallEndpoints()) {
            if ((route == CallAudioState.ROUTE_SPEAKER && endpoint.getEndpointType() == android.telecom.CallEndpoint.TYPE_SPEAKER)
                    || (route == CallAudioState.ROUTE_EARPIECE && endpoint.getEndpointType() == android.telecom.CallEndpoint.TYPE_EARPIECE)) {
                return endpoint;
            }
        }
        return getCurrentCallEndpoint();
    }

    private void launchCallScreen() {
        Intent intent = new Intent(this, InCallActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }
}

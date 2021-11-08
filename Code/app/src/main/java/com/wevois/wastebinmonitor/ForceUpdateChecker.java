package com.wevois.wastebinmonitor;

import android.content.Context;
import android.content.pm.PackageManager;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.remoteconfig.FirebaseRemoteConfig;

public class ForceUpdateChecker {

    public static final String
            KEY_UPDATE_REQUIRED = "wastebin_monitor_update_required",
            KEY_CURRENT_VERSION = "wastebin_monitor_current_version",
            KEY_UPDATE_URL = "wastebin_monitor_playstore_url",
            KEY_MUST_UPDATE = "wastebin_monitor_must_update";

    private OnUpdateNeededListener onUpdateNeededListener;
    private Context context;

    public interface OnUpdateNeededListener {
        void onUpdateNeeded(String updateUrl);
    }

    public static Builder with(@NonNull Context context) {
        return new Builder(context);
    }

    public ForceUpdateChecker(@NonNull Context context, OnUpdateNeededListener onUpdateNeededListener) {
        this.context = context;
        this.onUpdateNeededListener = onUpdateNeededListener;
    }

    public void check() {
        try {
            FirebaseRemoteConfig remoteConfig = FirebaseRemoteConfig.getInstance();
            remoteConfig.fetchAndActivate().addOnCompleteListener(task -> {
                if (task.isSuccessful()){
                    remoteConfig.getString(KEY_CURRENT_VERSION);
                }

                if (remoteConfig.getBoolean(KEY_UPDATE_REQUIRED)) {
                    String currentVersion = remoteConfig.getString(KEY_CURRENT_VERSION);
                    String appVersion = getAppVersion(context);
                    String updateUrl = remoteConfig.getString(KEY_UPDATE_URL);
                    if (!TextUtils.equals(currentVersion, appVersion) && onUpdateNeededListener != null) {
                        onUpdateNeededListener.onUpdateNeeded(updateUrl);
                    } else if (TextUtils.equals(currentVersion, appVersion) && onUpdateNeededListener != null) {
                        onUpdateNeededListener.onUpdateNeeded(null);
                    }
                }
            });
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public boolean mustUpdate() {
        FirebaseRemoteConfig remoteConfig = FirebaseRemoteConfig.getInstance();
        return remoteConfig.getBoolean(KEY_MUST_UPDATE);
    }

    private String getAppVersion(Context context) {
        String result = "";
        try {
            result = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0)
                    .versionName;
            result = result.replaceAll("[a-zA-Z]|-", "");
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return result;
    }

    public static class Builder {
        private Context context;
        private OnUpdateNeededListener onUpdateNeededListener;

        public Builder(Context context) {
            this.context = context;
        }

        public Builder onUpdateNeeded(OnUpdateNeededListener onUpdateNeededListener) {
            this.onUpdateNeededListener = onUpdateNeededListener;
            return this;
        }

        public ForceUpdateChecker build() {
            return new ForceUpdateChecker(context, onUpdateNeededListener);
        }

        public ForceUpdateChecker check() {
            ForceUpdateChecker forceUpdateChecker = build();
            forceUpdateChecker.check();

            return forceUpdateChecker;
        }
    }
}

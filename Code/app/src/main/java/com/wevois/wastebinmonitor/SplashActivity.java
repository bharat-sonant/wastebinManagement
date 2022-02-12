package com.wevois.wastebinmonitor;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.AlertDialog;
import android.app.LauncherActivity;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.provider.Settings;
import android.util.Log;
import android.view.View;

import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.UpdateAvailability;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.HashMap;

public class SplashActivity extends AppCompatActivity {
    CommonFunctions cmn = new CommonFunctions();
    SharedPreferences pref;
    boolean checkPermission = false;
    String[] PERMISSIONS = {
            Manifest.permission.CALL_PHONE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA,
            android.Manifest.permission.ACCESS_FINE_LOCATION
    };

    private AppUpdateManager mAppUpdateManager;
    private static final int RC_APP_UPDATE = 11;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        pref = getSharedPreferences("LoginDetails", MODE_PRIVATE);
        cmn.fetchWastebinMonitorSettings(this);
        pref.edit().putString("dbRef", "https://iejaipurgreater.firebaseio.com/").apply();
        pref.edit().putString("stoRef", "gs://dtdnavigator.appspot.com/Jaipur-Greater").apply();
//        pref.edit().putString("dbRef", "https://dtdnavigatortesting.firebaseio.com/").apply();
//        pref.edit().putString("stoRef", "gs://dtdnavigator.appspot.com/Test").apply();
        if (!pref.getString("date", " ").equals(cmn.getDate())) {
            pref.edit().putString("date", cmn.getDate()).apply();
            pref.edit().putBoolean("isDeleteImages", true).apply();
        }
        cmn.setLocale(pref.getString("lang", "hi"), SplashActivity.this);
        new Thread(() -> cmn.fetchDaysForDeletingImage(SplashActivity.this)).start();
        new Thread(() -> cmn.fetchDistanceValidations(SplashActivity.this)).start();
        new Thread(() -> {
            cmn.getDatabaseRef(this).child("WastebinMonitor/Users/" + pref.getString("uid","")+"/token").addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.getValue() == null) {
                        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                HashMap<String, Object> hashMap = new HashMap<>();
                                hashMap.put("token", task.getResult());
                                cmn.getDatabaseRef(SplashActivity.this).child("WastebinMonitor/Users/" + pref.getString("uid","") + "/").updateChildren(hashMap);
                            }
                        });

                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {

                }
            });

        }).start();
    }

    @Override
    protected void onStart() {
        super.onStart();
        UpdateApp();
    }

    public void UpdateApp(){
        mAppUpdateManager = AppUpdateManagerFactory.create(getApplicationContext());
        mAppUpdateManager.getAppUpdateInfo().addOnSuccessListener(appUpdateInfo -> {
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                    && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE /*AppUpdateType.IMMEDIATE*/)){
                try {
                    mAppUpdateManager.startUpdateFlowForResult(
                            appUpdateInfo, AppUpdateType.IMMEDIATE /*AppUpdateType.IMMEDIATE*/, SplashActivity.this, RC_APP_UPDATE);

                } catch (IntentSender.SendIntentException e) {
                    e.printStackTrace();
                }
            }else {
                checkPermission();
            }
        });
    }

    public void checkPermission() {
        SplashActivity.this.runOnUiThread(() -> {
            if (ActivityCompat.checkSelfPermission(SplashActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    || ActivityCompat.checkSelfPermission(SplashActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(SplashActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(SplashActivity.this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(SplashActivity.this, PERMISSIONS, 0000);
                return;
            }
            proceed();
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 0000) {
            if (grantResults.length > 0) {
                for (int i = 0; i < permissions.length; i++) {
                    String per = permissions[i];
                    if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                        if (ActivityCompat.shouldShowRequestPermissionRationale(this, per)) {
                            AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
                            alertBuilder.setCancelable(false);
                            alertBuilder.setTitle("जरूरी सूचना");
                            alertBuilder.setMessage("सभी permissions देना अनिवार्य है बिना permissions के आप आगे नहीं बढ़ सकते है |");
                            alertBuilder.setPositiveButton(android.R.string.yes, (dialog, which) -> checkPermission());

                            AlertDialog alert = alertBuilder.create();
                            alert.show();
                        } else {
                            infoDialog();
                        }
                        return;
                    }
                }
                proceed();
            } else {
                checkPermission();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 0000) {
            if (resultCode == RESULT_OK) {
                proceed();
            } else {
                checkPermission();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (checkPermission) {
            checkPermission = false;
            checkPermission();
        }
    }

    private void proceed() {
        new CountDownTimer(3000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
            }

            @Override
            public void onFinish() {
                if (pref.getString("uid", "").trim().length() > 1) {
                    startActivity(new Intent(SplashActivity.this, MainActivity.class));
                } else {
                    startActivity(new Intent(SplashActivity.this, LoginActivity.class));
                }
                finish();
            }
        }.start();
    }

    private void infoDialog() {
        try {
            cmn.closeDialog(this);
            View diaLayout = this.getLayoutInflater().inflate(R.layout.info_dialog_layout, null);
            AlertDialog.Builder alertDialog = new AlertDialog.Builder(this).setView(diaLayout).setCancelable(false);
            AlertDialog infoDialog = alertDialog.create();
            diaLayout.findViewById(R.id.accept_dialog_btn).setOnClickListener(v -> {
                infoDialog.dismiss();
                checkPermission = true;
                cmn.intentToAppInfo(this);
            });
            infoDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            infoDialog.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
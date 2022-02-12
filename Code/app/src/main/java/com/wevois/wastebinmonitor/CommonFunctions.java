package com.wevois.wastebinmonitor;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Rect;
import android.hardware.Camera;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CommonFunctions {
    ProgressDialog dialog;

    public SharedPreferences getDatabaseSp(Context context) {
        return context.getSharedPreferences("LoginDetails", Context.MODE_PRIVATE);
    }

    public SharedPreferences wardBoundPref(Context context) {
        return context.getSharedPreferences("WardBoundaries", Context.MODE_PRIVATE);
    }

    public SharedPreferences zonesPref(Context context) {
        return context.getSharedPreferences("Zones", Context.MODE_PRIVATE);
    }

    public DatabaseReference getDatabaseRef(Context context) {
        return FirebaseDatabase.getInstance(getDatabaseSp(context).getString("dbRef", " ")).getReference();
    }

    public StorageReference getStoRef(Context context) {
        return FirebaseStorage.getInstance().getReferenceFromUrl(getDatabaseSp(context).getString("stoRef", ""));
    }

    public String getYear() {
        return new SimpleDateFormat("yyyy").format(new Date());
    }

    public String getMonth() {
        return new SimpleDateFormat("MMMM", Locale.US).format(new Date());
    }

    @SuppressLint("SimpleDateFormat")
    public String getDate() {
        return new SimpleDateFormat("yyyy-MM-dd").format(new Date());
    }

    public String getTime() {
        return new SimpleDateFormat("HH:mm").format(new Date());
    }

    public void setProgressDialog(String title, String message, Context context, Activity activity) {
        closeDialog(activity);
        dialog = new ProgressDialog(context);
        dialog.setTitle(title);
        dialog.setMessage(message);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            dialog.create();
        }
        dialog.setCancelable(false);
        dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        if (!dialog.isShowing() && !activity.isFinishing()) {
            dialog.show();
        }
    }

    public void closeDialog(Activity activity) {
        if (dialog != null) {
            if (dialog.isShowing() && !activity.isFinishing()) {
                dialog.dismiss();
            }
        }
    }

    public void closeAlertDialog(AlertDialog dialog) {
        if (dialog != null) {
            dialog.dismiss();
        }
    }

    private Rect calculateFocusArea(float x, float y, SurfaceView surfaceView) {
        int left = clamp(Float.valueOf((x / surfaceView.getWidth()) * 2000 - 1000).intValue());
        int top = clamp(Float.valueOf((y / surfaceView.getHeight()) * 2000 - 1000).intValue());

        return new Rect(left, top, left + MainActivity.FOCUS_AREA_SIZE, top + MainActivity.FOCUS_AREA_SIZE);
    }

    private int clamp(int touchCoordinateInCam) {
        if (Math.abs(touchCoordinateInCam) + MainActivity.FOCUS_AREA_SIZE / 2 > 1000) {
            if (touchCoordinateInCam > 0) {
                return 1000 - MainActivity.FOCUS_AREA_SIZE / 2;
            } else {
                return -1000 + MainActivity.FOCUS_AREA_SIZE / 2;
            }
        } else {
            return touchCoordinateInCam - MainActivity.FOCUS_AREA_SIZE / 2;
        }
    }

    public void setCameraDisplayOrientation(Activity activity, int cameraId, Camera camera) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (360 - (info.orientation + degrees) % 360) % 360;
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        camera.setDisplayOrientation(result);
    }

    public void focusOnTouch(MotionEvent event, Camera mCamera, SurfaceView surfaceView) {
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            if (parameters.getMaxNumMeteringAreas() > 0) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                Rect rect = calculateFocusArea(event.getX(), event.getY(), surfaceView);
                List<Camera.Area> meteringAreas = new ArrayList<Camera.Area>();
                meteringAreas.add(new Camera.Area(rect, 800));
                parameters.setFocusAreas(meteringAreas);
                mCamera.setParameters(parameters);
            }
            mCamera.autoFocus((success, camera) -> {

            });

        }
    }

    private boolean internetIsConnected() {
        try {
            HttpURLConnection urlc = (HttpURLConnection) (new URL("https://google.com").openConnection());
            urlc.setRequestProperty("User-Agent", "Test");
            urlc.setRequestProperty("Connection", "close");
            urlc.setConnectTimeout(10000);
            urlc.setReadTimeout(10000);
            urlc.connect();
            return (urlc.getResponseCode() == 200);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean network(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnected() && internetIsConnected();
    }

    public void setLocale(String temp, Activity activity) {
        getDatabaseSp(activity).edit().putString("lang", temp).apply();
        Locale myLocale = new Locale(temp);
        Resources res = activity.getResources();
        DisplayMetrics dm = res.getDisplayMetrics();
        Configuration conf = res.getConfiguration();
        conf.locale = myLocale;
        res.updateConfiguration(conf, dm);
    }

    public void intentToAppInfo(Activity activity) {
        Intent intent = new Intent();
        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
        intent.setData(uri);
        activity.startActivity(intent);
    }

    public void fetchHelplineNumber(Context context, TextView textView) {
        try {
            getStoRef(context).child("/Defaults/HelpLineNumber.json").getMetadata()
                    .addOnSuccessListener(storageMetadata -> {
                        long fileCreationTime = storageMetadata.getCreationTimeMillis();
                        long fileDownloadTime = getDatabaseSp(context).getLong("HelpLineNumberDownloadTime", 0);
                        if (fileDownloadTime != fileCreationTime) {
                            try {
                                getStoRef(context).child("/Defaults/HelpLineNumber.json")
                                        .getBytes(10000000)
                                        .addOnSuccessListener(taskSnapshot -> {
                                            String str = new String(taskSnapshot, StandardCharsets.UTF_8);
                                            CommonFunctions.this.getDatabaseSp(context).edit().putString("HelpLineNumber", str.toString().trim()).apply();
                                            CommonFunctions.this.getDatabaseSp(context).edit().putLong("HelpLineNumberDownloadTime", fileCreationTime).apply();
                                            textView.setText(CommonFunctions.this.getDatabaseSp(context).getString("HelpLineNumber", " "));
                                            CommonFunctions.this.callOnNumber(context, textView);
                                        }).addOnFailureListener(exception -> textView.setText("Not available"));
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        } else {
                            textView.setText(getDatabaseSp(context).getString("HelpLineNumber", " "));
                            callOnNumber(context, textView);
                        }
                    }).addOnFailureListener(e -> textView.setText("Not available"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void fetchDistanceValidations(Context context) {
        try {
            getStoRef(context).child("/Defaults/WastebinDistanceValidation.json").getMetadata()
                    .addOnSuccessListener(storageMetadata -> {
                        long fileCreationTime = storageMetadata.getCreationTimeMillis();
                        long fileDownloadTime = getDatabaseSp(context).getLong("WastebinDistanceValidationDownloadTime", 0);
                        if (fileDownloadTime != fileCreationTime) {
                            try {
                                getStoRef(context).child("/Defaults/WastebinDistanceValidation.json")
                                        .getBytes(1024 * 1024)
                                        .addOnSuccessListener(taskSnapshot -> {
                                            String str = new String(taskSnapshot, StandardCharsets.UTF_8);
                                            CommonFunctions.this.getDatabaseSp(context).edit().putString("WastebinDistanceValidation", str.trim()).apply();
                                            CommonFunctions.this.getDatabaseSp(context).edit().putLong("WastebinDistanceValidationDownloadTime", fileCreationTime).apply();
                                        });
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void fetchDaysForDeletingImage(Context context) {
        try {
            getStoRef(context).child("/Defaults/DaysForDeletingImagesInWastebinMonitor.json").getMetadata()
                    .addOnSuccessListener(storageMetadata -> {
                        long fileCreationTime = storageMetadata.getCreationTimeMillis();
                        long fileDownloadTime = getDatabaseSp(context).getLong("DaysForDeletingImagesInWastebinMonitorDownloadTime", 0);
                        if (fileDownloadTime != fileCreationTime) {
                            try {
                                getStoRef(context).child("/Defaults/DaysForDeletingImagesInWastebinMonitor.json")
                                        .getBytes(10000000)
                                        .addOnSuccessListener(taskSnapshot -> {
                                            String str = new String(taskSnapshot, StandardCharsets.UTF_8);
                                            getDatabaseSp(context).edit().putInt("DaysForDeletingImagesInWastebinMonitor", Integer.parseInt(str.trim())).apply();
                                            getDatabaseSp(context).edit().putLong("DaysForDeletingImagesInWastebinMonitorDownloadTime", fileCreationTime).apply();
                                        });
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void fetchWastebinMonitorSettings(Context context) {
        try {
            getDatabaseRef(context).child("Settings/WastebinMonitorApplicationSettings").addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.getValue()!=null){
                        if (snapshot.hasChild("loginMessage")) {
                            getDatabaseSp(context).edit().putString("loginMessage", snapshot.child("loginMessage").getValue().toString()).apply();
                        }
                        if (snapshot.hasChild("notificationMessage")) {
                            getDatabaseSp(context).edit().putString("notificationMessage", snapshot.child("notificationMessage").getValue().toString()).apply();
                        }
                        if (snapshot.hasChild("notificationTitle")) {
                            getDatabaseSp(context).edit().putString("notificationTitle", snapshot.child("notificationTitle").getValue().toString()).apply();
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {

                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void callOnNumber(Context context, TextView textView) {
        textView.setOnClickListener(view -> {
            AlertDialog.Builder alertBuilder = new AlertDialog.Builder(context);
            alertBuilder.setCancelable(true);
            alertBuilder.setMessage( R.string.helpline_message);
            alertBuilder.setPositiveButton(android.R.string.yes, (dialog, which) -> {
                Intent callIntent = new Intent(Intent.ACTION_CALL);
                callIntent.setData(Uri.parse("tel:" + textView.getText().toString()));//change the number
                context.startActivity(callIntent);
            });
            alertBuilder.setNegativeButton(android.R.string.no, (dialog, which) -> dialog.dismiss());
            AlertDialog alert = alertBuilder.create();
            alert.show();
        });
    }

    public float distance(float lat_a, float lng_a, float lat_b, float lng_b) {
        double earthRadius = 3958.75;
        double latDiff = Math.toRadians(lat_b - lat_a);
        double lngDiff = Math.toRadians(lng_b - lng_a);
        double a = Math.sin(latDiff / 2) * Math.sin(latDiff / 2) +
                Math.cos(Math.toRadians(lat_a)) * Math.cos(Math.toRadians(lat_b)) *
                        Math.sin(lngDiff / 2) * Math.sin(lngDiff / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = earthRadius * c;

        int meterConversion = 1609;

        return new Float(distance * meterConversion).floatValue();
    }

    public int timeDiff(Date startDate, Date endDate) {
        long different = endDate.getTime() - startDate.getTime();
        long secondsInMilli = 1000;
        long minutesInMilli = secondsInMilli * 60;
        long elapsedMinutes = different / minutesInMilli;
        return (int) elapsedMinutes;
    }
}

package com.wevois.wastebinmonitor.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.wevois.wastebinmonitor.R;
import com.wevois.wastebinmonitor.SplashActivity;
import com.wevois.wastebinmonitor.views.ResolvedActivity;

public class MyFirebaseMessaging extends FirebaseMessagingService {

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        getNotification(remoteMessage);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void getNotification(RemoteMessage remoteMessage) {
        NotificationManager notif = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        Notification notify = null;
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getApplicationContext());
        Intent intent = new Intent(this, ResolvedActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        Bundle extras = new Bundle();
        extras.putString("reference", remoteMessage.getData().get("reference"));
        intent.putExtras(extras);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel notificationChannel = new NotificationChannel("MAN", "NOTIFICATION_CHANNEL_NAME", importance);
            notificationChannel.enableLights(true);
            notificationChannel.setLightColor(Color.RED);
            notificationChannel.enableVibration(true);
            notificationChannel.setVibrationPattern(new long[]{100, 200, 300, 400, 500, 400, 300, 200, 400});
            assert notif != null;
            mBuilder.setChannelId("MAN").
                    setContentTitle(remoteMessage.getNotification().getTitle()).
                    setContentText(remoteMessage.getNotification().getBody()).
                    setContentIntent(pendingIntent).
                    setAutoCancel(true).
                    setStyle(new NotificationCompat.BigTextStyle()
                    .bigText(remoteMessage.getNotification().getBody())).
                    setSmallIcon(R.drawable.login_app_icon).setColor(Color.GREEN);
            notif.createNotificationChannel(notificationChannel);
            assert notif != null;
            notif.notify(0 /* Request Code */, mBuilder.build());
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                notify = new Notification.Builder
                        (getApplicationContext()).
                        setContentTitle(remoteMessage.getNotification().getTitle()).
                        setContentText(remoteMessage.getNotification().getBody()).
                        setContentIntent(pendingIntent).
                        setAutoCancel(true).
                        setSmallIcon(R.drawable.login_app_icon).setColor(Color.GREEN).build();
            }

            notify.flags |= Notification.FLAG_AUTO_CANCEL;
            notif.notify(0, notify);
        }
    }
}

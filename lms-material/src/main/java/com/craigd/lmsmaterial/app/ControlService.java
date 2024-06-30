/**
 * LMS-Material-App
 *
 * Copyright (c) 2020-2023 Craig Drummond <craig.p.drummond@gmail.com>
 * MIT license.
 */

package com.craigd.lmsmaterial.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public class ControlService extends Service {
    private static final String NEXT_TRACK = ControlService.class.getCanonicalName() + ".NEXT_TRACK";
    private static final String PREV_TRACK = ControlService.class.getCanonicalName() + ".PREV_TRACK";
    private static final String PLAY_TRACK = ControlService.class.getCanonicalName() + ".PLAY_TRACK";
    private static final String PAUSE_TRACK = ControlService.class.getCanonicalName() + ".PAUSE_TRACK";
    public static final int PLAYER_NAME = 1;

    private static final int MSG_ID = 1;
    private static final String[] PREV_COMMAND = {"button", "jump_rew"};
    private static final String[] PLAY_COMMAND = {"play"};
    private static final String[] PAUSE_COMMAND = {"pause", "1"};
    private static final String[] NEXT_COMMAND = {"playlist", "index", "+1"};
    public static final String NOTIFICATION_CHANNEL_ID = "lms_control_service";

    private static boolean isRunning = false;

    public static boolean isActive() {
        return isRunning;
    }

    private JsonRpc rpc;
    private NotificationCompat.Builder notificationBuilder;
    private NotificationManagerCompat notificationManager;
    private final Messenger messenger = new Messenger(
            new IncomingHandler()
    );

    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == PLAYER_NAME && null!=notificationBuilder && null!=notificationManager) {
                notificationBuilder.setContentTitle((String) (msg.obj));
                if (Build.VERSION.SDK_INT >= 33 && ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                notificationManager.notify(MSG_ID, notificationBuilder.build());
            } else {
                super.handleMessage(msg);
            }
        }
    }

    public ControlService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return messenger.getBinder();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("LMS", "ControlService.onCreate()");
        startForegroundService();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d("LMS", "ControlService.onDestroy()");
        stopForegroundService();
    }

    private void sendCommand(String[] command) {
        if (null == MainActivity.activePlayer) {
            return;
        }
        if (null == rpc) {
            rpc = new JsonRpc(getApplicationContext());
        }
        rpc.sendMessage(MainActivity.activePlayer, command);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (PREV_TRACK.equals(action)) {
                sendCommand(PREV_COMMAND);
            } else if (PLAY_TRACK.equals(action)) {
                sendCommand(PLAY_COMMAND);
            } else if (PAUSE_TRACK.equals(action)) {
                sendCommand(PAUSE_COMMAND);
            } else if (NEXT_TRACK.equals(action)) {
                sendCommand(NEXT_COMMAND);
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    private void startForegroundService() {
        Log.d(MainActivity.TAG, "Start control service.");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel();
        } else {
            notificationBuilder = new NotificationCompat.Builder(this);
        }
        createNotification();
        isRunning = true;
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private void createNotificationChannel() {
        notificationManager = NotificationManagerCompat.from(this);
        NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, getApplicationContext().getResources().getString(R.string.main_notification), NotificationManager.IMPORTANCE_LOW);
        chan.setLightColor(Color.BLUE);
        chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        chan.setShowBadge(false);
        chan.enableLights(false);
        chan.enableVibration(false);
        chan.setSound(null, null);
        notificationManager.createNotificationChannel(chan);
        notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
    }

    @NonNull
    private PendingIntent getPendingIntent(@NonNull String action) {
        Intent intent = new Intent(this, ControlService.class);
        intent.setAction(action);
        return PendingIntent.getService(this, 0, intent, Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @SuppressLint("MissingPermission")
    private void createNotification() {
        if (!Utils.notificationAllowed(this, NOTIFICATION_CHANNEL_ID)) {
            return;
        }
        try {
            Intent intent = new Intent(this, MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : PendingIntent.FLAG_UPDATE_CURRENT);
            Notification notification = notificationBuilder.setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setSmallIcon(R.drawable.ic_mono_icon)
                    .setContentTitle(getResources().getString(R.string.no_player))
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .setContentIntent(pendingIntent)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setVibrate(null)
                    .setSound(null)
                    .setShowWhen(false)
                    .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                            .setShowActionsInCompactView(1, 2, 3))
                    .addAction(new NotificationCompat.Action(R.drawable.ic_prev, "Previous", getPendingIntent(PREV_TRACK)))
                    .addAction(new NotificationCompat.Action(R.drawable.ic_play, "Play", getPendingIntent(PLAY_TRACK)))
                    .addAction(new NotificationCompat.Action(R.drawable.ic_pause, "Pause", getPendingIntent(PAUSE_TRACK)))
                    .addAction(new NotificationCompat.Action(R.drawable.ic_next, "Next", getPendingIntent(NEXT_TRACK)))
                    .setChannelId(NOTIFICATION_CHANNEL_ID)
                    .build();

            notificationManager.notify(MSG_ID, notificationBuilder.build());
            startForeground(MSG_ID, notification);
        } catch (Exception e) {
            Log.e("LMS", "Failed to create control notification", e);
        }
    }

    private void stopForegroundService() {
        Log.d(MainActivity.TAG, "Stop control service.");
        stopForeground(true);
        stopSelf();
        isRunning = false;
    }
}

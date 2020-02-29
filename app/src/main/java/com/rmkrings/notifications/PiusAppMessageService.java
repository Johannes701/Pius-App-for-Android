package com.rmkrings.notifications;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.rmkrings.activities.R;
import com.rmkrings.activities.ScheduleChangedActivity;
import com.rmkrings.data.vertretungsplan.Vertretungsplan;
import com.rmkrings.helper.AppDefaults;
import com.rmkrings.helper.Cache;
import com.rmkrings.helper.Config;
import com.rmkrings.http.HttpResponseData;
import com.rmkrings.interfaces.HttpResponseCallback;
import com.rmkrings.loader.HttpDeviceTokenSetter;
import com.rmkrings.pius_app_for_android;

import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Objects;

/**
 * Cares about all push notification related stuff. It receives push messages and registers
 * device token in backend.
 */
public class PiusAppMessageService extends FirebaseMessagingService implements HttpResponseCallback {

    /**
     * New token has been created by Android for Pius App. We must register this token in
     * backend for being able to receive push messages.
     * @param token - The token that Android has created.
     */
    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        sendToken(token);
    }

    /**
     * This method is being called whenever a push message is received.
     * @param remoteMessage - The push message that has been received.
     */
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        Intent intent = new Intent(this, ScheduleChangedActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("deltaList", remoteMessage.getData().get("deltaList"));
        intent.putExtra("timestamp", remoteMessage.getData().get("timestamp"));
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        // Show notification.
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "PIUS-APP")
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(getResources().getColor(R.color.colorPiusBlue))
                .setContentTitle(Objects.requireNonNull(remoteMessage.getNotification()).getTitle())
                .setContentText(remoteMessage.getNotification().getBody())
                .setStyle(new NotificationCompat.BigTextStyle().bigText(remoteMessage.getNotification().getBody()))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .setAutoCancel(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            String id = "PIUS-APP";
            CharSequence name = remoteMessage.getNotification().getTitle();
            String description = getString(R.string.title_activity_schedule_changed);
            NotificationChannel mChannel = new NotificationChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT);
            mChannel.setDescription(description);
            mChannel.enableLights(true);
            mChannel.setLightColor(Color.BLUE);
            notificationManager.createNotificationChannel(mChannel);
            notificationManager.notify(0, builder.build());
        } else {
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
            notificationManager.notify(0, builder.build());
        }

        // If schedule data is attached update dashboard data cache and then
        // reload widget.
        if (remoteMessage.getData().containsKey("substitutionSchedule")) {
            try {
                final String data = remoteMessage.getData().get("substitutionSchedule");
                final Vertretungsplan vertretungsplan = new Vertretungsplan(new JSONObject(data));
                final String grade = AppDefaults.getGradeSetting();
                final Cache cache = new Cache();
                cache.store(Config.digestFilename(grade), vertretungsplan.getDigest());
                cache.store(Config.cacheFilename(grade), data);

                // Update widget when new data has been loaded.
                Context context = pius_app_for_android.getAppContext();
                Intent widgetIntent = new Intent(context, DashboardWidgetUpdateService.class);
                context.startService(widgetIntent);
            }
            catch(JSONException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Gets device token of Pius App that is bound to this device and triggers
     * registration in backend. Please note that getting device token other than in iOS
     * is an asynchronous operation and thus a callback is needed which then
     * starts actual registration.
     */
    public void updateDeviceToken() {
        FirebaseInstanceId.getInstance().getInstanceId()
                .addOnCompleteListener(new OnCompleteListener<InstanceIdResult>() {
                    @Override
                    public void onComplete(@NonNull Task<InstanceIdResult> task) {
                        if (!task.isSuccessful()) {
                            return;
                        }

                        // Get new Instance ID token
                        if (task.getResult() != null) {
                            String token = task.getResult().getToken();
                            sendToken(token);
                        }
                    }
                });
    }

    /**
     * Compute SHA1 hash of login credentials. These information is needed in backend for being able
     * to stop pushing when credentials get revoked by Pius Gymnasium.
     * @return - SHA1 of login credentials.
     */
    private String credential() {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");

            // Hash input is concatenation of username and password.
            String hashInput = AppDefaults.getUsername();
            hashInput = hashInput.concat(AppDefaults.getPassword());
            final byte[] b = hashInput.getBytes();

            // Compute digest as byte array. This is signed and cannot be converted into hex
            // string without conversion into BigInteger. Who has designed these crazy Java
            // interfaces? In iOS 13 it's a one shot.
            final byte[] digest = md.digest(b);
            final BigInteger no = new BigInteger(1, digest);
            return no.toString(16);
        }
        catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Registers device token in backend along with some other information that is needed to
     * create push message for substitution schedule changes.
     * @param token - The token that uniquely identifies this app instance.
     */
    private void sendToken(String token) {
        final String grade = AppDefaults.getGradeSetting();

        // Send token only if grade is set and authenticated.
        if (grade.length() > 0 && AppDefaults.isAuthenticated()) {
            String versionName;

            // Get version name. If this throws simply use empty string. An error here should
            // not break app.
            try {
                final Context context = pius_app_for_android.getAppContext();
                final PackageManager pm = context.getPackageManager();

                versionName = pm.getPackageInfo(pius_app_for_android.getAppPackageName(), 0).versionName;
            }
            catch (Exception e) {
                versionName = "";
            }

            final ArrayList<String> courseList = AppDefaults.getCourseList();
            final String credential = this.credential();

            HttpDeviceTokenSetter httpDeviceTokenSetter = new HttpDeviceTokenSetter(token, grade, courseList, versionName, credential);
            httpDeviceTokenSetter.load(this);
        }
    }

    /**
     * Void, we do not care about any outcome of token registration.
     * @param data - Response data, ignored.
     */
    @Override
    public void execute(HttpResponseData data) { /* void */ }

}

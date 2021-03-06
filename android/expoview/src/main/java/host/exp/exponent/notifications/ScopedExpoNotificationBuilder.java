package host.exp.exponent.notifications;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import expo.modules.notifications.notifications.channels.managers.NotificationsChannelManager;
import expo.modules.notifications.notifications.interfaces.NotificationBuilder;
import expo.modules.notifications.notifications.model.NotificationRequest;
import expo.modules.notifications.notifications.presentation.builders.CategoryAwareNotificationBuilder;
import expo.modules.notifications.notifications.service.SharedPreferencesNotificationCategoriesStore;
import host.exp.exponent.Constants;
import host.exp.exponent.ExponentManifest;
import host.exp.exponent.di.NativeModuleDepsProvider;
import host.exp.exponent.kernel.ExperienceId;
import host.exp.exponent.notifications.channels.ScopedNotificationsChannelManager;
import host.exp.exponent.notifications.channels.ScopedNotificationsGroupManager;
import host.exp.exponent.notifications.model.ScopedNotificationRequest;
import host.exp.exponent.storage.ExperienceDBObject;
import host.exp.exponent.storage.ExponentDB;
import host.exp.expoview.R;

public class ScopedExpoNotificationBuilder extends CategoryAwareNotificationBuilder {
  @Inject
  ExponentManifest mExponentManifest;
  @Nullable
  JSONObject manifest;

  @Nullable
  ExperienceId mExperienceId;

  public ScopedExpoNotificationBuilder(Context context, SharedPreferencesNotificationCategoriesStore store) {
    super(context, store);
    NativeModuleDepsProvider.getInstance().inject(ScopedExpoNotificationBuilder.class, this);
  }

  @Override
  public NotificationBuilder setNotification(expo.modules.notifications.notifications.model.Notification notification) {
    super.setNotification(notification);

    // We parse manifest here to have easy access to it from other methods.
    NotificationRequest requester = getNotification().getNotificationRequest();
    if (requester instanceof ScopedNotificationRequest) {
      mExperienceId = ExperienceId.create(((ScopedNotificationRequest) requester).getExperienceIdString());
      ExperienceDBObject experience = ExponentDB.experienceIdToExperienceSync(mExperienceId.get());
      try {
        manifest = new JSONObject(experience.manifest);
      } catch (JSONException e) {
        Log.e("notifications", "Couldn't parse manifest.", e);
        e.printStackTrace();
      }
    }

    return this;
  }

  @NonNull
  @Override
  protected NotificationsChannelManager getNotificationsChannelManager() {
    if (mExperienceId == null) {
      return super.getNotificationsChannelManager();
    }

    return new ScopedNotificationsChannelManager(getContext(), mExperienceId, new ScopedNotificationsGroupManager(getContext(), mExperienceId));
  }

  @Override
  protected int getIcon() {
    return Constants.isStandaloneApp() ? R.drawable.shell_notification_icon : R.drawable.notification_icon;
  }

  @Override
  protected Bitmap getLargeIcon() {
    if (manifest == null) {
      return super.getLargeIcon();
    }

    JSONObject notificationPreferences = manifest.optJSONObject(ExponentManifest.MANIFEST_NOTIFICATION_INFO_KEY);
    String iconUrl = manifest.optString(ExponentManifest.MANIFEST_ICON_URL_KEY);
    if (notificationPreferences != null) {
      iconUrl = notificationPreferences.optString(ExponentManifest.MANIFEST_NOTIFICATION_ICON_URL_KEY);
    }

    return mExponentManifest.loadIconBitmapSync(iconUrl);
  }

  @Nullable
  @Override
  protected Number getColor() {
    // Try to use color defined in notification content
    if (getNotificationContent().getColor() != null) {
      return getNotificationContent().getColor();
    }

    if (manifest == null) {
      return super.getColor();
    }

    return NotificationHelper.getColor(null, manifest, mExponentManifest);
  }
}

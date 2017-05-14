package by.chemerisuk.cordova.firebase;

import java.util.Arrays;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.appinvite.AppInvite;
import com.google.android.gms.appinvite.AppInviteInvitation;
import com.google.android.gms.appinvite.AppInviteInvitationResult;
import com.google.android.gms.appinvite.AppInviteReferral;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import static android.app.Activity.RESULT_OK;
import static com.google.android.gms.appinvite.AppInviteInvitation.IntentBuilder.PlatformMode.PROJECT_PLATFORM_IOS;

public class FirebaseDynamicLinks extends CordovaPlugin implements GoogleApiClient.OnConnectionFailedListener {
  private static final String TAG = "FirebaseDynamicLinks";

  private static final String ACTION_SEND_INVITATION = "sendInvitation";
  private static final String ACTION_GET_INVITATION = "onDynamicLink";
  private static final String ACTION_CONVERT_INVITATION = "convertInvitation";

  private static final int REQUEST_INVITE = 48;

  private GoogleApiClient _googleApiClient;
  private CallbackContext _sendInvitationCallbackContext;
  private CallbackContext _getInvitationCallbackContext;

  @Override
  public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
      if (ACTION_SEND_INVITATION.equals(action)) {
          sendInvitation(callbackContext, args.getJSONObject(0));
          return true;
      } else if (ACTION_GET_INVITATION.equals(action)) {
          getInvitation(callbackContext);
          return true;
      } else if (ACTION_CONVERT_INVITATION.equals(action)) {
          convertInvitation(callbackContext, args.getString(0));
          return true;
      }

      return false;
  }

  @Override
  public void onNewIntent(Intent intent) {
      super.onNewIntent(intent);

      respondWithReferral(intent);
  }

  private void getInvitation(final CallbackContext callbackContext) {
      this._getInvitationCallbackContext = callbackContext;

      cordova.getThreadPool().execute(new Runnable() {
          @Override
          public void run() {
              if (respondWithReferral(cordova.getActivity().getIntent())) return;

              AppInvite.AppInviteApi.getInvitation(getGoogleApiClient(), cordova.getActivity(), false)
                  .setResultCallback(new ResultCallback<AppInviteInvitationResult>() {
                      @Override
                      public void onResult(@NonNull AppInviteInvitationResult result) {
                          if (result.getStatus().isSuccess()) {
                              Intent intent = result.getInvitationIntent();

                              respondWithDeepLink(intent, AppInviteReferral.getDeepLink(intent));
                          } else {
                              _getInvitationCallbackContext.error("Not launched by invitation");
                          }
                      }
              });
          }
      });
  }

  private boolean respondWithReferral(Intent intent) {
    if (AppInviteReferral.hasReferral(intent)) {
          respondWithDeepLink(intent, AppInviteReferral.getDeepLink(intent));

          return true;
      }

      String action = intent.getAction();
      String data = intent.getDataString();

      if (Intent.ACTION_VIEW.equals(action) && data != null) {
          respondWithDeepLink(intent, data);

          return true;
      }

      return false;
  }

  private void respondWithDeepLink(Intent intent, String deepLink) {
      if (_getInvitationCallbackContext == null) return;

      JSONObject response = new JSONObject();
      String invitationId = AppInviteReferral.getInvitationId(intent);

      try {
          if (invitationId != null && invitationId != "") {
              response.put("invitationId", invitationId);
          }

          response.put("deepLink", deepLink);

          PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, response);
          pluginResult.setKeepCallback(true);
          _getInvitationCallbackContext.sendPluginResult(pluginResult);
      } catch (JSONException e) {
          Log.e(TAG, "Fail to handle dynamic link data", e);
      }
  }

  private void sendInvitation(final CallbackContext callbackContext, final JSONObject options) {
    this._sendInvitationCallbackContext = callbackContext; // only used for onActivityResult
    final FirebaseDynamicLinks that = this;

    cordova.getThreadPool().execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              // Only title and message properties are mandatory (and checked in JS API)
              // For all properties, see https://firebase.google.com/docs/invites/android
              String title = options.getString("title");
              String message = options.getString("message");
              String iosClientId = preferences.getString("GoogleIOSClientId", "");
              AppInviteInvitation.IntentBuilder builder = new AppInviteInvitation.IntentBuilder(title).setMessage(message);

              if (options.has("deepLink")) {
                builder.setDeepLink(Uri.parse(options.getString("deepLink")));
              }

              if (options.has("callToActionText")) {
                builder.setCallToActionText(options.getString("callToActionText"));
              }

              if (options.has("customImage")) {
                builder.setCustomImage(Uri.parse(options.getString("customImage")));
              }

              if (options.has("emailSubject")) {
                builder.setEmailSubject(options.getString("emailSubject"));
              }

              if (options.has("emailHtmlContent")) {
                builder.setEmailHtmlContent(options.getString("emailHtmlContent"));
              }

              if (!iosClientId.isEmpty()) {
                builder.setOtherPlatformsTargetApplication(PROJECT_PLATFORM_IOS, iosClientId);
              }

              if (options.has("androidMinimumVersion")) {
                builder.setAndroidMinimumVersionCode(options.getInt("androidMinimumVersion"));
              }

              cordova.startActivityForResult(that, builder.build(), REQUEST_INVITE);

            } catch (JSONException e) {
              _sendInvitationCallbackContext.error(e.getMessage());
            }
          }
        }
    );
  }

  private void convertInvitation(final CallbackContext callbackContext, final String invitationId) {
    cordova.getThreadPool().execute(new Runnable() {
      @Override
      public void run() {
        AppInvite.AppInviteApi.convertInvitation(getGoogleApiClient(), invitationId)
          .setResultCallback(new ResultCallback<Status>() {
            @Override
            public void onResult(@NonNull Status result) {
              if (result.isSuccess()) {
                callbackContext.success();
              } else {
                callbackContext.error("convertInvitation failed with error code " + result.getStatusCode());
              }
            }
          });
      }
    });
  }

  private GoogleApiClient getGoogleApiClient() {
    if (this._googleApiClient == null) {
      this._googleApiClient = new GoogleApiClient.Builder(webView.getContext())
          .addOnConnectionFailedListener(this)
          .addApi(AppInvite.API)
          .build();
    }

    return this._googleApiClient;
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent intent) {
    super.onActivityResult(requestCode, resultCode, intent);

    if (_sendInvitationCallbackContext == null || requestCode != REQUEST_INVITE) {
        return;
    }

    if (resultCode == RESULT_OK) {
        final String[] ids = AppInviteInvitation.getInvitationIds(resultCode, intent);
        try {
            _sendInvitationCallbackContext.success(new JSONArray(Arrays.asList(ids)));
        } catch (Exception e) {
            _sendInvitationCallbackContext.error(e.getMessage());
        }
    } else {
        _sendInvitationCallbackContext.error("Resultcode: " + resultCode);
    }
  }

  @Override
  public void onConnectionFailed(@NonNull ConnectionResult result) {
    this._getInvitationCallbackContext.error(
        "Connection to Google API failed with errorcode: " + result.getErrorCode());
  }
}

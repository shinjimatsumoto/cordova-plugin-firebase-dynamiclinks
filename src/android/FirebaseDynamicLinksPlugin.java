package by.chemerisuk.cordova.firebase;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.util.Log;
import android.text.TextUtils;

import com.google.android.gms.appinvite.AppInvite;
import com.google.android.gms.appinvite.AppInviteInvitation;
import com.google.android.gms.appinvite.AppInviteInvitationResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.appinvite.FirebaseAppInvite;
import com.google.firebase.dynamiclinks.DynamicLink;
import com.google.firebase.dynamiclinks.FirebaseDynamicLinks;
import com.google.firebase.dynamiclinks.PendingDynamicLinkData;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import static android.app.Activity.RESULT_OK;
import static com.google.android.gms.appinvite.AppInviteInvitation.IntentBuilder.PlatformMode.PROJECT_PLATFORM_IOS;

public class FirebaseDynamicLinksPlugin extends CordovaPlugin implements GoogleApiClient.OnConnectionFailedListener {
    private static final String TAG = "FirebaseDynamicLinks";

    private static final String ACTION_SEND_INVITATION = "sendInvitation";
    private static final String ACTION_GET_INVITATION = "onDynamicLink";

    private static final int REQUEST_INVITE = 4343248;

    private String iosClientId;
    private GoogleApiClient _googleApiClient;
    private CallbackContext _sendInvitationCallbackContext;
    private CallbackContext _getInvitationCallbackContext;

    @Override
    protected void pluginInitialize() {
        iosClientId = preferences.getString("REVERSED_CLIENT_ID", "");

        // REVERSED_CLIENT_ID -> CLIENT_ID for iOS

        if (!iosClientId.isEmpty()) {
            List<String> parts = Arrays.asList(iosClientId.split("\\."));

            Collections.reverse(parts);

            iosClientId = TextUtils.join(".", parts);
        }
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if (ACTION_SEND_INVITATION.equals(action)) {
            sendInvitation(callbackContext, args.getJSONObject(0));
            return true;
        } else if (ACTION_GET_INVITATION.equals(action)) {
            getInvitation(callbackContext);
            return true;
        }

        return false;
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if (this._getInvitationCallbackContext != null) {
            respondWithDynamicLink(intent);
        }
    }

    private void getInvitation(final CallbackContext callbackContext) {
        this._getInvitationCallbackContext = callbackContext;

        respondWithDynamicLink(cordova.getActivity().getIntent());
    }

    private void respondWithDynamicLink(final Intent intent) {
        FirebaseDynamicLinks.getInstance().getDynamicLink(intent)
            .addOnSuccessListener(cordova.getActivity(), new OnSuccessListener<PendingDynamicLinkData>() {
                @Override
                public void onSuccess(PendingDynamicLinkData pendingDynamicLinkData) {
                    if (pendingDynamicLinkData != null) {
                        Uri deepLink = pendingDynamicLinkData.getLink();

                        if (deepLink != null) {
                            JSONObject response = new JSONObject();
                            FirebaseAppInvite invite = FirebaseAppInvite.getInvitation(pendingDynamicLinkData);

                            try {
                                if (invite != null) {
                                    response.put("invitationId", invite.getInvitationId());
                                }

                                response.put("deepLink", deepLink);
                                response.put("clickTimestamp", pendingDynamicLinkData.getClickTimestamp());

                                PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, response);
                                pluginResult.setKeepCallback(true);
                                _getInvitationCallbackContext.sendPluginResult(pluginResult);
                            } catch (JSONException e) {
                                Log.e(TAG, "Fail to handle dynamic link data", e);
                            }
                        }
                    }
                }
            });
    }

    private void sendInvitation(final CallbackContext callbackContext, final JSONObject options) {
        this._sendInvitationCallbackContext = callbackContext; // only used for onActivityResult

        cordova.getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String title = options.getString("title");
                    String message = options.getString("message");
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

                    cordova.startActivityForResult(FirebaseDynamicLinksPlugin.this, builder.build(), REQUEST_INVITE);
                } catch (JSONException e) {
                    _sendInvitationCallbackContext.error(e.getMessage());
                }
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

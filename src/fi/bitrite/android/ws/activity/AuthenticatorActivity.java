package fi.bitrite.android.ws.activity;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import fi.bitrite.android.ws.R;
import fi.bitrite.android.ws.WSAndroidApplication;
import fi.bitrite.android.ws.auth.AuthenticationHelper;
import fi.bitrite.android.ws.auth.AuthenticationService;
import fi.bitrite.android.ws.auth.http.HttpAuthenticator;
import roboguice.activity.RoboAccountAuthenticatorActivity;
import roboguice.inject.InjectView;
import roboguice.util.Strings;

/**
 * The activity responsible for getting WarmShowers credentials from the user,
 * verifying them against the WarmShowers web service and storing and storing
 * them on the device using Android's custom account facilities.
 */
public class AuthenticatorActivity extends RoboAccountAuthenticatorActivity {

    public static final String PARAM_USERNAME = "username";
    public static final String KEY_USERID = "userid";
    public static final String PARAM_AUTHTOKEN_TYPE = "authtokenType";
    public static final String PARAM_INITIAL_AUTHENTICATION = "initialAuthentication";

    public static final int RESULT_AUTHENTICATION_FAILED = RESULT_FIRST_USER + 1;
    
    private AccountManager accountManager;

    @InjectView(R.id.editUsername) EditText editUsername;
    @InjectView(R.id.editPassword) EditText editPassword;

    private String username;
    private String password;
    private boolean initialAuthentication;

    private DialogHandler dialogHandler;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.credentials);

        accountManager = AccountManager.get(this);
        dialogHandler = new DialogHandler(this);

        Intent intent = getIntent();
        username = intent.getStringExtra(PARAM_USERNAME);
        initialAuthentication = username == null;
        editUsername.setText(username);
    }

    public void cancel(View view) {
        Intent resultIntent = new Intent();
        resultIntent.putExtra(PARAM_INITIAL_AUTHENTICATION, initialAuthentication);
        setResult(RESULT_CANCELED, resultIntent);
        finish();
    }
    
    @Override
    public void onBackPressed() {
        cancel(null);
    }

    public void applyCredentials(View view) {
        username = editUsername.getText().toString();
        password = editPassword.getText().toString();
        if (!Strings.isEmpty(username) && !Strings.isEmpty(password)) {
            dialogHandler.showDialog(DialogHandler.AUTHENTICATE);
            doAuthentication();
        }
    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle args) {
        return dialogHandler.createDialog(id, getResources().getString(R.string.authenticating));
    }

    public void doAuthentication() {
        new AuthenticationThread(handler).start();
    }

    final Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            dialogHandler.dismiss();

            Object obj = msg.obj;
            Intent resultIntent = new Intent();
            resultIntent.putExtra(PARAM_INITIAL_AUTHENTICATION, initialAuthentication);

            if (obj instanceof Exception) {
                setResult(RESULT_AUTHENTICATION_FAILED, resultIntent);
            } else {
                if (!initialAuthentication) {
                    deleteOldAccount();
                }
                createNewAccount(String.valueOf(msg.arg1));
                setResult(RESULT_OK, resultIntent);
            }

            finish();
        }

        private void createNewAccount(String userId) {
            Account account = new Account(username, AuthenticationService.ACCOUNT_TYPE);
            accountManager.addAccountExplicitly(account, password, null);
            accountManager.setUserData(account, KEY_USERID, userId);
        }

        private void deleteOldAccount() {
            Account oldAccount = AuthenticationHelper.getWarmshowersAccount();
            accountManager.removeAccount(oldAccount, null, null);
        }
    };

    private class AuthenticationThread extends Thread {
        Handler handler;

        public AuthenticationThread(Handler handler) {
            this.handler = handler;
        }

        public void run() {
            Message msg = handler.obtainMessage();

            try {
                HttpAuthenticator authenticator = new HttpAuthenticator();
                int userId = authenticator.authenticate(username, password);
                msg.obj = RESULT_OK;
                msg.arg1 = userId;
            }

            catch (Exception e) {
                Log.e(WSAndroidApplication.TAG, e.getMessage(), e);
                msg.obj = e;
            }

            handler.sendMessage(msg);
        }
    }
}

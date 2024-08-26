/*******************************************************************************
 * Copyright (c) 2012 Humberto Fraga <xisberto@gmail.com>.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * <p/>
 * Contributors:
 * Humberto Fraga <xisberto@gmail.com> - initial API and implementation
 ******************************************************************************/
package net.xisberto.phonetodesktop.ui;

import android.accounts.AccountManager;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.android.gms.common.AccountPicker;
import com.google.android.gms.common.GoogleApiAvailability;
import com.octo.android.robospice.SpiceManager;
import com.octo.android.robospice.persistence.exception.SpiceException;
import com.octo.android.robospice.request.listener.RequestListener;

import net.xisberto.phonetodesktop.Preferences;
import net.xisberto.phonetodesktop.R;
import net.xisberto.phonetodesktop.Utils;
import net.xisberto.phonetodesktop.network.GoogleTasksSpiceService;
import net.xisberto.phonetodesktop.network.TasksListRequest;

public class MainActivity extends AppCompatActivity implements MainFragment.PhoneToDesktopAuthorization {

    private static final int REQUEST_GOOGLE_PLAY_SERVICES = 0;
    private static final int REQUEST_AUTHORIZATION = 1;
    private static final int REQUEST_ACCOUNT_PICKER = 2;

    private static final String TAG_MAIN = "mainFragment";
    
    private Preferences preferences;
    private MainFragment mainFragment;
    private boolean showWelcome;
    private final SpiceManager spiceManager = new SpiceManager(GoogleTasksSpiceService.class);

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.hasExtra(Utils.EXTRA_UPDATING)) {
                updateMainLayout(intent.getBooleanExtra(Utils.EXTRA_UPDATING, false));
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        supportRequestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.activity_main);

        preferences = Preferences.getInstance(this);

        if (savedInstanceState == null) {
            initFragment();
        } else {
            mainFragment = (MainFragment) getSupportFragmentManager().findFragmentByTag(TAG_MAIN);
        }

        checkActionAuth(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        checkActionAuth(intent);
    }

    private void checkActionAuth(Intent intent) {
        if (Utils.ACTION_AUTHENTICATE.equals(intent.getAction())) {
            startAuthorization();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_ACCOUNT_PICKER:
                handleAccountPickerResult(resultCode, data);
                break;
            case REQUEST_GOOGLE_PLAY_SERVICES:
                handleGooglePlayServicesResult(resultCode);
                break;
            case REQUEST_AUTHORIZATION:
                handleAuthorizationResult(resultCode);
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, new IntentFilter(Utils.ACTION_AUTHENTICATE));

        if (!showWelcome && currentFragment instanceof WelcomeFragment) {
            switchToMainFragment();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
        if (spiceManager.isStarted()) {
            spiceManager.shouldStop();
        }
    }

    @Override
    public void startAuthorization() {
        if (isGooglePlayServicesAvailable()) {
            updateMainLayout(true);
            preferences.saveListId(null);
            startActivityForResult(AccountPicker.newChooseAccountIntent(null, null, new String[]{"com.google"}, true, null, null, null, null), REQUEST_ACCOUNT_PICKER);
        }
    }

    private boolean isGooglePlayServicesAvailable() {
        GoogleApiAvailability googleApiAvailability = GoogleApiAvailability.getInstance();
        int statusCode = googleApiAvailability.isGooglePlayServicesAvailable(this);
        if (googleApiAvailability.isUserResolvableError(statusCode)) {
            googleApiAvailability.showErrorDialogFragment(this, statusCode, REQUEST_GOOGLE_PLAY_SERVICES);
            return false;
        }
        return true;
    }

    private void updateMainLayout(boolean updating) {
        if (mainFragment != null) {
            mainFragment.setUpdating(updating);
            if (mainFragment.isVisible()) {
                mainFragment.updateMainLayout();
            }
        }
    }

    private void saveListId() {
        if (!spiceManager.isStarted()) {
            spiceManager.start(this);
        }

        RequestListener<Void> listRequestListener = new RequestListener<Void>() {
            @Override
            public void onRequestFailure(SpiceException spiceException) {
                handleSaveListIdFailure(spiceException);
            }

            @Override
            public void onRequestSuccess(Void aVoid) {
                onSaveListIdSuccess();
            }
        };

        TasksListRequest request = new TasksListRequest(this);
        spiceManager.execute(request, listRequestListener);
    }

    private void initFragment() {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        if (preferences.loadAccountName() == null) {
            showWelcome = true;
            transaction.replace(R.id.main_frame, WelcomeFragment.newInstance());
        } else {
            showWelcome = false;
            mainFragment = MainFragment.newInstance();
            transaction.replace(R.id.main_frame, mainFragment, TAG_MAIN);
        }
        transaction.commit();
    }

    private void handleAccountPickerResult(int resultCode, Intent data) {
        if (resultCode == RESULT_OK && data != null && data.hasExtra(AccountManager.KEY_ACCOUNT_NAME)) {
            String accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
            preferences.saveAccountName(accountName);
            saveListId();
            updateMainLayout(true);
        } else {
            updateMainLayout(false);
        }
    }

    private void handleGooglePlayServicesResult(int resultCode) {
        // Handle result from Google Play Services
    }

    private void handleAuthorizationResult(int resultCode) {
        if (resultCode == RESULT_OK) {
            updateMainLayout(true);
            saveListId();
        } else {
            updateMainLayout(false);
        }
    }

    private void handleSaveListIdFailure(SpiceException spiceException) {
        if (spiceException.getCause() instanceof UserRecoverableAuthException) {
            UserRecoverableAuthException authException = (UserRecoverableAuthException) spiceException.getCause();
            startActivityForResult(authException.getIntent(), REQUEST_AUTHORIZATION);
        } else {
            showRetryDialog();
        }
    }

    private void onSaveListIdSuccess() {
        updateMainLayout(false);
        if (showWelcome) {
            startActivity(new Intent(MainActivity.this, TutorialActivity.class));
            showWelcome = false;
        }
    }

    private void showRetryDialog() {
        RetryDialog dialog = RetryDialog.newInstance(R.string.txt_retry, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    saveListId();
                } else if (which == DialogInterface.BUTTON_NEGATIVE) {
                    updateMainLayout(false);
                }
            }
        });
        dialog.show(getSupportFragmentManager(), "retry_dialog");
    }

    public static class RetryDialog extends DialogFragment {

        private int messageResId;
        private DialogInterface.OnClickListener listener;

        public static RetryDialog newInstance(@StringRes int message, DialogInterface.OnClickListener listener) {
            RetryDialog dialog = new RetryDialog();
            dialog.messageResId = message;
            dialog.listener = listener;
            return dialog;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.app_name)
                    .setMessage(messageResId)
                    .setPositiveButton(R.string.retry, listener)
                    .setNegativeButton(android.R.string.cancel, listener)
                    .create();
        }
    }
}

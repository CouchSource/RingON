package org.couchsource.dring.legal;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.preference.PreferenceManager;
import org.couchsource.dring.application.R;

/**
 * Disclaimer for the app. Copied from: http://www.donnfelker.com/android-a-simple-eula-for-your-android-apps/
 */

public class Disclaimer {

    private final Activity mActivity;

    public Disclaimer(Activity context) {
        mActivity = context;
    }

    private PackageInfo getPackageInfo() {
        PackageInfo pi = null;
        try {
            pi = mActivity.getPackageManager().getPackageInfo(mActivity.getPackageName(), PackageManager.GET_ACTIVITIES);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return pi;
    }

    public void show() {
        PackageInfo versionInfo = getPackageInfo();

        // the eulaKey changes every time you increment the version number in the AndroidManifest.xml
        final String eulaKey = "eula_" + versionInfo.versionCode;
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mActivity);
        boolean hasBeenShown = prefs.getBoolean(eulaKey, false);
        if(!hasBeenShown){

            // Show the Eula
            String title = mActivity.getString(R.string.app_name) + " v" + versionInfo.versionName;

            //Includes the updates as well so users know what changed. 
            String message = mActivity.getString(R.string.app_updates) + "\n\n" + mActivity.getString(R.string.eula);

            AlertDialog.Builder builder = new AlertDialog.Builder(mActivity)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, new Dialog.OnClickListener() {

                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            // Mark this version as read.
                            SharedPreferences.Editor editor = prefs.edit();
                            editor.putBoolean(eulaKey, true);
                            editor.commit();
                            dialogInterface.dismiss();
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, new Dialog.OnClickListener() {

                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // Close the activity as they have declined the EULA
                            mActivity.finish();
                        }

                    });
            builder.create().show();
        }
    }

}

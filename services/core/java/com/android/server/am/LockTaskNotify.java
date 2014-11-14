/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.am;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Message;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.WindowManager;
import android.view.WindowManagerPolicy;
import android.view.accessibility.AccessibilityManager;
import android.widget.Toast;

import com.android.internal.R;
import com.android.internal.policy.PolicyManager;

/**
 *  Helper to manage showing/hiding a image to notify them that they are entering
 *  or exiting lock-to-app mode.
 */
public class LockTaskNotify {
    private static final String TAG = "LockTaskNotify";

    private final Context mContext;
    private final H mHandler;
    private final WindowManagerPolicy mPolicy = PolicyManager.makeNewWindowManager();
    private AccessibilityManager mAccessibilityManager;
    private Toast mLastToast;
    private boolean mHasNavigationBar;

    public LockTaskNotify(Context context) {
        mContext = context;
        mAccessibilityManager = (AccessibilityManager)
                mContext.getSystemService(Context.ACCESSIBILITY_SERVICE);
        mHandler = new H();
        SettingsObserver observer = new SettingsObserver(mHandler);
        observer.observe();
    }

    public void showToast(boolean isLocked) {
        mHandler.obtainMessage(H.SHOW_TOAST, isLocked ? 1 : 0, 0 /* Not used */).sendToTarget();
    }

    public void handleShowToast(boolean isLocked) {
        final int textResId;
        if (isLocked) {
            textResId = R.string.lock_to_app_toast_locked;
        } else if (mAccessibilityManager.isEnabled()) {
            textResId = R.string.lock_to_app_toast_accessible;
        } else {
            textResId = (mPolicy.hasNavigationBar() || mHasNavigationBar)
                    ? R.string.lock_to_app_toast : R.string.lock_to_app_toast_no_navbar;
        }
        if (mLastToast != null) {
            mLastToast.cancel();
        }
        mLastToast = makeAllUserToastAndShow(mContext.getString(textResId));
    }

    public void show(boolean starting) {
        int showString = R.string.lock_to_app_exit;
        if (starting) {
            showString = R.string.lock_to_app_start;
        }
        makeAllUserToastAndShow(mContext.getString(showString));
    }

    private Toast makeAllUserToastAndShow(String text) {
        Toast toast = Toast.makeText(mContext, text, Toast.LENGTH_LONG);
        toast.getWindowParams().privateFlags |=
                WindowManager.LayoutParams.PRIVATE_FLAG_SHOW_FOR_ALL_USERS;
        toast.show();
        return toast;
    }

    private final class H extends Handler {
        private static final int SHOW_TOAST = 3;

        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
                case SHOW_TOAST:
                    handleShowToast(msg.arg1 != 0);
                    break;
            }
        }
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            // Observe all users' changes
            final ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                            Settings.System.NAVBAR_FORCE_ENABLE), false, this,
                    UserHandle.USER_ALL);
            onChange(true);
        }

        @Override public void onChange(boolean selfChange) {
            final ContentResolver resolver = mContext.getContentResolver();
            mHasNavigationBar = Settings.System.getIntForUser(resolver,
                    Settings.System.NAVBAR_FORCE_ENABLE, 0, UserHandle.USER_CURRENT) == 1;
        }
    }
}

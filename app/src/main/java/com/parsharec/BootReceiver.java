package com.parsharec;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Puts the quick-record notification back after a reboot or an app update. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        NotifBar.refresh(c);
    }
}

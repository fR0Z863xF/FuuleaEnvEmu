package com.innofidei.guardsecure.service;

import static android.preference.PreferenceManager.getDefaultSharedPreferences;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

public class EduAppReceiver extends BroadcastReceiver {
    public Context f3275c;

    public void onReceive(Context context, Intent intent){
        /*
        //Context contexta = Context.getApplicationContext();
        Log.i("EduReveiver","Receive BroadCast");
        //CharSequence text = "收到广播";
        //int duration = Toast.LENGTH_SHORT;
        //Toast.makeText(context, text, duration).show();
        SharedPreferences sharedPref = getDefaultSharedPreferences(context.getApplicationContext());
        Log.i("EduReveiver","Read settings....");
        String snnn=sharedPref.getString("sn","0");
        Log.i("EduReveiver",snnn);
        Log.i("EduReveiver","SN read successfully");
        */
        this.f3275c = context;
        StringBuilder sb = new StringBuilder();
        sb.append(intent.getAction());
        if(sb.toString().equals("com.linspirer.edu.getdevicesn")) {
            //Context contexta = Context.getApplicationContext();
            Log.i("EduReveiver","Receive BroadCast: Get sn");
            //CharSequence text = "收到广播";
            //int duration = Toast.LENGTH_SHORT;
            //Toast.makeText(context, text, duration).show();
            SharedPreferences sharedPref = getDefaultSharedPreferences(context.getApplicationContext());
            Log.i("EduReveiver","Read settings....");
            String snnn=sharedPref.getString("sn","Fail_to_get_sn");
            Log.i("EduReveiver",snnn);
            Log.i("EduReveiver","SN read successfully");
            Intent intent7 = new Intent("com.android.laucher3.mdm.obtaindevicesn");
            intent7.setPackage("com.fuulea.test.g");
            intent7.putExtra("device_sn", snnn);
            intent7.putExtra("device_mac", "000000000000");
            intent7.putExtra("mac", "000000000000");
            intent7.putExtra("device_imei", "unknown");
            this.f3275c.sendBroadcast(intent7);
            Log.i("EduReveiver","BroadCast 1 sent");
            Intent intent8 = new Intent("com.android.laucher3.mdm.obtaindevicesn");
            intent8.setPackage("com.fuulea.venus.g");
            intent8.putExtra("device_sn", snnn);
            intent8.putExtra("device_mac", "000000000000");
            intent8.putExtra("mac", "000000000000");
            intent8.putExtra("device_imei", "unknown");
            this.f3275c.sendBroadcast(intent8);
            Log.i("EduReveiver","BroadCast 2 sent");
        }
        else {
            Log.i("EduReveiver","Receive BroadCast: Get user info");
            SharedPreferences sharedPref = getDefaultSharedPreferences(context.getApplicationContext());
            Log.i("EduReveiver","Read settings....");
            String accc=sharedPref.getString("acc","Fail_to_get_acc");
            Log.i("EduReveiver",accc);
            Log.i("EduReveiver","User info read successfully");
            Intent intent7 = new Intent("com.linspirer.edu.return.userinfo");
            intent7.setPackage("com.fuulea.venus.g");
            intent7.putExtra("account", accc);
            intent7.putExtra("nickName", "com.fuulea.test.g");
            intent7.putExtra("className", "000000000000");
            intent7.putExtra("schoolName", "邢台市第五中学");
            this.f3275c.sendBroadcast(intent7);
        }
    }
}

package com.android.launcher3;

import static android.preference.PreferenceManager.getDefaultSharedPreferences;

import androidx.appcompat.app.AppCompatActivity;

import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onStart() {
        super.onStart();
        //setContentView(R.layout.activity_main);
        try {
            SharedPreferences sharedPref = getDefaultSharedPreferences(this.getApplicationContext());
            Log.i("Main", "Setting text.");
            EditText snn = findViewById(R.id.sn);
            EditText accc = findViewById(R.id.acc);
            Log.i("Main", "Setting text..");
            //snn.setSaveEnabled(false);
            snn.setText(sharedPref.getString("sn", ""));
            accc.setText(sharedPref.getString("acc", ""));
            Log.i("Main", "Setting text...");
            Log.i("Main", snn.getText().toString());
            Log.i("Main", accc.getText().toString());

        } catch (Exception e) {
            Log.e("Main", "Fail to set text!");

        }
    }



    public void saveConfig(View view) {
        EditText snn = findViewById(R.id.sn);
        EditText accc = findViewById(R.id.acc);
        //Context contexta = Context.getApplicationContext()
        SharedPreferences sharedPref = getDefaultSharedPreferences(this.getApplicationContext());
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString("sn", snn.getText().toString().toUpperCase(Locale.ROOT));
        editor.putString("acc", accc.getText().toString());
        editor.apply();

        Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show();

    }
    public void startFu(View view) {

        try {
//            String package_name = "com.fuulea.venus.g";
//            PackageManager packageManager = this.getPackageManager();
//            Intent it = packageManager.getLaunchIntentForPackage(package_name);
//            startActivity(it);
//            Log.i("Main","Start fuuleaHD");
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);

            ComponentName componentName = new ComponentName(
                    "com.fuulea.venus",
                    "com.fuulea.venus.MainActivity"
            );

            intent.setComponent(componentName);

            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "请手动启动辅立码课", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
            Log.e("Main","Fail to start fuuleaHD!");

        }
    }
}
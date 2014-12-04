package com.aidy.bottomdrawerlayout;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

public class MainActivity extends Activity {

    private AllDrawerLayout drawerLayout;
    private FrameLayout mainContent;
    private LinearLayout drawerLeft;
    private LinearLayout drawerRight;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i("BottomDrawerLayout", "onCreate()");
        // setContentView(R.layout.main_activity_all);
        // setContentView(R.layout.main_activity_original);
        setContentView(R.layout.main_activity_bottom);

        // drawerLayout = (BottomDrawerLayout) findViewById(R.id.drawer_layout);
        //
        // mainContent = (FrameLayout) findViewById(R.id.main_content);
        // drawerLeft = (LinearLayout) findViewById(R.id.drawer_left);
        // drawerRight = (LinearLayout) findViewById(R.id.drawer_right);
    }

}

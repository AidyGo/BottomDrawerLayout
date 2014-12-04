package com.aidy.bottomdrawerlayout;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

public class MainActivity extends Activity {

	private BottomDrawerLayout bottomDrawerLayout;
	private FrameLayout mainContent;
	private LinearLayout drawerBottom;

	private GestureDetector mDetector;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.i("BottomDrawerLayout", "onCreate()");
		setContentView(R.layout.main_activity_bottom);

		bottomDrawerLayout = (BottomDrawerLayout) findViewById(R.id.drawer_layout);
		drawerBottom = (LinearLayout) findViewById(R.id.drawer_bottom);

		mDetector = new GestureDetector(this, new BottomGestureListener());

		bottomDrawerLayout.setOnTouchListener(new OnTouchListener() {

			@Override
			public boolean onTouch(View v, MotionEvent event) {
				// TODO Auto-generated method stub
				mDetector.onTouchEvent(event);
				return false;
			}
		});

	}

	private class BottomGestureListener extends SimpleOnGestureListener {

		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
			// TODO Auto-generated method stub
			if (e2.getY() < e1.getY()) {
				bottomDrawerLayout.openDrawer(Gravity.BOTTOM);
				return true;
			}

			return super.onFling(e1, e2, velocityX, velocityY);
		}
	}

}

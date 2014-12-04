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

package com.aidy.bottomdrawerlayout;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import android.support.v4.view.AccessibilityDelegateCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.KeyEventCompat;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewGroupCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;

/**
 * DrawerLayout acts as a top-level container for window content that allows for
 * interactive "drawer" views to be pulled out from the edge of the window.
 * 
 * <p>
 * Drawer positioning and layout is controlled using the
 * <code>android:layout_gravity</code> attribute on child views corresponding to
 * which side of the view you want the drawer to emerge from: left or right. (Or
 * start/end on platform versions that support layout direction.)
 * </p>
 * 
 * <p>
 * To use a DrawerLayout, position your primary content view as the first child
 * with a width and height of <code>match_parent</code>. Add drawers as child
 * views after the main content view and set the <code>layout_gravity</code>
 * appropriately. Drawers commonly use <code>match_parent</code> for height with
 * a fixed width.
 * </p>
 * 
 * <p>
 * {@link DrawerListener} can be used to monitor the state and motion of drawer
 * views. Avoid performing expensive operations such as layout during animation
 * as it can cause stuttering; try to perform expensive operations during the
 * {@link #STATE_IDLE} state. {@link SimpleDrawerListener} offers default/no-op
 * implementations of each callback method.
 * </p>
 * 
 * <p>
 * As per the Android Design guide, any drawers positioned to the left/start
 * should always contain content for navigating around the application, whereas
 * any drawers positioned to the right/end should always contain actions to take
 * on the current content. This preserves the same navigation left, actions
 * right structure present in the Action Bar and elsewhere.
 * </p>
 */
public class AllDrawerLayout extends ViewGroup {
	private static final String TAG = "DrawerLayout.Bottom";

	/**
	 * Indicates that any drawers are in an idle, settled state. No animation is
	 * in progress.
	 */
	public static final int STATE_IDLE = ViewDragHelper.STATE_IDLE;

	/**
	 * Indicates that a drawer is currently being dragged by the user.
	 */
	public static final int STATE_DRAGGING = ViewDragHelper.STATE_DRAGGING;

	/**
	 * Indicates that a drawer is in the process of settling to a final
	 * position.
	 */
	public static final int STATE_SETTLING = ViewDragHelper.STATE_SETTLING;

	/**
	 * The drawer is unlocked.
	 */
	public static final int LOCK_MODE_UNLOCKED = 0;

	/**
	 * The drawer is locked closed. The user may not open it, though the app may
	 * open it programmatically.
	 */
	public static final int LOCK_MODE_LOCKED_CLOSED = 1;

	/**
	 * The drawer is locked open. The user may not close it, though the app may
	 * close it programmatically.
	 */
	public static final int LOCK_MODE_LOCKED_OPEN = 2;

	private static final int MIN_DRAWER_MARGIN = 64; // dp

	private static final int DEFAULT_SCRIM_COLOR = 0x99000000;

	/**
	 * Length of time to delay before peeking the drawer.
	 */
	private static final int PEEK_DELAY = 160; // ms

	/**
	 * Minimum velocity that will be detected as a fling
	 */
	private static final int MIN_FLING_VELOCITY = 400; // dips per second

	/**
	 * Experimental feature.
	 */
	private static final boolean ALLOW_EDGE_LOCK = false;

	private static final boolean CHILDREN_DISALLOW_INTERCEPT = true;

	private static final float TOUCH_SLOP_SENSITIVITY = 1.f;

	private static final int[] LAYOUT_ATTRS = new int[] { android.R.attr.layout_gravity };

	private int mMinDrawerMargin;

	private int mScrimColor = DEFAULT_SCRIM_COLOR;
	private float mScrimOpacity;
	private Paint mScrimPaint = new Paint();

	private final ViewDragHelper mLeftDragger;
	private final ViewDragHelper mRightDragger;
	private final ViewDragHelper mTopDragger;
	private final ViewDragHelper mBottomDragger;
	private final ViewDragCallback mLeftCallback;
	private final ViewDragCallback mRightCallback;
	private final ViewDragCallback mTopCallback;
	private final ViewDragCallback mBottomCallback;
	private int mLockModeLeft;
	private int mLockModeRight;
	private int mLockModeTop;
	private int mLockModeBottom;
	private Drawable mShadowLeft;
	private Drawable mShadowRight;
	private Drawable mShadowTop;
	private Drawable mShadowBottom;

	private int mDrawerState;
	private boolean mInLayout;
	private boolean mFirstLayout = true;

	private boolean mDisallowInterceptRequested;
	private boolean mChildrenCanceledTouch;

	private DrawerListener mListener;

	private float mInitialMotionX;
	private float mInitialMotionY;

	/**
	 * Listener for monitoring events about drawers.
	 */
	public interface DrawerListener {
		/**
		 * Called when a drawer's position changes.
		 * 
		 * @param drawerView
		 *            The child view that was moved
		 * @param slideOffset
		 *            The new offset of this drawer within its range, from 0-1
		 */
		public void onDrawerSlide(View drawerView, float slideOffset);

		/**
		 * Called when a drawer has settled in a completely open state. The
		 * drawer is interactive at this point.
		 * 
		 * @param drawerView
		 *            Drawer view that is now open
		 */
		public void onDrawerOpened(View drawerView);

		/**
		 * Called when a drawer has settled in a completely closed state.
		 * 
		 * @param drawerView
		 *            Drawer view that is now closed
		 */
		public void onDrawerClosed(View drawerView);

		/**
		 * Called when the drawer motion state changes. The new state will be
		 * one of {@link #STATE_IDLE}, {@link #STATE_DRAGGING} or
		 * {@link #STATE_SETTLING}.
		 * 
		 * @param newState
		 *            The new drawer motion state
		 */
		public void onDrawerStateChanged(int newState);
	}

	/**
	 * Stub/no-op implementations of all methods of {@link DrawerListener}.
	 * Override this if you only care about a few of the available callback
	 * methods.
	 */
	public static abstract class SimpleDrawerListener implements DrawerListener {
		@Override
		public void onDrawerSlide(View drawerView, float slideOffset) {
		}

		@Override
		public void onDrawerOpened(View drawerView) {
		}

		@Override
		public void onDrawerClosed(View drawerView) {
		}

		@Override
		public void onDrawerStateChanged(int newState) {
		}
	}

	public AllDrawerLayout(Context context) {
		this(context, null);
	}

	public AllDrawerLayout(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public AllDrawerLayout(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);

		final float density = getResources().getDisplayMetrics().density;
		mMinDrawerMargin = (int) (MIN_DRAWER_MARGIN * density + 0.5f);
		final float minVel = MIN_FLING_VELOCITY * density;

		mLeftCallback = new ViewDragCallback(Gravity.LEFT);
		mRightCallback = new ViewDragCallback(Gravity.RIGHT);
		mTopCallback = new ViewDragCallback(Gravity.TOP);
		mBottomCallback = new ViewDragCallback(Gravity.BOTTOM);

		mLeftDragger = ViewDragHelper.create(this, TOUCH_SLOP_SENSITIVITY, mLeftCallback);
		mLeftDragger.setEdgeTrackingEnabled(ViewDragHelper.EDGE_LEFT);
		mLeftDragger.setMinVelocity(minVel);
		mLeftCallback.setDragger(mLeftDragger);

		mRightDragger = ViewDragHelper.create(this, TOUCH_SLOP_SENSITIVITY, mRightCallback);
		mRightDragger.setEdgeTrackingEnabled(ViewDragHelper.EDGE_RIGHT);
		mRightDragger.setMinVelocity(minVel);
		mRightCallback.setDragger(mRightDragger);

		mTopDragger = ViewDragHelper.create(this, TOUCH_SLOP_SENSITIVITY, mTopCallback);
		mTopDragger.setEdgeTrackingEnabled(ViewDragHelper.EDGE_TOP);
		mTopDragger.setMinVelocity(minVel);
		mTopCallback.setDragger(mTopDragger);

		mBottomDragger = ViewDragHelper.create(this, TOUCH_SLOP_SENSITIVITY, mBottomCallback);
		mBottomDragger.setEdgeTrackingEnabled(ViewDragHelper.EDGE_BOTTOM);
		mBottomDragger.setMinVelocity(minVel);
		mBottomCallback.setDragger(mBottomDragger);

		// So that we can catch the back button
		setFocusableInTouchMode(true);

		ViewCompat.setAccessibilityDelegate(this, new AccessibilityDelegate());
		ViewGroupCompat.setMotionEventSplittingEnabled(this, false);
	}

	/**
	 * Set a simple drawable used for the left or right shadow. The drawable
	 * provided must have a nonzero intrinsic width.
	 * 
	 * @param shadowDrawable
	 *            Shadow drawable to use at the edge of a drawer
	 * @param gravity
	 *            Which drawer the shadow should apply to
	 */
	public void setDrawerShadow(Drawable shadowDrawable, int gravity) {
		/*
		 * TODO Someone someday might want to set more complex drawables here.
		 * They're probably nuts, but we might want to consider registering
		 * callbacks, setting states, etc. properly.
		 */

		final int absGravity = GravityCompat.getAbsoluteGravity(gravity, ViewCompat.getLayoutDirection(this));
		if ((absGravity & Gravity.LEFT) == Gravity.LEFT) {
			mShadowLeft = shadowDrawable;
			invalidate();
		}
		if ((absGravity & Gravity.RIGHT) == Gravity.RIGHT) {
			mShadowRight = shadowDrawable;
			invalidate();
		}
		if ((absGravity & Gravity.TOP) == Gravity.TOP) {
			mShadowTop = shadowDrawable;
			invalidate();
		}
		if ((absGravity & Gravity.BOTTOM) == Gravity.BOTTOM) {
			mShadowBottom = shadowDrawable;
			invalidate();
		}
	}

	/**
	 * Set a simple drawable used for the left or right shadow. The drawable
	 * provided must have a nonzero intrinsic width.
	 * 
	 * @param resId
	 *            Resource id of a shadow drawable to use at the edge of a
	 *            drawer
	 * @param gravity
	 *            Which drawer the shadow should apply to
	 */
	public void setDrawerShadow(int resId, int gravity) {
		setDrawerShadow(getResources().getDrawable(resId), gravity);
	}

	/**
	 * Set a color to use for the scrim that obscures primary content while a
	 * drawer is open.
	 * 
	 * @param color
	 *            Color to use in 0xAARRGGBB format.
	 */
	public void setScrimColor(int color) {
		mScrimColor = color;
		invalidate();
	}

	/**
	 * Set a listener to be notified of drawer events.
	 * 
	 * @param listener
	 *            Listener to notify when drawer events occur
	 * @see DrawerListener
	 */
	public void setDrawerListener(DrawerListener listener) {
		mListener = listener;
	}

	/**
	 * Enable or disable interaction with all drawers.
	 * 
	 * <p>
	 * This allows the application to restrict the user's ability to open or
	 * close any drawer within this layout. DrawerLayout will still respond to
	 * calls to {@link #openDrawer(int)}, {@link #closeDrawer(int)} and friends
	 * if a drawer is locked.
	 * </p>
	 * 
	 * <p>
	 * Locking drawers open or closed will implicitly open or close any drawers
	 * as appropriate.
	 * </p>
	 * 
	 * @param lockMode
	 *            The new lock mode for the given drawer. One of
	 *            {@link #LOCK_MODE_UNLOCKED}, {@link #LOCK_MODE_LOCKED_CLOSED}
	 *            or {@link #LOCK_MODE_LOCKED_OPEN}.
	 */
	public void setDrawerLockMode(int lockMode) {
		setDrawerLockMode(lockMode, Gravity.LEFT);
		setDrawerLockMode(lockMode, Gravity.RIGHT);
		setDrawerLockMode(lockMode, Gravity.TOP);
		setDrawerLockMode(lockMode, Gravity.BOTTOM);
	}

	/**
	 * Enable or disable interaction with the given drawer.
	 * 
	 * <p>
	 * This allows the application to restrict the user's ability to open or
	 * close the given drawer. DrawerLayout will still respond to calls to
	 * {@link #openDrawer(int)}, {@link #closeDrawer(int)} and friends if a
	 * drawer is locked.
	 * </p>
	 * 
	 * <p>
	 * Locking a drawer open or closed will implicitly open or close that drawer
	 * as appropriate.
	 * </p>
	 * 
	 * @param lockMode
	 *            The new lock mode for the given drawer. One of
	 *            {@link #LOCK_MODE_UNLOCKED}, {@link #LOCK_MODE_LOCKED_CLOSED}
	 *            or {@link #LOCK_MODE_LOCKED_OPEN}.
	 * @param edgeGravity
	 *            Gravity.LEFT, RIGHT, START or END. Expresses which drawer to
	 *            change the mode for.
	 * 
	 * @see #LOCK_MODE_UNLOCKED
	 * @see #LOCK_MODE_LOCKED_CLOSED
	 * @see #LOCK_MODE_LOCKED_OPEN
	 */
	public void setDrawerLockMode(int lockMode, int edgeGravity) {
		final int absGravity = GravityCompat.getAbsoluteGravity(edgeGravity, ViewCompat.getLayoutDirection(this));
		final ViewDragHelper helper;
		switch (absGravity) {
		case Gravity.LEFT:
			mLockModeLeft = lockMode;
			helper = mLeftDragger;
			break;
		case Gravity.RIGHT:
			mLockModeRight = lockMode;
			helper = mRightDragger;
			break;
		case Gravity.TOP:
			mLockModeTop = lockMode;
			helper = mTopDragger;
			break;
		case Gravity.BOTTOM:
			mLockModeBottom = lockMode;
			helper = mBottomDragger;
			break;
		default:
			helper = mBottomDragger;
			break;
		}
		if (lockMode != LOCK_MODE_UNLOCKED) {
			// Cancel interaction in progress
			helper.cancel();
		}
		switch (lockMode) {
		case LOCK_MODE_LOCKED_OPEN:
			final View toOpen = findDrawerWithGravity(absGravity);
			if (toOpen != null) {
				openDrawer(toOpen);
			}
			break;
		case LOCK_MODE_LOCKED_CLOSED:
			final View toClose = findDrawerWithGravity(absGravity);
			if (toClose != null) {
				closeDrawer(toClose);
			}
			break;
		// default: do nothing
		}
	}

	/**
	 * Enable or disable interaction with the given drawer.
	 * 
	 * <p>
	 * This allows the application to restrict the user's ability to open or
	 * close the given drawer. DrawerLayout will still respond to calls to
	 * {@link #openDrawer(int)}, {@link #closeDrawer(int)} and friends if a
	 * drawer is locked.
	 * </p>
	 * 
	 * <p>
	 * Locking a drawer open or closed will implicitly open or close that drawer
	 * as appropriate.
	 * </p>
	 * 
	 * @param lockMode
	 *            The new lock mode for the given drawer. One of
	 *            {@link #LOCK_MODE_UNLOCKED}, {@link #LOCK_MODE_LOCKED_CLOSED}
	 *            or {@link #LOCK_MODE_LOCKED_OPEN}.
	 * @param drawerView
	 *            The drawer view to change the lock mode for
	 * 
	 * @see #LOCK_MODE_UNLOCKED
	 * @see #LOCK_MODE_LOCKED_CLOSED
	 * @see #LOCK_MODE_LOCKED_OPEN
	 */
	public void setDrawerLockMode(int lockMode, View drawerView) {
		if (!isDrawerView(drawerView)) {
			throw new IllegalArgumentException("View " + drawerView + " is not a " + "drawer with appropriate layout_gravity");
		}
		final int gravity = ((LayoutParams) drawerView.getLayoutParams()).gravity;
		setDrawerLockMode(lockMode, gravity);
	}

	/**
	 * Check the lock mode of the drawer with the given gravity.
	 * 
	 * @param edgeGravity
	 *            Gravity of the drawer to check
	 * @return one of {@link #LOCK_MODE_UNLOCKED},
	 *         {@link #LOCK_MODE_LOCKED_CLOSED} or
	 *         {@link #LOCK_MODE_LOCKED_OPEN}.
	 */
	public int getDrawerLockMode(int edgeGravity) {
		final int absGravity = GravityCompat.getAbsoluteGravity(edgeGravity, ViewCompat.getLayoutDirection(this));
		switch (absGravity) {
		case Gravity.LEFT:
			return mLockModeLeft;
		case Gravity.RIGHT:
			return mLockModeRight;
		case Gravity.TOP:
			return mLockModeTop;
		case Gravity.BOTTOM:
			return mLockModeBottom;
		default:
			return LOCK_MODE_UNLOCKED;
		}
	}

	/**
	 * Check the lock mode of the given drawer view.
	 * 
	 * @param drawerView
	 *            Drawer view to check lock mode
	 * @return one of {@link #LOCK_MODE_UNLOCKED},
	 *         {@link #LOCK_MODE_LOCKED_CLOSED} or
	 *         {@link #LOCK_MODE_LOCKED_OPEN}.
	 */
	public int getDrawerLockMode(View drawerView) {
		final int absGravity = getDrawerViewAbsoluteGravity(drawerView);
		switch (absGravity) {
		case Gravity.LEFT:
			return mLockModeLeft;
		case Gravity.RIGHT:
			return mLockModeRight;
		case Gravity.TOP:
			return mLockModeTop;
		case Gravity.BOTTOM:
			return mLockModeBottom;
		default:
			return LOCK_MODE_UNLOCKED;
		}
	}

	/**
	 * Resolve the shared state of all drawers from the component
	 * ViewDragHelpers. Should be called whenever a ViewDragHelper's state
	 * changes.
	 */
	void updateDrawerState(int forGravity, int activeState, View activeDrawer) {
		final int leftState = mLeftDragger.getViewDragState();
		final int rightState = mRightDragger.getViewDragState();
		final int topState = mTopDragger.getViewDragState();
		final int bottomState = mBottomDragger.getViewDragState();

		final int state;
		if (leftState == STATE_DRAGGING || rightState == STATE_DRAGGING || topState == STATE_DRAGGING || bottomState == STATE_DRAGGING) {
			state = STATE_DRAGGING;
		} else if (leftState == STATE_SETTLING || rightState == STATE_SETTLING || topState == STATE_SETTLING
				|| bottomState == STATE_SETTLING) {
			state = STATE_SETTLING;
		} else {
			state = STATE_IDLE;
		}

		if (activeDrawer != null && activeState == STATE_IDLE) {
			final LayoutParams lp = (LayoutParams) activeDrawer.getLayoutParams();
			if (lp.onScreen == 0) {
				dispatchOnDrawerClosed(activeDrawer);
			} else if (lp.onScreen == 1) {
				dispatchOnDrawerOpened(activeDrawer);
			}
		}
		if (state != mDrawerState) {
			mDrawerState = state;
			if (mListener != null) {
				mListener.onDrawerStateChanged(state);
			}
		}
	}

	void dispatchOnDrawerClosed(View drawerView) {
		final LayoutParams lp = (LayoutParams) drawerView.getLayoutParams();
		if (lp.knownOpen) {
			lp.knownOpen = false;
			if (mListener != null) {
				mListener.onDrawerClosed(drawerView);
			}
			sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
		}
	}

	void dispatchOnDrawerOpened(View drawerView) {
		final LayoutParams lp = (LayoutParams) drawerView.getLayoutParams();
		if (!lp.knownOpen) {
			lp.knownOpen = true;
			if (mListener != null) {
				mListener.onDrawerOpened(drawerView);
			}
			drawerView.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
		}
	}

	void dispatchOnDrawerSlide(View drawerView, float slideOffset) {
		if (mListener != null) {
			mListener.onDrawerSlide(drawerView, slideOffset);
		}
	}

	void setDrawerViewOffset(View drawerView, float slideOffset) {
		final LayoutParams lp = (LayoutParams) drawerView.getLayoutParams();
		if (slideOffset == lp.onScreen) {
			return;
		}

		lp.onScreen = slideOffset;
		dispatchOnDrawerSlide(drawerView, slideOffset);
	}

	float getDrawerViewOffset(View drawerView) {
		return ((LayoutParams) drawerView.getLayoutParams()).onScreen;
	}

	/**
	 * @return the absolute gravity of the child drawerView, resolved according
	 *         to the current layout direction
	 */
	int getDrawerViewAbsoluteGravity(View drawerView) {
		final int gravity = ((LayoutParams) drawerView.getLayoutParams()).gravity;
		return GravityCompat.getAbsoluteGravity(gravity, ViewCompat.getLayoutDirection(this));
	}

	boolean checkDrawerViewAbsoluteGravity(View drawerView, int checkFor) {
		final int absGravity = getDrawerViewAbsoluteGravity(drawerView);
		return (absGravity & checkFor) == checkFor;
	}

	View findOpenDrawer() {
		final int childCount = getChildCount();
		for (int i = 0; i < childCount; i++) {
			final View child = getChildAt(i);
			if (((LayoutParams) child.getLayoutParams()).knownOpen) {
				return child;
			}
		}
		return null;
	}

	void moveDrawerToOffset(View drawerView, float slideOffset) {
		Log.i(TAG, "BottomDrawerLayout -- moveDrawerToOffset() -- slideOffset = " + slideOffset);
		final int absGravity = getDrawerViewAbsoluteGravity(drawerView);
		final float oldOffset = getDrawerViewOffset(drawerView);
		final int width = drawerView.getWidth();
		final int height = drawerView.getHeight();
		final int xoldPos = (int) (width * oldOffset);
		final int xnewPos = (int) (width * slideOffset);
		final int yoldPos = (int) (height * oldOffset);
		final int ynewPos = (int) (height * slideOffset);
		int dx = xnewPos - xoldPos;
		int dy = ynewPos - yoldPos;

		if (absGravity == Gravity.LEFT || absGravity == Gravity.RIGHT) {
			drawerView.offsetLeftAndRight(checkDrawerViewAbsoluteGravity(drawerView, Gravity.LEFT) ? dx : -dx);
		}
		if (absGravity == Gravity.TOP || absGravity == Gravity.BOTTOM) {
			drawerView.offsetTopAndBottom(checkDrawerViewAbsoluteGravity(drawerView, Gravity.TOP) ? dy : -dy);
		}
		setDrawerViewOffset(drawerView, slideOffset);
	}

	/**
	 * @param gravity
	 *            the gravity of the child to return. If specified as a relative
	 *            value, it will be resolved according to the current layout
	 *            direction.
	 * @return the drawer with the specified gravity
	 */
	View findDrawerWithGravity(int gravity) {
		final int absHorizGravity = GravityCompat.getAbsoluteGravity(gravity, ViewCompat.getLayoutDirection(this))
				& Gravity.HORIZONTAL_GRAVITY_MASK;
		final int childCount = getChildCount();
		for (int i = 0; i < childCount; i++) {
			final View child = getChildAt(i);
			final int childAbsGravity = getDrawerViewAbsoluteGravity(child);
			if ((childAbsGravity & Gravity.HORIZONTAL_GRAVITY_MASK) == absHorizGravity) {
				return child;
			}
		}
		return null;
	}

	/**
	 * Simple gravity to string - only supports LEFT and RIGHT for debugging
	 * output.
	 * 
	 * @param gravity
	 *            Absolute gravity value
	 * @return LEFT or RIGHT as appropriate, or a hex string
	 */
	static String gravityToString(int gravity) {
		if ((gravity & Gravity.LEFT) == Gravity.LEFT) {
			return "LEFT";
		}
		if ((gravity & Gravity.RIGHT) == Gravity.RIGHT) {
			return "RIGHT";
		}
		if ((gravity & Gravity.TOP) == Gravity.TOP) {
			return "TOP";
		}
		if ((gravity & Gravity.BOTTOM) == Gravity.BOTTOM) {
			return "BOTTOM";
		}
		return Integer.toHexString(gravity);
	}

	@Override
	protected void onDetachedFromWindow() {
		super.onDetachedFromWindow();
		mFirstLayout = true;
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		mFirstLayout = true;
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		Log.i(TAG, "onMeasure -- widthMeasureSpec = " + widthMeasureSpec + " -- heightMeasureSpec = " + heightMeasureSpec);
		int widthMode = MeasureSpec.getMode(widthMeasureSpec);
		int heightMode = MeasureSpec.getMode(heightMeasureSpec);
		int widthSize = MeasureSpec.getSize(widthMeasureSpec);
		int heightSize = MeasureSpec.getSize(heightMeasureSpec);

		if (widthMode != MeasureSpec.EXACTLY || heightMode != MeasureSpec.EXACTLY) {
			if (isInEditMode()) {
				if (widthMode == MeasureSpec.AT_MOST) {
					widthMode = MeasureSpec.EXACTLY;
				} else if (widthMode == MeasureSpec.UNSPECIFIED) {
					widthMode = MeasureSpec.EXACTLY;
					widthSize = 300;
				}
				if (heightMode == MeasureSpec.AT_MOST) {
					heightMode = MeasureSpec.EXACTLY;
				} else if (heightMode == MeasureSpec.UNSPECIFIED) {
					heightMode = MeasureSpec.EXACTLY;
					heightSize = 300;
				}
			} else {
				throw new IllegalArgumentException("DrawerLayout must be measured with MeasureSpec.EXACTLY.");
			}
		}

		setMeasuredDimension(widthSize, heightSize);

		// Gravity value for each drawer we've seen. Only one of each permitted.
		int foundDrawers = 0;
		final int childCount = getChildCount();
		for (int i = 0; i < childCount; i++) {
			final View child = getChildAt(i);

			if (child.getVisibility() == GONE) {
				continue;
			}

			final LayoutParams lp = (LayoutParams) child.getLayoutParams();

			if (isContentView(child)) {
				// Content views get measured at exactly the layout's size.
				final int contentWidthSpec = MeasureSpec.makeMeasureSpec(widthSize - lp.leftMargin - lp.rightMargin, MeasureSpec.EXACTLY);
				final int contentHeightSpec = MeasureSpec.makeMeasureSpec(heightSize - lp.topMargin - lp.bottomMargin, MeasureSpec.EXACTLY);
				child.measure(contentWidthSpec, contentHeightSpec);
			} else if (isDrawerView(child)) {
				final int childGravity = getDrawerViewAbsoluteGravity(child) & Gravity.HORIZONTAL_GRAVITY_MASK;
				if ((foundDrawers & childGravity) != 0) {
					throw new IllegalStateException("Child drawer has absolute gravity " + gravityToString(childGravity) + " but this "
							+ TAG + " already has a " + "drawer view along that edge");
				}
				final int drawerWidthSpec = getChildMeasureSpec(widthMeasureSpec, mMinDrawerMargin + lp.leftMargin + lp.rightMargin,
						lp.width);
				final int drawerHeightSpec = getChildMeasureSpec(heightMeasureSpec, mMinDrawerMargin + lp.topMargin + lp.bottomMargin,
						lp.height);
				child.measure(drawerWidthSpec, drawerHeightSpec);
			} else {
				throw new IllegalStateException("Child " + child + " at index " + i
						+ " does not have a valid layout_gravity - must be Gravity.LEFT, " + "Gravity.RIGHT or Gravity.NO_GRAVITY");
			}
		}
	}

	/**
	 * 12-03 22:59:19.686: I/BottomDrawerLayout(12480): onLayout() -- left = 0
	 * -- top = 0 -- right = 1080 -- b = 1675 12-03 22:59:19.686:
	 * I/BottomDrawerLayout(12480): onLayout() -- childWidth = 750 --
	 * childHeight = 1675 -- lp.onScreen = 0.0 12-03 22:59:19.686:
	 * I/BottomDrawerLayout(12480): onLayout() -- childLeft = -750 -- newOffset
	 * = 0.0 12-03 22:59:19.686: I/BottomDrawerLayout(12480): onLayout() --
	 * childWidth = 750 -- childHeight = 1675 -- lp.onScreen = 0.0 12-03
	 * 22:59:19.686: I/BottomDrawerLayout(12480): onLayout() -- childLeft = 1080
	 * -- newOffset = 0.0
	 */
	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		Log.i(TAG, "onLayout() -- left = " + l + " -- top = " + t + " -- right = " + r + " -- b = " + b);
		mInLayout = true;
		final int width = r - l;// 整个容器的宽度
		final int height = b - t;// 整个容器的高度
		final int childCount = getChildCount();
		for (int i = 0; i < childCount; i++) {
			final View child = getChildAt(i);
			if (child.getVisibility() == GONE) {
				continue;
			}
			final LayoutParams lp = (LayoutParams) child.getLayoutParams();
			if (isContentView(child)) {
				child.layout(lp.leftMargin, lp.topMargin, lp.leftMargin + child.getMeasuredWidth(),
						lp.topMargin + child.getMeasuredHeight());
			} else {
				// 子view的宽和高
				final int childWidth = child.getMeasuredWidth();
				final int childHeight = child.getMeasuredHeight();
				// Log.i(TAG, "onLayout() -- childWidth = " + childWidth +
				// " -- childHeight = " + childHeight
				// + " -- lp.onScreen = " + lp.onScreen);
				int childLeft = 0;// 橫軸起点
				int childTop = 0;// 竖轴起点
				float newOffset = 0;// 滑动的起点

				switch (getDrawerViewAbsoluteGravity(child)) {
				case Gravity.LEFT:
					if (checkDrawerViewAbsoluteGravity(child, Gravity.LEFT)) {
						// Log.i(TAG, "onLayout() -- 1");
						childLeft = -childWidth + (int) (childWidth * lp.onScreen);
						newOffset = (float) (childWidth + childLeft) / childWidth;// 横轴方向
					}
					break;
				case Gravity.RIGHT:
					if (checkDrawerViewAbsoluteGravity(child, Gravity.RIGHT)) {
						// Log.i(TAG, "onLayout() -- 2");
						childLeft = width - (int) (childWidth * lp.onScreen);
						newOffset = (float) (width - childLeft) / childWidth;// 横轴方向
					}
					break;
				case Gravity.TOP:
					if (checkDrawerViewAbsoluteGravity(child, Gravity.TOP)) {
						// Log.i(TAG, "onLayout() -- 3");
						childTop = -childHeight + (int) (childHeight * lp.onScreen);
						newOffset = (float) (childHeight + childTop) / childHeight;// 竖轴方向
					}
					break;
				case Gravity.BOTTOM:
					if (checkDrawerViewAbsoluteGravity(child, Gravity.BOTTOM)) {
						// Log.i(TAG, "onLayout() -- 4");
						childTop = height - (int) (childHeight * lp.onScreen);
						newOffset = (float) (height - childTop) / childHeight;// 竖轴方向
					}
					break;
				default:
					childTop = height - (int) (childHeight * lp.onScreen);
					newOffset = (float) (height - childTop) / childHeight;// 竖轴方向
					break;
				}
				// /////////////////////////////////////////
				// Log.i(TAG, "onLayout() -- childLeft = " + childLeft +
				// " -- newOffset = " + newOffset);
				final boolean changeOffset = newOffset != lp.onScreen;
				final int vgrav = lp.gravity & Gravity.VERTICAL_GRAVITY_MASK;
				switch (vgrav) {
				// case Gravity.TOP: {
				// Log.i(TAG, "onLayout() -- Gravity.TOP");
				// child.layout(childLeft, lp.topMargin, childLeft + childWidth,
				// lp.topMargin + childHeight);
				// break;
				// }
				// case Gravity.BOTTOM: {
				// Log.i(TAG, "onLayout() -- Gravity.BOTTOM");
				// child.layout(childLeft, height - lp.bottomMargin -
				// child.getMeasuredHeight(), childLeft
				// + childWidth, height - lp.bottomMargin);
				// break;
				// }
				case Gravity.CENTER_VERTICAL: {
					// Log.i(TAG, "onLayout() -- Gravity.CENTER_VERTICAL");
					int childTop_cv = (height - childHeight) / 2;

					// Offset for margins. If things don't fit right because of
					// bad measurement before, oh well.
					if (childTop_cv < lp.topMargin) {
						childTop_cv = lp.topMargin;
					} else if (childTop_cv + childHeight > height - lp.bottomMargin) {
						childTop_cv = height - lp.bottomMargin - childHeight;
					}
					child.layout(childLeft, childTop_cv, childLeft + childWidth, childTop_cv + childHeight);
					break;
				}
				}

				// /////////////////////////////////////////

				if (changeOffset) {
					setDrawerViewOffset(child, newOffset);
				}

				final int newVisibility = lp.onScreen > 0 ? VISIBLE : INVISIBLE;
				if (child.getVisibility() != newVisibility) {
					child.setVisibility(newVisibility);
				}
			}
		}
		mInLayout = false;
		mFirstLayout = false;
	}

	@Override
	public void requestLayout() {
		if (!mInLayout) {
			super.requestLayout();
		}
	}

	@Override
	public void computeScroll() {
		Log.i(TAG, "computeScroll()");
		final int childCount = getChildCount();
		float scrimOpacity = 0;
		for (int i = 0; i < childCount; i++) {
			final float onscreen = ((LayoutParams) getChildAt(i).getLayoutParams()).onScreen;
			Log.i(TAG, "computeScroll() -- 1");
			scrimOpacity = Math.max(scrimOpacity, onscreen);
		}
		mScrimOpacity = scrimOpacity;

		// "|" used on purpose; both need to run.
		if (mLeftDragger.continueSettling(true) | mRightDragger.continueSettling(true) | mTopDragger.continueSettling(true)
				| mBottomDragger.continueSettling(true)) {
			Log.i(TAG, "computeScroll() -- 2");
			ViewCompat.postInvalidateOnAnimation(this);
		}
	}

	private static boolean hasOpaqueBackground(View v) {
		final Drawable bg = v.getBackground();
		if (bg != null) {
			return bg.getOpacity() == PixelFormat.OPAQUE;
		}
		return false;
	}

	@Override
	protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
		Log.i(TAG, "drawChild()");
		final int height = getHeight();
		final boolean drawingContent = isContentView(child);
		int clipLeft = 0, clipRight = getWidth();
		int clipTop = 0, clipBottom = getHeight();

		final int restoreCount = canvas.save();
		if (drawingContent) {
			final int childCount = getChildCount();
			for (int i = 0; i < childCount; i++) {
				final View v = getChildAt(i);
				if (v == child || v.getVisibility() != VISIBLE || !hasOpaqueBackground(v) || !isDrawerView(v) || v.getHeight() < height) {
					Log.i(TAG, "drawChild() -- 0");
					continue;
				}
				switch (getDrawerViewAbsoluteGravity(v)) {
				case Gravity.LEFT:
					Log.i(TAG, "drawChild() -- 1");
					if (checkDrawerViewAbsoluteGravity(v, Gravity.LEFT)) {
						final int vright = v.getRight();
						if (vright > clipLeft)
							clipLeft = vright;
					}
					break;
				case Gravity.RIGHT:
					Log.i(TAG, "drawChild() -- 2");
					if (checkDrawerViewAbsoluteGravity(v, Gravity.RIGHT)) {
						final int vleft = v.getLeft();
						if (vleft < clipRight)
							clipRight = vleft;
					}
					break;
				case Gravity.TOP:
					Log.i(TAG, "drawChild() -- 3");
					if (checkDrawerViewAbsoluteGravity(v, Gravity.TOP)) {
						final int vbottom = v.getBottom();
						if (vbottom > clipTop) {
							clipTop = vbottom;
						}
					}
					break;
				case Gravity.BOTTOM:
					Log.i(TAG, "drawChild() -- 4");
					if (checkDrawerViewAbsoluteGravity(v, Gravity.BOTTOM)) {
						final int vtop = v.getTop();
						if (vtop < clipBottom) {
							clipBottom = vtop;
						}
					}
					break;
				default:
					Log.i(TAG, "drawChild() -- 5");
					final int vtop = v.getTop();
					if (vtop < clipBottom) {
						clipBottom = vtop;
					}
					break;
				}
			}
			canvas.clipRect(clipLeft, clipTop, clipRight, clipBottom);
		}
		final boolean result = super.drawChild(canvas, child, drawingTime);
		canvas.restoreToCount(restoreCount);

		if (mScrimOpacity > 0 && drawingContent) {
			Log.i(TAG, "drawChild() -- drawingContent");
			final int baseAlpha = (mScrimColor & 0xff000000) >>> 24;
			final int imag = (int) (baseAlpha * mScrimOpacity);
			final int color = imag << 24 | (mScrimColor & 0xffffff);
			mScrimPaint.setColor(color);
			canvas.drawRect(clipLeft, clipTop, clipRight, clipBottom, mScrimPaint);
		} else if (mShadowLeft != null && checkDrawerViewAbsoluteGravity(child, Gravity.LEFT)) {
			Log.i(TAG, "drawChild() -- LEFT");
			final int shadowWidth = mShadowLeft.getIntrinsicWidth();
			final int childRight = child.getRight();
			final int drawerPeekDistance = mLeftDragger.getEdgeSize();
			final float alpha = Math.max(0, Math.min((float) childRight / drawerPeekDistance, 1.f));
			mShadowLeft.setBounds(childRight, child.getTop(), childRight + shadowWidth, child.getBottom());
			mShadowLeft.setAlpha((int) (0xff * alpha));
			mShadowLeft.draw(canvas);
		} else if (mShadowRight != null && checkDrawerViewAbsoluteGravity(child, Gravity.RIGHT)) {
			Log.i(TAG, "drawChild() -- Gravity.RIGHT");
			final int shadowWidth = mShadowRight.getIntrinsicWidth();
			final int childLeft = child.getLeft();
			final int showing = getWidth() - childLeft;
			final int drawerPeekDistance = mRightDragger.getEdgeSize();
			final float alpha = Math.max(0, Math.min((float) showing / drawerPeekDistance, 1.f));
			mShadowRight.setBounds(childLeft - shadowWidth, child.getTop(), childLeft, child.getBottom());
			mShadowRight.setAlpha((int) (0xff * alpha));
			mShadowRight.draw(canvas);
		} else if (mShadowTop != null && checkDrawerViewAbsoluteGravity(child, Gravity.TOP)) {
			Log.i(TAG, "drawChild() -- Gravity.TOP");
			final int shadowHeight = mShadowTop.getIntrinsicHeight();
			final int childBottom = child.getBottom();
			final int drawerPeekDistance = mTopDragger.getEdgeSize();
			final float alpha = Math.max(0, Math.min((float) childBottom / drawerPeekDistance, 1.f));
			mShadowTop.setBounds(child.getLeft(), childBottom, child.getRight(), childBottom + shadowHeight);
			mShadowTop.setAlpha((int) (0xff * alpha));
			mShadowTop.draw(canvas);
		} else if (mShadowBottom != null && checkDrawerViewAbsoluteGravity(child, Gravity.BOTTOM)) {
			Log.i(TAG, "drawChild() -- Gravity.BOTTOM");
			final int shadowHeight = mShadowBottom.getIntrinsicWidth();
			final int childTop = child.getTop();
			final int showing = getHeight() - childTop;
			final int drawerPeekDistance = mBottomDragger.getEdgeSize();
			final float alpha = Math.max(0, Math.min((float) showing / drawerPeekDistance, 1.f));
			mShadowRight.setBounds(child.getLeft(), childTop - shadowHeight, child.getRight(), childTop);
			mShadowRight.setAlpha((int) (0xff * alpha));
			mShadowRight.draw(canvas);
		}
		return result;
	}

	boolean isContentView(View child) {
		return ((LayoutParams) child.getLayoutParams()).gravity == Gravity.NO_GRAVITY;
	}

	boolean isDrawerView(View child) {
		final int gravity = ((LayoutParams) child.getLayoutParams()).gravity;
		final int absGravity = GravityCompat.getAbsoluteGravity(gravity, ViewCompat.getLayoutDirection(child));
		return (absGravity & (Gravity.LEFT | Gravity.RIGHT | Gravity.TOP | Gravity.BOTTOM)) != 0;
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		Log.i(TAG, "onInterceptTouchEvent()");
		final int action = MotionEventCompat.getActionMasked(ev);

		// "|" used deliberately here; both methods should be invoked.
		final boolean interceptForDrag = mLeftDragger.shouldInterceptTouchEvent(ev) | mRightDragger.shouldInterceptTouchEvent(ev)
				| mTopDragger.shouldInterceptTouchEvent(ev) | mBottomDragger.shouldInterceptTouchEvent(ev);
		boolean interceptForTap = false;
		switch (action) {
		case MotionEvent.ACTION_DOWN: {
			Log.i(TAG, "onInterceptTouchEvent() -- ACTION_DOWN");
			final float x = ev.getX();
			final float y = ev.getY();
			mInitialMotionX = x;
			mInitialMotionY = y;
			if (mScrimOpacity > 0 && isContentView(mLeftDragger.findTopChildUnder((int) x, (int) y))) {
				interceptForTap = true;
			}
			mDisallowInterceptRequested = false;
			mChildrenCanceledTouch = false;
			break;
		}

		case MotionEvent.ACTION_MOVE: {
			Log.i(TAG, "onInterceptTouchEvent() -- ACTION_MOVE");
			// If we cross the touch slop, don't perform the delayed peek for an
			// edge touch.
			if (mLeftDragger.checkTouchSlop(ViewDragHelper.DIRECTION_ALL)) {
				Log.i(TAG, "onInterceptTouchEvent() -- ACTION_MOVE -- 2");
				mLeftCallback.removeCallbacks();
				mRightCallback.removeCallbacks();
				mTopCallback.removeCallbacks();
				mBottomCallback.removeCallbacks();
			}
			break;
		}

		case MotionEvent.ACTION_CANCEL:
		case MotionEvent.ACTION_UP: {
			Log.i(TAG, "onInterceptTouchEvent() -- ACTION_CANCEL | ACTION_UP");
			closeDrawers(true);
			mDisallowInterceptRequested = false;
			mChildrenCanceledTouch = false;
		}
		}

		boolean result = interceptForDrag || interceptForTap || hasPeekingDrawer() || mChildrenCanceledTouch;
		Log.i(TAG, "onInterceptTouchEvent() -- result = " + result);
		return result;
	}

	@Override
	public boolean onTouchEvent(MotionEvent ev) {
		Log.i(TAG, "onTouchEvent()");
		final int action = ev.getAction();
		boolean wantTouchEvents = true;
		try {

			mLeftDragger.processTouchEvent(ev);
			mRightDragger.processTouchEvent(ev);
			mTopDragger.processTouchEvent(ev);
			mBottomDragger.processTouchEvent(ev);

			switch (action & MotionEventCompat.ACTION_MASK) {
			case MotionEvent.ACTION_DOWN: {
				Log.i(TAG, "onTouchEvent() -- ACTION_DOWN");
				final float x = ev.getX();
				final float y = ev.getY();
				mInitialMotionX = x;
				mInitialMotionY = y;
				mDisallowInterceptRequested = false;
				mChildrenCanceledTouch = false;
				break;
			}

			case MotionEvent.ACTION_UP: {
				Log.i(TAG, "onTouchEvent() -- ACTION_UP");
				final float x = ev.getX();
				final float y = ev.getY();
				boolean peekingOnly = true;
				final View touchedView = mLeftDragger.findTopChildUnder((int) x, (int) y);
				if (touchedView != null && isContentView(touchedView)) {
					final float dx = x - mInitialMotionX;
					final float dy = y - mInitialMotionY;
					final int slop = mLeftDragger.getTouchSlop();
					if (dx * dx + dy * dy < slop * slop) {
						// Taps close a dimmed open drawer but only if it isn't
						// locked open.
						final View openDrawer = findOpenDrawer();
						if (openDrawer != null) {
							peekingOnly = getDrawerLockMode(openDrawer) == LOCK_MODE_LOCKED_OPEN;
						}
					}
				}
				closeDrawers(peekingOnly);
				mDisallowInterceptRequested = false;
				break;
			}

			case MotionEvent.ACTION_CANCEL: {
				Log.i(TAG, "onTouchEvent() -- ACTION_CANCEL");
				closeDrawers(true);
				mDisallowInterceptRequested = false;
				mChildrenCanceledTouch = false;
				break;
			}
			}
		} catch (IllegalArgumentException e) {
			// TODO: handle exception
		}
		boolean result = wantTouchEvents;
		Log.i(TAG, "onTouchEvent() -- result = " + result);
		return result;
	}

	public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
		if (CHILDREN_DISALLOW_INTERCEPT
				|| (!mLeftDragger.isEdgeTouched(ViewDragHelper.EDGE_LEFT) && !mRightDragger.isEdgeTouched(ViewDragHelper.EDGE_RIGHT)
						&& !mTopDragger.isEdgeTouched(ViewDragHelper.EDGE_TOP) && !mBottomDragger.isEdgeTouched(ViewDragHelper.EDGE_BOTTOM))) {
			// If we have an edge touch we want to skip this and track it for
			// later instead.
			super.requestDisallowInterceptTouchEvent(disallowIntercept);
		}
		mDisallowInterceptRequested = disallowIntercept;
		if (disallowIntercept) {
			closeDrawers(true);
		}
	}

	/**
	 * Close all currently open drawer views by animating them out of view.
	 */
	public void closeDrawers() {
		closeDrawers(false);
	}

	void closeDrawers(boolean peekingOnly) {
		boolean needsInvalidate = false;
		final int childCount = getChildCount();
		for (int i = 0; i < childCount; i++) {
			final View child = getChildAt(i);
			final LayoutParams lp = (LayoutParams) child.getLayoutParams();

			if (!isDrawerView(child) || (peekingOnly && !lp.isPeeking)) {
				continue;
			}

			final int childWidth = child.getWidth();
			switch (getDrawerViewAbsoluteGravity(child)) {
			case Gravity.LEFT:
				if (checkDrawerViewAbsoluteGravity(child, Gravity.LEFT))
					needsInvalidate |= mLeftDragger.smoothSlideViewTo(child, -childWidth, child.getTop());
				break;
			case Gravity.RIGHT:
				if (checkDrawerViewAbsoluteGravity(child, Gravity.RIGHT))
					needsInvalidate |= mRightDragger.smoothSlideViewTo(child, getWidth(), child.getTop());
				break;
			case Gravity.TOP:
				if (checkDrawerViewAbsoluteGravity(child, Gravity.TOP))
					needsInvalidate |= mTopDragger.smoothSlideViewTo(child, getWidth(), child.getTop());
				break;
			case Gravity.BOTTOM:
				if (checkDrawerViewAbsoluteGravity(child, Gravity.BOTTOM))
					needsInvalidate |= mBottomDragger.smoothSlideViewTo(child, getWidth(), child.getTop());
				break;
			default:
				needsInvalidate |= mBottomDragger.smoothSlideViewTo(child, getWidth(), child.getTop());
				break;
			}
			lp.isPeeking = false;
		}

		mLeftCallback.removeCallbacks();
		mRightCallback.removeCallbacks();
		mTopCallback.removeCallbacks();
		mBottomCallback.removeCallbacks();

		if (needsInvalidate) {
			invalidate();
		}
	}

	/**
	 * Open the specified drawer view by animating it into view.
	 * 
	 * @param drawerView
	 *            Drawer view to open
	 */
	public void openDrawer(View drawerView) {
		if (!isDrawerView(drawerView)) {
			throw new IllegalArgumentException("View " + drawerView + " is not a sliding drawer");
		}

		if (mFirstLayout) {
			final LayoutParams lp = (LayoutParams) drawerView.getLayoutParams();
			lp.onScreen = 1.f;
			lp.knownOpen = true;
		} else {
			switch (getDrawerViewAbsoluteGravity(drawerView)) {
			case Gravity.LEFT:
				if (checkDrawerViewAbsoluteGravity(drawerView, Gravity.LEFT))
					mLeftDragger.smoothSlideViewTo(drawerView, 0, drawerView.getTop());
				break;
			case Gravity.RIGHT:
				if (checkDrawerViewAbsoluteGravity(drawerView, Gravity.RIGHT))
					mRightDragger.smoothSlideViewTo(drawerView, getWidth() - drawerView.getWidth(), drawerView.getTop());
				break;
			case Gravity.TOP:
				if (checkDrawerViewAbsoluteGravity(drawerView, Gravity.TOP))
					mTopDragger.smoothSlideViewTo(drawerView, drawerView.getLeft(), 0);
				break;
			case Gravity.BOTTOM:
				if (checkDrawerViewAbsoluteGravity(drawerView, Gravity.BOTTOM))
					mBottomDragger.smoothSlideViewTo(drawerView, getHeight() - drawerView.getHeight(), drawerView.getLeft());
				break;
			default:
				mBottomDragger.smoothSlideViewTo(drawerView, getHeight() - drawerView.getHeight(), drawerView.getLeft());
				break;
			}
		}
		invalidate();
	}

	/**
	 * Open the specified drawer by animating it out of view.
	 * 
	 * @param gravity
	 *            Gravity.LEFT to move the left drawer or Gravity.RIGHT for the
	 *            right. GravityCompat.START or GravityCompat.END may also be
	 *            used.
	 */
	public void openDrawer(int gravity) {
		final View drawerView = findDrawerWithGravity(gravity);
		if (drawerView == null) {
			throw new IllegalArgumentException("No drawer view found with gravity " + gravityToString(gravity));
		}
		openDrawer(drawerView);
	}

	/**
	 * Close the specified drawer view by animating it into view.
	 * 
	 * @param drawerView
	 *            Drawer view to close
	 */
	public void closeDrawer(View drawerView) {
		if (!isDrawerView(drawerView)) {
			throw new IllegalArgumentException("View " + drawerView + " is not a sliding drawer");
		}

		if (mFirstLayout) {
			final LayoutParams lp = (LayoutParams) drawerView.getLayoutParams();
			lp.onScreen = 0.f;
			lp.knownOpen = false;
		} else {
			switch (getDrawerViewAbsoluteGravity(drawerView)) {
			case Gravity.LEFT:
				if (checkDrawerViewAbsoluteGravity(drawerView, Gravity.LEFT))
					mLeftDragger.smoothSlideViewTo(drawerView, -drawerView.getWidth(), drawerView.getTop());
				break;
			case Gravity.RIGHT:
				if (checkDrawerViewAbsoluteGravity(drawerView, Gravity.RIGHT))
					mRightDragger.smoothSlideViewTo(drawerView, getWidth(), drawerView.getTop());
				break;
			case Gravity.TOP:
				if (checkDrawerViewAbsoluteGravity(drawerView, Gravity.TOP))
					mTopDragger.smoothSlideViewTo(drawerView, drawerView.getLeft(), -drawerView.getHeight());
				break;
			case Gravity.BOTTOM:
				if (checkDrawerViewAbsoluteGravity(drawerView, Gravity.BOTTOM))
					mBottomDragger.smoothSlideViewTo(drawerView, drawerView.getLeft(), getHeight());
				break;
			default:
				mBottomDragger.smoothSlideViewTo(drawerView, drawerView.getLeft(), getHeight());
				break;
			}
		}
		invalidate();
	}

	/**
	 * Close the specified drawer by animating it out of view.
	 * 
	 * @param gravity
	 *            Gravity.LEFT to move the left drawer or Gravity.RIGHT for the
	 *            right. GravityCompat.START or GravityCompat.END may also be
	 *            used.
	 */
	public void closeDrawer(int gravity) {
		final View drawerView = findDrawerWithGravity(gravity);
		if (drawerView == null) {
			throw new IllegalArgumentException("No drawer view found with gravity " + gravityToString(gravity));
		}
		closeDrawer(drawerView);
	}

	/**
	 * Check if the given drawer view is currently in an open state. To be
	 * considered "open" the drawer must have settled into its fully visible
	 * state. To check for partial visibility use
	 * {@link #isDrawerVisible(android.view.View)}.
	 * 
	 * @param drawer
	 *            Drawer view to check
	 * @return true if the given drawer view is in an open state
	 * @see #isDrawerVisible(android.view.View)
	 */
	public boolean isDrawerOpen(View drawer) {
		if (!isDrawerView(drawer)) {
			throw new IllegalArgumentException("View " + drawer + " is not a drawer");
		}
		return ((LayoutParams) drawer.getLayoutParams()).knownOpen;
	}

	/**
	 * Check if the given drawer view is currently in an open state. To be
	 * considered "open" the drawer must have settled into its fully visible
	 * state. If there is no drawer with the given gravity this method will
	 * return false.
	 * 
	 * @param drawerGravity
	 *            Gravity of the drawer to check
	 * @return true if the given drawer view is in an open state
	 */
	public boolean isDrawerOpen(int drawerGravity) {
		final View drawerView = findDrawerWithGravity(drawerGravity);
		if (drawerView != null) {
			return isDrawerOpen(drawerView);
		}
		return false;
	}

	/**
	 * Check if a given drawer view is currently visible on-screen. The drawer
	 * may be only peeking onto the screen, fully extended, or anywhere
	 * inbetween.
	 * 
	 * @param drawer
	 *            Drawer view to check
	 * @return true if the given drawer is visible on-screen
	 * @see #isDrawerOpen(android.view.View)
	 */
	public boolean isDrawerVisible(View drawer) {
		if (!isDrawerView(drawer)) {
			throw new IllegalArgumentException("View " + drawer + " is not a drawer");
		}
		return ((LayoutParams) drawer.getLayoutParams()).onScreen > 0;
	}

	/**
	 * Check if a given drawer view is currently visible on-screen. The drawer
	 * may be only peeking onto the screen, fully extended, or anywhere
	 * inbetween. If there is no drawer with the given gravity this method will
	 * return false.
	 * 
	 * @param drawerGravity
	 *            Gravity of the drawer to check
	 * @return true if the given drawer is visible on-screen
	 */
	public boolean isDrawerVisible(int drawerGravity) {
		final View drawerView = findDrawerWithGravity(drawerGravity);
		if (drawerView != null) {
			return isDrawerVisible(drawerView);
		}
		return false;
	}

	private boolean hasPeekingDrawer() {
		final int childCount = getChildCount();
		for (int i = 0; i < childCount; i++) {
			final LayoutParams lp = (LayoutParams) getChildAt(i).getLayoutParams();
			if (lp.isPeeking) {
				return true;
			}
		}
		return false;
	}

	@Override
	protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
		return new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
	}

	@Override
	protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
		return p instanceof LayoutParams ? new LayoutParams((LayoutParams) p)
				: p instanceof ViewGroup.MarginLayoutParams ? new LayoutParams((MarginLayoutParams) p) : new LayoutParams(p);
	}

	@Override
	protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
		return p instanceof LayoutParams && super.checkLayoutParams(p);
	}

	@Override
	public ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
		return new LayoutParams(getContext(), attrs);
	}

	private boolean hasVisibleDrawer() {
		return findVisibleDrawer() != null;
	}

	private View findVisibleDrawer() {
		final int childCount = getChildCount();
		for (int i = 0; i < childCount; i++) {
			final View child = getChildAt(i);
			if (isDrawerView(child) && isDrawerVisible(child)) {
				return child;
			}
		}
		return null;
	}

	void cancelChildViewTouch() {
		// Cancel child touches
		if (!mChildrenCanceledTouch) {
			final long now = SystemClock.uptimeMillis();
			final MotionEvent cancelEvent = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0.0f, 0.0f, 0);
			final int childCount = getChildCount();
			for (int i = 0; i < childCount; i++) {
				getChildAt(i).dispatchTouchEvent(cancelEvent);
			}
			cancelEvent.recycle();
			mChildrenCanceledTouch = true;
		}
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK && hasVisibleDrawer()) {
			KeyEventCompat.startTracking(event);
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			final View visibleDrawer = findVisibleDrawer();
			if (visibleDrawer != null && getDrawerLockMode(visibleDrawer) == LOCK_MODE_UNLOCKED) {
				closeDrawers();
			}
			return visibleDrawer != null;
		}
		return super.onKeyUp(keyCode, event);
	}

	@Override
	protected void onRestoreInstanceState(Parcelable state) {
		final SavedState ss = (SavedState) state;
		super.onRestoreInstanceState(ss.getSuperState());

		if (ss.openDrawerGravity != Gravity.NO_GRAVITY) {
			final View toOpen = findDrawerWithGravity(ss.openDrawerGravity);
			if (toOpen != null) {
				openDrawer(toOpen);
			}
		}

		setDrawerLockMode(ss.lockModeLeft, Gravity.LEFT);
		setDrawerLockMode(ss.lockModeRight, Gravity.RIGHT);
	}

	@Override
	protected Parcelable onSaveInstanceState() {
		final Parcelable superState = super.onSaveInstanceState();

		final SavedState ss = new SavedState(superState);

		final int childCount = getChildCount();
		for (int i = 0; i < childCount; i++) {
			final View child = getChildAt(i);
			if (!isDrawerView(child)) {
				continue;
			}

			final LayoutParams lp = (LayoutParams) child.getLayoutParams();
			if (lp.knownOpen) {
				ss.openDrawerGravity = lp.gravity;
				// Only one drawer can be open at a time.
				break;
			}
		}

		ss.lockModeLeft = mLockModeLeft;
		ss.lockModeRight = mLockModeRight;
		ss.lockModeTop = mLockModeTop;
		ss.lockModeBottom = mLockModeBottom;

		return ss;
	}

	/**
	 * State persisted across instances
	 */
	protected static class SavedState extends BaseSavedState {
		int openDrawerGravity = Gravity.NO_GRAVITY;
		int lockModeLeft = LOCK_MODE_UNLOCKED;
		int lockModeRight = LOCK_MODE_UNLOCKED;
		int lockModeTop = LOCK_MODE_UNLOCKED;
		int lockModeBottom = LOCK_MODE_UNLOCKED;

		public SavedState(Parcel in) {
			super(in);
			openDrawerGravity = in.readInt();
		}

		public SavedState(Parcelable superState) {
			super(superState);
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			super.writeToParcel(dest, flags);
			dest.writeInt(openDrawerGravity);
		}

		public static final Parcelable.Creator<SavedState> CREATOR = new Parcelable.Creator<SavedState>() {
			@Override
			public SavedState createFromParcel(Parcel source) {
				return new SavedState(source);
			}

			@Override
			public SavedState[] newArray(int size) {
				return new SavedState[size];
			}
		};
	}

	private class ViewDragCallback extends ViewDragHelper.Callback {
		private final int mAbsGravity;
		private ViewDragHelper mDragger;

		private final Runnable mPeekRunnable = new Runnable() {
			@Override
			public void run() {
				peekDrawer();
			}
		};

		public ViewDragCallback(int gravity) {
			mAbsGravity = gravity;
		}

		public void setDragger(ViewDragHelper dragger) {
			mDragger = dragger;
		}

		public void removeCallbacks() {
			AllDrawerLayout.this.removeCallbacks(mPeekRunnable);
		}

		@Override
		public boolean tryCaptureView(View child, int pointerId) {
			// Only capture views where the gravity matches what we're looking
			// for.
			// This lets us use two ViewDragHelpers, one for each side drawer.
			return isDrawerView(child) && checkDrawerViewAbsoluteGravity(child, mAbsGravity)
					&& getDrawerLockMode(child) == LOCK_MODE_UNLOCKED;
		}

		@Override
		public void onViewDragStateChanged(int state) {
			updateDrawerState(mAbsGravity, state, mDragger.getCapturedView());
		}

		@Override
		public void onViewPositionChanged(View changedView, int left, int top, int dx, int dy) {
			Log.i(TAG, "onViewPositionChanged() -- left = " + left + " -- top = " + top + " -- dx = " + dx + " -- dy = " + dy);
			float offset = 0;
			final int childWidth = changedView.getWidth();
			final int childHeight = changedView.getHeight();
			final int width = getWidth();
			final int height = getHeight();

			// This reverses the positioning shown in onLayout.
			switch (getDrawerViewAbsoluteGravity(changedView)) {
			case Gravity.LEFT:
				if (checkDrawerViewAbsoluteGravity(changedView, Gravity.LEFT))
					offset = (float) (childWidth + left) / childWidth;
				break;
			case Gravity.RIGHT:
				if (checkDrawerViewAbsoluteGravity(changedView, Gravity.RIGHT))
					offset = (float) (width - left) / childWidth;
				break;
			case Gravity.TOP:
				if (checkDrawerViewAbsoluteGravity(changedView, Gravity.TOP))
					offset = (float) (childHeight + top) / childHeight;
				break;
			case Gravity.BOTTOM:
				if (checkDrawerViewAbsoluteGravity(changedView, Gravity.BOTTOM))
					offset = (float) (height - top) / childHeight;
				break;
			default:
				offset = (float) (height - top) / childHeight;
				break;
			}
			setDrawerViewOffset(changedView, offset);
			changedView.setVisibility(offset == 0 ? INVISIBLE : VISIBLE);
			invalidate();
		}

		@Override
		public void onViewCaptured(View capturedChild, int activePointerId) {
			final LayoutParams lp = (LayoutParams) capturedChild.getLayoutParams();
			lp.isPeeking = false;
			closeOtherDrawer();
		}

		private void closeOtherDrawer() {
			final int otherGrav;
			switch (mAbsGravity) {
			case Gravity.LEFT:
				otherGrav = Gravity.LEFT;
				break;
			case Gravity.RIGHT:
				otherGrav = Gravity.RIGHT;
				break;
			case Gravity.TOP:
				otherGrav = Gravity.TOP;
				break;
			case Gravity.BOTTOM:
				otherGrav = Gravity.BOTTOM;
				break;
			default:
				otherGrav = Gravity.BOTTOM;
				break;
			}
			final View toClose = findDrawerWithGravity(otherGrav);
			if (toClose != null) {
				closeDrawer(toClose);
			}
		}

		@Override
		public void onViewReleased(View releasedChild, float xvel, float yvel) {
			Log.i(TAG, "onViewReleased() -- xvel = " + xvel + " -- yvel = " + yvel);
			// Offset is how open the drawer is, therefore left/right values
			// are reversed from one another.
			final float offset = getDrawerViewOffset(releasedChild);
			final int childWidth = releasedChild.getWidth();
			final int childHeight = releasedChild.getHeight();

			int left = 0;
			int top = 0;
			switch (getDrawerViewAbsoluteGravity(releasedChild)) {
			case Gravity.LEFT:
				if (checkDrawerViewAbsoluteGravity(releasedChild, Gravity.LEFT)) {
					left = xvel > 0 || xvel == 0 && offset > 0.5f ? 0 : -childWidth;
					top = releasedChild.getTop();
				}
				break;
			case Gravity.RIGHT:
				if (checkDrawerViewAbsoluteGravity(releasedChild, Gravity.RIGHT)) {
					final int width = getWidth();
					left = xvel < 0 || xvel == 0 && offset > 0.5f ? width - childWidth : width;
					top = releasedChild.getTop();
				}
				break;
			case Gravity.TOP:
				if (checkDrawerViewAbsoluteGravity(releasedChild, Gravity.TOP)) {
					left = releasedChild.getLeft();
					top = yvel > 0 || yvel == 0 && offset > 0.5f ? 0 : -childHeight;
				}
				break;
			case Gravity.BOTTOM:
				if (checkDrawerViewAbsoluteGravity(releasedChild, Gravity.BOTTOM)) {
					left = releasedChild.getLeft();
					final int height = getHeight();
					top = yvel < 0 || yvel == 0 && offset > 0.5f ? height - childHeight : height;
				}
				break;
			}
			mDragger.settleCapturedViewAt(left, top);
			invalidate();
		}

		@Override
		public void onEdgeTouched(int edgeFlags, int pointerId) {
			Log.i(TAG, "onEdgeTouched()");
			postDelayed(mPeekRunnable, PEEK_DELAY);
		}

		private void peekDrawer() {
			View toCapture = null;
			int childLeft = 0;
			int childTop = 0;
			final int peekDistance = mDragger.getEdgeSize();
			switch (mAbsGravity) {
			case Gravity.LEFT:
				toCapture = findDrawerWithGravity(Gravity.LEFT);
				childLeft = (toCapture != null ? -toCapture.getWidth() : 0) + peekDistance;
				childTop = 0;
				break;
			case Gravity.RIGHT:
				toCapture = findDrawerWithGravity(Gravity.RIGHT);
				childLeft = getWidth() - peekDistance;
				childTop = 0;
				break;
			case Gravity.TOP:
				toCapture = findDrawerWithGravity(Gravity.TOP);
				childLeft = 0;
				childTop = (toCapture != null ? -toCapture.getHeight() : 0) + peekDistance;
				break;
			case Gravity.BOTTOM:
				toCapture = findDrawerWithGravity(Gravity.BOTTOM);
				childLeft = 0;
				childTop = getHeight() - peekDistance;
				break;
			default:
				toCapture = findDrawerWithGravity(Gravity.BOTTOM);
				childLeft = 0;
				childTop = getHeight() - peekDistance;
			}
			boolean leftEdge = (mAbsGravity == Gravity.LEFT);
			boolean topEdge = (mAbsGravity == Gravity.TOP);
			if (toCapture != null && ((leftEdge && toCapture.getLeft() < childLeft) || (!leftEdge && toCapture.getLeft() > childLeft))
					&& ((topEdge && toCapture.getTop() < childTop) || (!topEdge && toCapture.getTop() > childTop))
					&& getDrawerLockMode(toCapture) == LOCK_MODE_UNLOCKED) {
				final LayoutParams lp = (LayoutParams) toCapture.getLayoutParams();
				mDragger.smoothSlideViewTo(toCapture, childLeft, toCapture.getTop());
				lp.isPeeking = true;
				invalidate();
				closeOtherDrawer();
				cancelChildViewTouch();
			}
		}

		@Override
		public boolean onEdgeLock(int edgeFlags) {
			if (ALLOW_EDGE_LOCK) {
				final View drawer = findDrawerWithGravity(mAbsGravity);
				if (drawer != null && !isDrawerOpen(drawer)) {
					closeDrawer(drawer);
				}
				return true;
			}
			return false;
		}

		@Override
		public void onEdgeDragStarted(int edgeFlags, int pointerId) {
			final View toCapture;
			if ((edgeFlags & ViewDragHelper.EDGE_LEFT) == ViewDragHelper.EDGE_LEFT) {
				toCapture = findDrawerWithGravity(Gravity.LEFT);
			} else if ((edgeFlags & ViewDragHelper.EDGE_RIGHT) == ViewDragHelper.EDGE_RIGHT) {
				toCapture = findDrawerWithGravity(Gravity.RIGHT);
			} else if ((edgeFlags & ViewDragHelper.EDGE_TOP) == ViewDragHelper.EDGE_TOP) {
				toCapture = findDrawerWithGravity(Gravity.TOP);
			} else/*
				 * if ((edgeFlags & ViewDragHelper.EDGE_BOTTOM) ==
				 * ViewDragHelper.EDGE_BOTTOM)
				 */{
				toCapture = findDrawerWithGravity(Gravity.BOTTOM);
			}
			if (toCapture != null && getDrawerLockMode(toCapture) == LOCK_MODE_UNLOCKED) {
				mDragger.captureChildView(toCapture, pointerId);
			}
		}

		@Override
		public int getViewHorizontalDragRange(View child) {
			return child.getWidth();
		}

		@Override
		public int getViewVerticalDragRange(View child) {
			// TODO Auto-generated method stub
			return child.getHeight();
		}

		@Override
		public int clampViewPositionHorizontal(View child, int left, int dx) {
			switch (getDrawerViewAbsoluteGravity(child)) {
			case Gravity.LEFT:
				if (checkDrawerViewAbsoluteGravity(child, Gravity.LEFT)) {
					return Math.max(-child.getWidth(), Math.min(left, 0));
				}
				break;
			case Gravity.RIGHT:
				if (checkDrawerViewAbsoluteGravity(child, Gravity.RIGHT)) {
					final int width = getWidth();
					return Math.max(width - child.getWidth(), Math.min(left, width));
				}
				break;
			case Gravity.TOP:
				if (checkDrawerViewAbsoluteGravity(child, Gravity.TOP)) {
					return child.getLeft();
				}
				break;
			case Gravity.BOTTOM:
				if (checkDrawerViewAbsoluteGravity(child, Gravity.BOTTOM)) {
					return child.getLeft();
				}
				break;
			default:
				final int width = getWidth();
				return Math.max(width - child.getWidth(), Math.min(left, width));
			}
			final int width = getWidth();
			return Math.max(width - child.getWidth(), Math.min(left, width));
		}

		@Override
		public int clampViewPositionVertical(View child, int top, int dy) {
			switch (getDrawerViewAbsoluteGravity(child)) {
			case Gravity.LEFT:
				if (checkDrawerViewAbsoluteGravity(child, Gravity.LEFT)) {
					return child.getTop();
				}
				break;
			case Gravity.RIGHT:
				if (checkDrawerViewAbsoluteGravity(child, Gravity.RIGHT)) {
					return child.getTop();
				}
				break;
			case Gravity.TOP:
				if (checkDrawerViewAbsoluteGravity(child, Gravity.TOP)) {
					return Math.max(-child.getHeight(), Math.min(top, 0));
				}
				break;
			case Gravity.BOTTOM:
				if (checkDrawerViewAbsoluteGravity(child, Gravity.BOTTOM)) {
					final int height = getHeight();
					return Math.max(height - child.getHeight(), Math.min(top, height));
				}
				break;
			default:
				final int height = getHeight();
				return Math.max(height - child.getHeight(), Math.min(top, height));
			}
			final int height = getHeight();
			return Math.max(height - child.getHeight(), Math.min(top, height));
		}
	}

	public static class LayoutParams extends ViewGroup.MarginLayoutParams {

		public int gravity = Gravity.NO_GRAVITY;
		float onScreen;
		boolean isPeeking;
		boolean knownOpen;

		public LayoutParams(Context c, AttributeSet attrs) {
			super(c, attrs);

			final TypedArray a = c.obtainStyledAttributes(attrs, LAYOUT_ATTRS);
			this.gravity = a.getInt(0, Gravity.NO_GRAVITY);
			a.recycle();
		}

		public LayoutParams(int width, int height) {
			super(width, height);
		}

		public LayoutParams(int width, int height, int gravity) {
			this(width, height);
			this.gravity = gravity;
		}

		public LayoutParams(LayoutParams source) {
			super(source);
			this.gravity = source.gravity;
		}

		public LayoutParams(ViewGroup.LayoutParams source) {
			super(source);
		}

		public LayoutParams(ViewGroup.MarginLayoutParams source) {
			super(source);
		}
	}

	class AccessibilityDelegate extends AccessibilityDelegateCompat {
		private final Rect mTmpRect = new Rect();

		@Override
		public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfoCompat info) {
			final AccessibilityNodeInfoCompat superNode = AccessibilityNodeInfoCompat.obtain(info);
			super.onInitializeAccessibilityNodeInfo(host, superNode);

			info.setSource(host);
			final ViewParent parent = ViewCompat.getParentForAccessibility(host);
			if (parent instanceof View) {
				info.setParent((View) parent);
			}
			copyNodeInfoNoChildren(info, superNode);

			superNode.recycle();

			final int childCount = getChildCount();
			for (int i = 0; i < childCount; i++) {
				final View child = getChildAt(i);
				if (!filter(child)) {
					info.addChild(child);
				}
			}
		}

		@Override
		public boolean onRequestSendAccessibilityEvent(ViewGroup host, View child, AccessibilityEvent event) {
			if (!filter(child)) {
				return super.onRequestSendAccessibilityEvent(host, child, event);
			}
			return false;
		}

		public boolean filter(View child) {
			final View openDrawer = findOpenDrawer();
			return openDrawer != null && openDrawer != child;
		}

		/**
		 * This should really be in AccessibilityNodeInfoCompat, but there
		 * unfortunately seem to be a few elements that are not easily cloneable
		 * using the underlying API. Leave it private here as it's not
		 * general-purpose useful.
		 */
		private void copyNodeInfoNoChildren(AccessibilityNodeInfoCompat dest, AccessibilityNodeInfoCompat src) {
			final Rect rect = mTmpRect;

			src.getBoundsInParent(rect);
			dest.setBoundsInParent(rect);

			src.getBoundsInScreen(rect);
			dest.setBoundsInScreen(rect);

			dest.setVisibleToUser(src.isVisibleToUser());
			dest.setPackageName(src.getPackageName());
			dest.setClassName(src.getClassName());
			dest.setContentDescription(src.getContentDescription());

			dest.setEnabled(src.isEnabled());
			dest.setClickable(src.isClickable());
			dest.setFocusable(src.isFocusable());
			dest.setFocused(src.isFocused());
			dest.setAccessibilityFocused(src.isAccessibilityFocused());
			dest.setSelected(src.isSelected());
			dest.setLongClickable(src.isLongClickable());

			dest.addAction(src.getActions());
		}
	}
}

/*
 * Copyright (C) 2008 The Android Open Source Project
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

package org.codepunks.keyflinger;

import android.os.Handler;
import android.os.Message;
import android.content.Context;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.util.Log;


public class KeyFlingDetector
{
    public interface OnGestureListener
    {
        boolean onDown(MotionEvent e);
        void onShowPress(MotionEvent e);
        boolean onSingleTapUp(MotionEvent e);
        boolean onScroll(MotionEvent e1, MotionEvent e2,
                         float distanceX, float distanceY);
        void onLongPress(MotionEvent e);
        boolean onFling(MotionEvent e1, MotionEvent e2,
                        float velocityX, float velocityY);
    }

    public interface OnDoubleTapListener
    {
        boolean onSingleTapConfirmed(MotionEvent e);
        boolean onDoubleTap(MotionEvent e);
        boolean onDoubleTapEvent(MotionEvent e);
    }

    public static class SimpleOnGestureListener
        implements OnGestureListener, OnDoubleTapListener
    {
        public boolean onSingleTapUp(MotionEvent e)
        {
            return false;
        }

        public void onLongPress(MotionEvent e)
        {
        }

        public boolean onScroll(MotionEvent e1, MotionEvent e2,
                                float distanceX, float distanceY)
        {
            return false;
        }

        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
                               float velocityY)
        {
            return false;
        }

        public void onShowPress(MotionEvent e)
        {
        }

        public boolean onDown(MotionEvent e)
        {
            return false;
        }

        public boolean onDoubleTap(MotionEvent e)
        {
            return false;
        }

        public boolean onDoubleTapEvent(MotionEvent e)
        {
            return false;
        }

        public boolean onSingleTapConfirmed(MotionEvent e)
        {
            return false;
        }
    }

    // TODO: ViewConfiguration
    private int mBiggerTouchSlopSquare = 20 * 20;

    private int mTouchSlopSquare;
    private int mDoubleTapSlopSquare;
    private int mMinimumFlingVelocity;
    private int mMaximumFlingVelocity;

    private static final int LONGPRESS_TIMEOUT =
        ViewConfiguration.getLongPressTimeout();
    private static final int TAP_TIMEOUT = ViewConfiguration.getTapTimeout();
    private static final int DOUBLE_TAP_TIMEOUT =
        ViewConfiguration.getDoubleTapTimeout();

    // constants for Message.what used by GestureHandler below
    private static final int SHOW_PRESS = 1;
    private static final int LONG_PRESS = 2;
    private static final int TAP = 3;

    private final Handler mHandler;
    private final OnGestureListener mListener;
    private OnDoubleTapListener mDoubleTapListener;

    private boolean mStillDown;
    private boolean mInLongPress;
    private boolean mAlwaysInTapRegion;
    private boolean mAlwaysInBiggerTapRegion;

    private MotionEvent mCurrentDownEvent;
    private MotionEvent mPreviousUpEvent;

    /**
     * True when the user is still touching for the second tap (down, move, and
     * up events). Can only be true if there is a double tap listener attached.
     */
    private boolean mIsDoubleTapping;

    private float mLastMotionY;
    private float mLastMotionX;

    private boolean mIsLongpressEnabled;

    /**
     * Determines speed during touch scrolling
     */
    private VelocityTracker mVelocityTracker;

    private class GestureHandler extends Handler {
        GestureHandler() {
            super();
        }

        GestureHandler(Handler handler) {
            super(handler.getLooper());
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case SHOW_PRESS:
                mListener.onShowPress(mCurrentDownEvent);
                break;
                
            case LONG_PRESS:
                dispatchLongPress();
                break;
                
            case TAP:
                // If the user's finger is still down, do not count it as a tap
                if (mDoubleTapListener != null && !mStillDown) {
                    mDoubleTapListener.onSingleTapConfirmed(mCurrentDownEvent);
                }
                break;

            default:
                throw new RuntimeException("Unknown message " + msg); //never
            }
        }
    }

    @Deprecated public KeyFlingDetector(OnGestureListener listener,
                                        Handler handler)
    {
        this(null, listener, handler);
    }

    @Deprecated public KeyFlingDetector(OnGestureListener listener)
    {
        this(null, listener, null);
    }

    public KeyFlingDetector(Context context, OnGestureListener listener)
    {
        this(context, listener, null);
    }

    public KeyFlingDetector(Context context, OnGestureListener listener,
                            Handler handler)
    {
        if (handler != null) {
            mHandler = new GestureHandler(handler);
        } else {
            mHandler = new GestureHandler();
        }
        mListener = listener;
        if (listener instanceof OnDoubleTapListener) {
            setOnDoubleTapListener((OnDoubleTapListener) listener);
        }
        init(context);
    }

    private void init(Context context)
    {
        if (mListener == null) {
            throw new NullPointerException("OnGestureListener must not be null");
        }
        if (context == null) {
            throw new NullPointerException("Context must not be null");
        }
        mIsLongpressEnabled = true;

        int touchSlop = 10;
        int doubleTapSlop = 10;
        final ViewConfiguration configuration =
            ViewConfiguration.get(context);
        touchSlop = configuration.getScaledTouchSlop();
        doubleTapSlop = configuration.getScaledDoubleTapSlop();
        mMinimumFlingVelocity = configuration.getScaledMinimumFlingVelocity();
        mMinimumFlingVelocity = 5;
        mMaximumFlingVelocity = configuration.getScaledMaximumFlingVelocity();
        mTouchSlopSquare = touchSlop * touchSlop;
        mTouchSlopSquare = 20;
        mDoubleTapSlopSquare = doubleTapSlop * doubleTapSlop;
    }

    public void setOnDoubleTapListener(OnDoubleTapListener onDoubleTapListener)
    {
        mDoubleTapListener = onDoubleTapListener;
    }

    public void setIsLongpressEnabled(boolean isLongpressEnabled)
    {
        mIsLongpressEnabled = isLongpressEnabled;
    }

    public boolean isLongpressEnabled()
    {
        return mIsLongpressEnabled;
    }

    public boolean onTouchEvent(MotionEvent ev)
    {
        final int action = ev.getAction();
        final float y = ev.getY();
        final float x = ev.getX();

        if (mVelocityTracker == null)
        {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(ev);

        boolean handled = false;

        switch (action)
        {
        case MotionEvent.ACTION_DOWN:
            if (mDoubleTapListener != null)
            {
                boolean hadTapMessage = mHandler.hasMessages(TAP);
                if (hadTapMessage)
                {
                    mHandler.removeMessages(TAP);
                }
                if ((mCurrentDownEvent != null) && (mPreviousUpEvent != null) &&
                    hadTapMessage &&
                    isConsideredDoubleTap(mCurrentDownEvent,
                                          mPreviousUpEvent, ev))
                {
                    // This is a second tap
                    mIsDoubleTapping = true;
                    // Give a callback with the first tap of the double-tap
                    handled |= mDoubleTapListener.onDoubleTap(mCurrentDownEvent);
                    // Give a callback with down event of the double-tap
                    handled |= mDoubleTapListener.onDoubleTapEvent(ev);
                }
                else
                {
                    // This is a first tap
                    mHandler.sendEmptyMessageDelayed(TAP, DOUBLE_TAP_TIMEOUT);
                }
            }

            mLastMotionX = x;
            mLastMotionY = y;
            mCurrentDownEvent = MotionEvent.obtain(ev);
            mAlwaysInTapRegion = true;
            mAlwaysInBiggerTapRegion = true;
            mStillDown = true;
            mInLongPress = false;
            
            if (mIsLongpressEnabled)
            {
                mHandler.removeMessages(LONG_PRESS);
                mHandler.sendEmptyMessageAtTime(LONG_PRESS,
                                                mCurrentDownEvent.getDownTime() +
                                                TAP_TIMEOUT +
                                                LONGPRESS_TIMEOUT);
            }
            mHandler.sendEmptyMessageAtTime(SHOW_PRESS,
                                            mCurrentDownEvent.getDownTime() +
                                            TAP_TIMEOUT);
            handled |= mListener.onDown(ev);
            break;

        case MotionEvent.ACTION_MOVE:
            if (mInLongPress)
            {
                break;
            }
            final float scrollX = mLastMotionX - x;
            final float scrollY = mLastMotionY - y;
            if (mIsDoubleTapping)
            {
                // Give the move events of the double-tap
                handled |= mDoubleTapListener.onDoubleTapEvent(ev);
            }
            else if (mAlwaysInTapRegion)
            {
                final int deltaX = (int) (x - mCurrentDownEvent.getX());
                final int deltaY = (int) (y - mCurrentDownEvent.getY());
                int distance = (deltaX * deltaX) + (deltaY * deltaY);
                if (distance > mTouchSlopSquare)
                {
                    handled = mListener.onScroll(mCurrentDownEvent, ev, scrollX,
                                                 scrollY);
                    mLastMotionX = x;
                    mLastMotionY = y;
                    mAlwaysInTapRegion = false;
                    mHandler.removeMessages(TAP);
                    mHandler.removeMessages(SHOW_PRESS);
                    mHandler.removeMessages(LONG_PRESS);
                }
                if (distance > mBiggerTouchSlopSquare)
                {
                    mAlwaysInBiggerTapRegion = false;
                }
            }
            else if ((Math.abs(scrollX) >= 1) || (Math.abs(scrollY) >= 1))
            {
                handled = mListener.onScroll(mCurrentDownEvent, ev, scrollX,
                                             scrollY);
                mLastMotionX = x;
                mLastMotionY = y;
            }
            break;

        case MotionEvent.ACTION_UP:
            mStillDown = false;
            MotionEvent currentUpEvent = MotionEvent.obtain(ev);
            if (mIsDoubleTapping)
            {
                // Finally, give the up event of the double-tap
                handled |= mDoubleTapListener.onDoubleTapEvent(ev);
            }
            else if (mInLongPress)
            {
                mHandler.removeMessages(TAP);
                mInLongPress = false;
            }
            else if (mAlwaysInTapRegion)
            {
                handled = mListener.onSingleTapUp(ev);
            }
            else
            {
                // A fling must travel the minimum tap distance
                final VelocityTracker velocityTracker = mVelocityTracker;
                velocityTracker.computeCurrentVelocity(1000,
                                                       mMaximumFlingVelocity);
                final float velocityY = velocityTracker.getYVelocity();
                final float velocityX = velocityTracker.getXVelocity();

                if ((Math.abs(velocityY) > mMinimumFlingVelocity) ||
                    (Math.abs(velocityX) > mMinimumFlingVelocity))
                {
                    handled = mListener.onFling(mCurrentDownEvent,
                                                currentUpEvent, velocityX,
                                                velocityY);
                }
            }
            mPreviousUpEvent = MotionEvent.obtain(ev);
            mVelocityTracker.recycle();
            mVelocityTracker = null;
            mIsDoubleTapping = false;
            mHandler.removeMessages(SHOW_PRESS);
            mHandler.removeMessages(LONG_PRESS);
            break;
        case MotionEvent.ACTION_CANCEL:
            mHandler.removeMessages(SHOW_PRESS);
            mHandler.removeMessages(LONG_PRESS);
            mHandler.removeMessages(TAP);
            mVelocityTracker.recycle();
            mVelocityTracker = null;
            mIsDoubleTapping = false;
            mStillDown = false;
            if (mInLongPress)
            {
                mInLongPress = false;
                break;
            }
        }
        return handled;
    }

    private boolean isConsideredDoubleTap(MotionEvent firstDown,
                                          MotionEvent firstUp,
                                          MotionEvent secondDown)
    {
        if (!mAlwaysInBiggerTapRegion)
        {
            return false;
        }

        if ((secondDown.getEventTime() -
             firstUp.getEventTime()) > DOUBLE_TAP_TIMEOUT)
        {
            return false;
        }

        int deltaX = (int) firstDown.getX() - (int) secondDown.getX();
        int deltaY = (int) firstDown.getY() - (int) secondDown.getY();
        return (deltaX * deltaX + deltaY * deltaY < mDoubleTapSlopSquare);
    }

    private void dispatchLongPress()
    {
        mHandler.removeMessages(TAP);
        mInLongPress = true;
        mListener.onLongPress(mCurrentDownEvent);
    }
}

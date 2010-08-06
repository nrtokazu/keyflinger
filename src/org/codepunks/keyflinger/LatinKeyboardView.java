/*
 * Copyright (C) 2010 James Newton
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
/*
 * Copyright (C) 2008-2009 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.codepunks.keyflinger;

import android.content.Context;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.inputmethodservice.Keyboard.Key;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.Region.Op;
import android.view.MotionEvent;
import android.util.AttributeSet;
import android.util.Log;

import java.util.List;


public class LatinKeyboardView extends KeyboardView
{
    static final String TAG = "KeyFlinger";
    static final int KEYCODE_OPTIONS = -100;

    private KeyFlinger mKeyFlinger;
    private KeyFlingDetector mFlingDetector;
    private int travelX = 10;
    private int travelY = 10;

    // Copied from android.inputmethodservice.Keyboard
    private static final int NOT_A_KEY = -1000;
    private LatinKeyboard mKeyboard;
    private LatinKeyboard.LatinKey[] mKeys;
    private int mProximityThreshold;
    private static int MAX_NEARBY_KEYS = 12;
    private int[] mDistances = new int[MAX_NEARBY_KEYS];
    private int mDownKey = NOT_A_KEY;
    private boolean mKeyboardChanged;
    private boolean mDrawPending;
    private Bitmap mBuffer;
    private Canvas mCanvas;
    private Rect mDrawRect;
    private Rect mTextRect;
    private Paint mPaint;
        
    public LatinKeyboardView(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        Log.d(TAG, "cons 1");
        setup(context, attrs);
    }

    public LatinKeyboardView(Context context, AttributeSet attrs, int defStyle)
    {
        super(context, attrs, defStyle);
        Log.d(TAG, "cons 2");
        setup(context, attrs);
    }

    protected void setup(Context context, AttributeSet attrs)
    {
        Log.d(TAG, "setup");

        initKeyFlinging();
        setPreviewEnabled(false);

        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setAlpha(255);

        mDrawRect = new Rect();
        mTextRect = new Rect();
    }

    public void setKeyFlinger(KeyFlinger kf)
    {
        mKeyFlinger = kf;
    }

    protected void initKeyFlinging()
    {
        mFlingDetector = new KeyFlingDetector(getContext(),
            new KeyFlingDetector.FlingListener()
            {
                @Override
                public boolean onFling(KeyFlingDetector.FlingEvent[] evs,
                                       int idx, int pid)
                {
                    Log.d(TAG, String.format("onFling idx=%d pid=%d", idx, pid));
                    KeyFlingDetector.FlingEvent e = evs[pid];
                    final float absX = Math.abs(e.velocityX);
                    final float absY = Math.abs(e.velocityY);
                    float deltaX = e.up.getX(idx) - e.down.getX(idx);
                    float deltaY = e.up.getY(idx) - e.down.getY(idx);
                    int code = NOT_A_KEY;
                    if (mDownKey == NOT_A_KEY)
                    {
                        Log.d(TAG, "Passing in onFling. Bad key.");
                        return false;
                    }
                    if ((absY < absX) && (deltaX > travelX))
                    {
                        mKeyFlinger.flingRight(mKeys[mDownKey]);
                        code = mKeys[mDownKey].mDCodes[
                            LatinKeyboard.KEY_INDEX_RIGHT];
                    }
                    else if ((absY < absX) & (deltaX < -travelX))
                    {
                        mKeyFlinger.flingLeft(mKeys[mDownKey]);
                        code = mKeys[mDownKey].mDCodes[
                            LatinKeyboard.KEY_INDEX_LEFT];
                    }
                    else if ((absX < absY) && (deltaY < -travelY))
                    {
                        mKeyFlinger.flingUp(mKeys[mDownKey]);
                        code = mKeys[mDownKey].mDCodes[
                            LatinKeyboard.KEY_INDEX_UP];
                    }
                    else if ((absX < absY / 2) && (deltaY > travelY))
                    {
                        mKeyFlinger.flingDown(mKeys[mDownKey]);
                        code = mKeys[mDownKey].mDCodes[
                            LatinKeyboard.KEY_INDEX_DOWN];
                    }
                    if (code != NOT_A_KEY)
                    {
                        detectAndSendKey(mDownKey, code);
                        return true;
                    }
                    Log.d(TAG, "Passing in onFling");
                    return false;
                }
            });
    }

    @Override protected boolean onLongPress(Key key)
    {
        Log.d(TAG, "LKV::onLongPress");
        if (key.codes[0] == Keyboard.KEYCODE_CANCEL)
        {
            getOnKeyboardActionListener().onKey(KEYCODE_OPTIONS, null);
            return true;
        }
        else
        {
            return super.onLongPress(key);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent e)
    {
        //dumpEvent(e);
        if (mFlingDetector.onTouchEvent(e))
        {
            mKeyFlinger.ignoreNextKey();
        }
        if (e.getAction() == MotionEvent.ACTION_DOWN)
        {
            int touchX = (int) e.getX() - getPaddingLeft();
            int touchY = (int) e.getY() + getPaddingTop();
            mDownKey = getKeyIndices(touchX, touchY, null);
        }
        return super.onTouchEvent(e);
    }

    @SuppressWarnings("unused")
	private void dumpEvent(MotionEvent event)
    {
        String names[] = { "DOWN" , "UP" , "MOVE" , "CANCEL" , "OUTSIDE" ,
                           "POINTER_DOWN" , "POINTER_UP" , "7?" , "8?" , "9?" };
        StringBuilder sb = new StringBuilder();
        int action = event.getAction();
        int actionCode = action & MotionEvent.ACTION_MASK;
        sb.append("event ACTION_" ).append(names[actionCode]);
        if ((actionCode == MotionEvent.ACTION_POINTER_DOWN) ||
            (actionCode == MotionEvent.ACTION_POINTER_UP))
        {
            sb.append("(pid " ).append(
                action >> MotionEvent.ACTION_POINTER_ID_SHIFT);
            sb.append(")" );
        }
        sb.append("[" );
        for (int i = 0; i < event.getPointerCount(); i++)
        {
            sb.append("#" ).append(i);
            sb.append("(pid " ).append(event.getPointerId(i));
            sb.append(")=" ).append((int) event.getX(i));
            sb.append("," ).append((int) event.getY(i));
            if (i + 1 < event.getPointerCount())
                sb.append(";" );
        }
        sb.append("]" );
        Log.d(TAG, sb.toString());
    }

    /*
     * Copied from android.inputmethodservice.Keyboard to get at bits that are
     * needed.
     */

    @Override public void onSizeChanged(int w, int h, int oldw, int oldh)
    {
        super.onSizeChanged(w, h, oldw, oldh);
        // Release the buffer, if any and it will be reallocated on the next draw
        mBuffer = null;
    }
    
    @Override public void closing()
    {
        mBuffer = null;
        mCanvas = null;
        super.closing();
    }

    public void invalidateAllKeys()
    {
        mDrawPending = true;
        super.invalidateAllKeys();
    }

    protected CharSequence adjustCase(CharSequence label)
    {
        if (mKeyboard.isShifted() && (label != null) && (label.length() < 3) &&
            Character.isLowerCase(label.charAt(0)))
        {
            label = label.toString().toUpperCase();
        }
        return label;
    }

    @Override public void onDraw(Canvas canvas)
    {
        super.onDraw(canvas);
        if (mDrawPending || (mBuffer == null) || mKeyboardChanged)
        {
            onBufferDraw();
        }
        canvas.drawBitmap(mBuffer, 0, 0, null);
    }

    protected void onBufferDraw()
    {
        Log.d("KeyFlinger", "onBufferDraw()");
        if (mBuffer == null || mKeyboardChanged)
        {
            if ((mBuffer == null) || mKeyboardChanged &&
                (mBuffer.getWidth() != getWidth()) ||
                (mBuffer.getHeight() != getHeight()))
            {
                mBuffer = Bitmap.createBitmap(getWidth(), getHeight(),
                                              Bitmap.Config.ARGB_8888);
                mCanvas = new Canvas(mBuffer);
            }
            invalidateAllKeys();
            mKeyboardChanged = false;
        }

        final Canvas canvas = mCanvas;
        mDrawRect.set(0, 0, getWidth(), getHeight());
        canvas.clipRect(mDrawRect, Op.REPLACE);
        
        if (mKeyboard == null)
        {
            return;
        }
        
        final Paint paint = mPaint;
        final Key[] keys = mKeys;
        final int keyCount = keys.length;
        final int padding = (int)paint.getFontMetrics(null) / 2;

        paint.setTextSize(12);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTypeface(Typeface.DEFAULT);
        paint.setColor(0xFFFFFFFF);
        canvas.drawColor(0x00000000, PorterDuff.Mode.CLEAR);

        for (int i = 0; i < keyCount; i++)
        {
            final LatinKeyboard.LatinKey key = (LatinKeyboard.LatinKey)keys[i];
            String label = null;
            int x = 0;
            int y = 0;

            canvas.translate(key.x + getPaddingLeft(),
                             key.y + getPaddingTop());

            for (int j = 0; j < LatinKeyboard.KEY_INDEX_MAX; ++j)
            {
                if (key.mDLabels[j] == null)
                {
                    continue;
                }
                
                if (j == LatinKeyboard.KEY_INDEX_UP)
                {
                    label = adjustCase(key.mDLabels[j]).toString();
                    paint.getTextBounds(label, 0, label.length(), mTextRect);
                    x = key.width / 4 + key.mDOffsets[j][0];
                    y = Math.abs(mTextRect.top) + Math.abs(mTextRect.bottom) +
                        padding + key.mDOffsets[j][1];
                }
                else if (j == LatinKeyboard.KEY_INDEX_DOWN)
                {
                    label = adjustCase(key.mDLabels[j]).toString();
                    paint.getTextBounds(label, 0, label.length(), mTextRect);
                    x = key.width * 3 / 4 - Math.abs(mTextRect.right) +
                        key.mDOffsets[j][0];
                    y = key.height /*- Math.abs(mTextRect.bottom)*/ - padding +
                        key.mDOffsets[j][1];
                }
                else if (j == LatinKeyboard.KEY_INDEX_LEFT)
                {
                    label = adjustCase(key.mDLabels[j]).toString();
                    paint.getTextBounds(label, 0, label.length(), mTextRect);
                    x = 5 + key.mDOffsets[j][0];
                    y = (int)(key.height / 2 +
                              (paint.getTextSize() - paint.descent()) / 2) +
                        key.mDOffsets[j][1];
                }
                else if (j == LatinKeyboard.KEY_INDEX_RIGHT)
                {
                    label = adjustCase(key.mDLabels[j]).toString();
                    paint.getTextBounds(label, 0, label.length(), mTextRect);
                    x = key.width - 5 - Math.abs(mTextRect.right) +
                        key.mDOffsets[j][0];
                    y = (int)(key.height / 2 +
                              (paint.getTextSize() - paint.descent()) / 2) +
                        key.mDOffsets[j][1];
                }
                canvas.drawText(label, x, y, paint);
            }

            canvas.translate(- key.x - getPaddingLeft(),
                             - key.y - getPaddingTop());
        }

        mDrawPending = false;
    }

    @Override public void setKeyboard(Keyboard keyboard)
    {
        mKeyboard = (LatinKeyboard)keyboard;
        List<Key> keys = mKeyboard.getKeys();
        mKeys = keys.toArray(new LatinKeyboard.LatinKey[keys.size()]);

        int length = mKeys.length;
        int dimensionSum = 0;
        for (int i = 0; i < length; i++)
        {
            Key key = mKeys[i];
            dimensionSum += Math.min(key.width, key.height) + key.gap;
        }
        if (dimensionSum < 0 || length == 0) return;
        mProximityThreshold = (int) (dimensionSum * 1.4f / length);
        mProximityThreshold *= mProximityThreshold; // Square it
        mKeyboardChanged = true;
        super.setKeyboard(keyboard);
    }
    
    protected int getKeyIndices(int x, int y, int[] allKeys)
    {
        final Key[] keys = mKeys;
        int closestKey = NOT_A_KEY;
        int primaryIndex = NOT_A_KEY;
        int closestKeyDist = mProximityThreshold + 1;
        java.util.Arrays.fill(mDistances, Integer.MAX_VALUE);
        int [] nearestKeyIndices = mKeyboard.getNearestKeys(x, y);
        final int keyCount = nearestKeyIndices.length;
        for (int i = 0; i < keyCount; i++)
        {
            final Key key = keys[nearestKeyIndices[i]];
            int dist = 0;
            boolean isInside = key.isInside(x,y);
            if (((isProximityCorrectionEnabled() &&
                  ((dist = key.squaredDistanceFrom(x, y)) <
                   mProximityThreshold)) ||
                 isInside) &&
                (key.codes[0] > 32))
            {
                // Find insertion point
                final int nCodes = key.codes.length;
                if (dist < closestKeyDist)
                {
                    closestKeyDist = dist;
                    closestKey = nearestKeyIndices[i];
                }
                
                if (allKeys == null)
                {
                    continue;
                }
                
                for (int j = 0; j < mDistances.length; j++)
                {
                    if (mDistances[j] > dist)
                    {
                        // Make space for nCodes codes
                        System.arraycopy(mDistances, j, mDistances, j + nCodes,
                                         mDistances.length - j - nCodes);
                        System.arraycopy(allKeys, j, allKeys, j + nCodes,
                                         allKeys.length - j - nCodes);
                        for (int c = 0; c < nCodes; c++) {
                            allKeys[j + c] = key.codes[c];
                            mDistances[j + c] = dist;
                        }
                        break;
                    }
                }
            }
            
            if (isInside)
            {
                primaryIndex = nearestKeyIndices[i];
            }
        }
        if (primaryIndex == NOT_A_KEY)
        {
            primaryIndex = closestKey;
        }
        return primaryIndex;
    }

    protected void detectAndSendKey(int index, int code)
    {
        if (index != NOT_A_KEY && index < mKeys.length)
        {
            final Key key = mKeys[index];
            if (key.text != null)
            {
                getOnKeyboardActionListener().onText(key.text);
                getOnKeyboardActionListener().onRelease(NOT_A_KEY);
            }
            else
            {
                getOnKeyboardActionListener().onKey(code, null);
                getOnKeyboardActionListener().onRelease(code);
            }
        }
    }

    public void setParams(int touchSlop, int doubleTapSlop, int minFlingVelocity,
                          boolean longPressEnabled)
    {
        mFlingDetector.setParams(touchSlop, doubleTapSlop, minFlingVelocity,
                                 longPressEnabled);
    }
}

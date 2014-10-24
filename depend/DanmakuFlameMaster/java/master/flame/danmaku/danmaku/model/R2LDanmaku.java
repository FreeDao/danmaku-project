/*
 * Copyright (C) 2013 Chen Hui <calmer91@gmail.com>
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

package master.flame.danmaku.danmaku.model;



public class R2LDanmaku extends BaseDanmaku {
    
    protected static final long MAX_RENDERING_TIME = 100;
    
    protected static final long CORDON_RENDERING_TIME = 40;

    protected float x = 0;

    protected float y = -1;

    protected int mDistance;

    protected float[] RECT = null;

    protected float mStepX, m60FPSStepX,m30FPSStepX;

    protected long mLastTime;

    public R2LDanmaku(Duration duration) {
        this.duration = duration;
    }

    @Override
    public void layout(IDisplayer displayer, float x, float y) {
        if (mTimer != null) {
            long currMS = mTimer.currMillisecond;
            long deltaDuration = currMS - time;
            if (deltaDuration > 0 && deltaDuration < duration.value) {
                this.x = getAccurateLeft(displayer, currMS);
                if (!this.isShown()) {
                    this.y = y;
                    this.setVisibility(true);
                }
                mLastTime = currMS;
                return;
            }
            mLastTime = currMS;
        }
        this.setVisibility(false);
    }

    protected float getStableLeft(IDisplayer displayer, long currTime) {
        long elapsedTime = currTime - time;
        if (elapsedTime >= duration.value) {
            return -paintWidth;
        }
        
        long averageRenderingTime = displayer.getAverageRenderingTime();
        if (averageRenderingTime > CORDON_RENDERING_TIME || Math.abs(mLastTime - currTime) > MAX_RENDERING_TIME){
            float newX = getAccurateLeft(displayer, currTime);
            if(x - newX < m60FPSStepX) {
                newX = x - m60FPSStepX;
            }
            return newX;
        }
        
        float stepX = m60FPSStepX;
        
        if (averageRenderingTime > 0) {
            float layoutCount = (duration.value - elapsedTime) / (float) averageRenderingTime;
            stepX = (this.x + paintWidth) / layoutCount;
            if (stepX < m60FPSStepX) {
                stepX = m60FPSStepX;
            } else if (stepX > m30FPSStepX) {
                stepX = m30FPSStepX;
            }
        }
        return this.x - stepX;
    }

    protected float getAccurateLeft(IDisplayer displayer, long currTime) {
        long elapsedTime = currTime - time;
        if (elapsedTime >= duration.value) {
            return -paintWidth;
        }

        return displayer.getWidth() - elapsedTime * mStepX;
    }

    @Override
    public float[] getRectAtTime(IDisplayer displayer, long time) {
        if (!isMeasured())
            return null;
        float left = getAccurateLeft(displayer, time);
        if (RECT == null) {
            RECT = new float[4];
        }
        RECT[0] = left;
        RECT[1] = y;
        RECT[2] = left + paintWidth;
        RECT[3] = y + paintHeight;
        return RECT;
    }

    @Override
    public float getLeft() {
        return x;
    }

    @Override
    public float getTop() {
        return y;
    }

    @Override
    public float getRight() {
        return x + paintWidth;
    }

    @Override
    public float getBottom() {
        return y + paintHeight;
    }

    @Override
    public int getType() {
        return TYPE_SCROLL_RL;
    }
    
    @Override
    public void measure(IDisplayer displayer) {
        super.measure(displayer);
        mDistance = (int) (displayer.getWidth() + paintWidth);
        mStepX = mDistance / (float) duration.value;
        m60FPSStepX = mStepX * 16;
        m30FPSStepX = m60FPSStepX * 2;
        this.x = (mTimer != null ? getAccurateLeft(displayer, mTimer.currMillisecond) : displayer
                .getWidth());
    }

}

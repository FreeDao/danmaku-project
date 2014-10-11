package com.wanke.activity;

import java.lang.ref.WeakReference;

import master.flame.danmaku.DanmakuManager;
import master.flame.danmaku.ui.widget.DanmakuSurfaceView;

import org.videolan.libvlc.EventHandler;
import org.videolan.libvlc.IVideoPlayer;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaList;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Message;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.wanke.danmaku.DanmakuController;
import com.wanke.danmaku.DanmakuController.DanmakuListener;
import com.wanke.danmaku.protocol.PushChatResponse;
import com.wanke.tv.R;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class VideoActivity extends Activity implements SurfaceHolder.Callback,
        IVideoPlayer {
    public final static String TAG = "VideoActivity";

    public final static String LOCATION = "com.compdigitec.libvlcandroidsample.VideoActivity.location";

    private String mFilePath;

    // display surface
    private SurfaceView mSurface;
    private SurfaceHolder holder;

    // media player
    private LibVLC libvlc;
    private int mVideoWidth;
    private int mVideoHeight;
    private final static int VideoSizeChanged = -1;

    private View mRootView;
    private View mVideoControllContainer;

    /*************
     * Activity
     *************/

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mRootView = getLayoutInflater().inflate(R.layout.activity_video_play,
                null);
        requestFullScreen();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        setContentView(mRootView);

        // Receive path to play from intent
        Intent intent = getIntent();
        Bundle extraBundle = intent.getExtras();
        if (extraBundle != null) {
            mFilePath = extraBundle.getString(LOCATION);
        }

        mFilePath = "file:///storage/sdcard0/2.mp4";

        Log.d(TAG, "Playing back " + mFilePath);

        mSurface = (SurfaceView) findViewById(R.id.video_surface);
        holder = mSurface.getHolder();
        holder.addCallback(this);

        if (isTablet(this)) {
            mNavigationBarHeight = getNavigationBarHeight();
            Log.d(TAG, "navigation bar height:" + mNavigationBarHeight);
        }

        mVideoControllContainer = findViewById(R.id.video_controll_container);
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        layoutParams.setMargins(0, 0, 0, 0);
        mVideoControllContainer.setLayoutParams(layoutParams);

        initVideoController(mVideoControllContainer);
        initDanmaku();
    }

    // 控制是否显示弹幕
    private View mDanmakuSwitch = null;

    /**
     * 初始化视频播放的工具栏
     * 
     * @param videoController
     */
    private void initVideoController(View videoController) {
        mDanmakuSwitch = videoController.findViewById(R.id.danmuku_btn);

        mDanmakuSwitch.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                DanmakuController.getInstance().sendChat("Hello World!");
            }
        });
    }

    private DanmakuSurfaceView mDanmakuView;

    private void initDanmaku() {
        mDanmakuView = (DanmakuSurfaceView) findViewById(R.id.danmamu_surface);
        if (mDanmakuView != null) {
            DanmakuManager.getInstance()
                    .init((DanmakuSurfaceView) mDanmakuView);
            DanmakuManager.getInstance().start();
        }
    }

    /**
     * 判断当前设备是手机还是平板，代码来自Google I/O App for Android
     * 
     * @param context
     * @return 平板返回 True，手机返回 False
     */
    public static boolean isTablet(Context context) {
        return (context.getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE;
    }

    int mNavigationBarHeight = 0;

    private int getNavigationBarHeight() {
        Resources resources = getResources();
        int resourceId = resources.getIdentifier("navigation_bar_height",
                "dimen", "android");
        if (resourceId > 0) {
            return resources.getDimensionPixelSize(resourceId);
        }
        return 0;
    }

    int mUiState = 1;// 0 hide: 1: visible, 2: all visible

    final int uiHideOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;

    final int uiVisibleOptions = View.SYSTEM_UI_FLAG_VISIBLE
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public void requestFullScreen() {
        getWindow().getDecorView().setSystemUiVisibility(uiHideOptions);
        mRootView.setSystemUiVisibility(uiHideOptions);

        View fullScreenController = mRootView
                .findViewById(R.id.full_screen_controller);

        fullScreenController.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                int i = mRootView.getSystemUiVisibility();
                Log.d(TAG, "system ui visibility:" + i);

                if ((i & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) {
                    mRootView.setSystemUiVisibility(uiVisibleOptions);
                    mVideoControllContainer.setVisibility(View.INVISIBLE);
                } else if ((i & View.SYSTEM_UI_FLAG_VISIBLE) == View.SYSTEM_UI_FLAG_VISIBLE
                        && mVideoControllContainer.getVisibility() == View.INVISIBLE) {
                    showVideoControllBar();
                } else {
                    hideVideoControllBar();
                }
            }
        });
    }

    // 显示视频播放工具栏
    private void showVideoControllBar() {
        Animation animation = AnimationUtils.loadAnimation(this,
                R.anim.slide_in_bottom);
        mVideoControllContainer.setVisibility(View.VISIBLE);
        animation.setAnimationListener(new AnimationListener() {

            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                mRootView.setSystemUiVisibility(uiVisibleOptions);
                mVideoControllContainer.setVisibility(View.VISIBLE);
            }
        });
        mVideoControllContainer.startAnimation(animation);
    }

    // 隐藏视频播放工具栏
    private void hideVideoControllBar() {
        Animation animation = AnimationUtils.loadAnimation(this,
                R.anim.slide_out_bottom);
        mVideoControllContainer.setVisibility(View.INVISIBLE);
        animation.setAnimationListener(new AnimationListener() {

            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                mRootView.setSystemUiVisibility(uiHideOptions);
                mVideoControllContainer.setVisibility(View.INVISIBLE);
            }
        });
        mVideoControllContainer.startAnimation(animation);

    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setSize(mVideoWidth, mVideoHeight);
    }

    @Override
    protected void onStart() {
        super.onStart();

        createPlayer(mFilePath);
        DanmakuController.getInstance().connect(new DanmakuListener() {

            @Override
            public void onSendChatStatus(int status) {
                // 发送失败
            }

            @Override
            public void onPushChatReceive(PushChatResponse pushChatResponse) {
                // 收到弹幕
                Message message = mDanmakuHandler.obtainMessage(DANMAKU_PUSH_CAHT);
                message.obj = pushChatResponse;
                mDanmakuHandler.sendMessage(message);
            }

            @Override
            public void onLoginStatus(int status) {
                // 登录状态
            }

            @Override
            public void onConnectionStatus(int status) {
                // 连接状态
            }
        });
    }

    private final static int DANMAKU_PUSH_CAHT = 0x01;
    private Handler mDanmakuHandler = new Handler(new Callback() {

        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
            case DANMAKU_PUSH_CAHT:
                String chatContent = ((PushChatResponse) msg.obj).getContent();
                DanmakuManager.getInstance().sendDanmaku(chatContent);
                break;

            default:
                break;
            }
            return false;
        }
    });

    @Override
    protected void onStop() {
        super.onStop();

        releasePlayer();
        DanmakuController.getInstance().disconnect();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releasePlayer();
    }

    /*************
     * Surface
     *************/
    public void surfaceCreated(SurfaceHolder holder) {
    }

    public void surfaceChanged(SurfaceHolder surfaceholder, int format,
            int width, int height) {
        if (libvlc != null) {
            if (holder != null) {
                libvlc.attachSurface(holder.getSurface(), this);
            }
        }
    }

    public void surfaceDestroyed(SurfaceHolder surfaceholder) {
    }

    private void setSize(int width, int height) {
        mVideoWidth = width;
        mVideoHeight = height;
        if (mVideoWidth * mVideoHeight <= 1) {
            return;
        }

        // get screen size
        int w = getWindow().getDecorView().getWidth();
        int h = getWindow().getDecorView().getHeight();

        // getWindow().getDecorView() doesn't always take orientation into
        // account, we have to correct the values
        boolean isPortrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        if (w > h && isPortrait || w < h && !isPortrait) {
            int i = w;
            w = h;
            h = i;
        }

        float videoAR = (float) mVideoWidth / (float) mVideoHeight;
        float screenAR = (float) w / (float) h;

        if (screenAR < videoAR)
            h = (int) (w / videoAR);
        else
            w = (int) (h * videoAR);

        // force surface buffer size
        holder.setFixedSize(mVideoWidth, mVideoHeight);

        // set display size
        LayoutParams lp = mSurface.getLayoutParams();
        lp.width = w;
        lp.height = h;
        mSurface.setLayoutParams(lp);
        mSurface.invalidate();

        Log.d(TAG, "Set Video Surface View Width=" + w + ", Height=" + h);
    }

    @Override
    public void setSurfaceSize(int width, int height, int visible_width,
            int visible_height, int sar_num, int sar_den) {
        Message msg = Message.obtain(mHandler, VideoSizeChanged, width, height);
        msg.sendToTarget();
    }

    /*************
     * Player
     *************/

    private void createPlayer(String media) {
        releasePlayer();
        try {
            if (media.length() > 0) {
                Toast toast = Toast.makeText(this, media, Toast.LENGTH_LONG);
                toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0,
                        0);
                toast.show();
            }

            // Create a new media player
            libvlc = LibVLC.getInstance();
            libvlc.setHardwareAcceleration(LibVLC.HW_ACCELERATION_DISABLED);
            libvlc.setSubtitlesEncoding("");
            libvlc.setAout(LibVLC.AOUT_OPENSLES);
            libvlc.setTimeStretching(true);
            libvlc.setChroma("RV32");
            libvlc.setVerboseMode(true);
            // LibVLC.restart(this);
            libvlc.init(this);
            EventHandler.getInstance().addHandler(mHandler);
            holder.setFormat(PixelFormat.RGBX_8888);
            holder.setKeepScreenOn(true);
            MediaList list = libvlc.getMediaList();
            list.clear();
            list.add(new Media(libvlc, media));
            libvlc.playIndex(0);
        } catch (Exception e) {
            Toast.makeText(this, "Error creating player!", Toast.LENGTH_LONG)
                    .show();
            Log.d(TAG, "Create Player Exception:" + e.toString());
        }
    }

    private void releasePlayer() {
        if (libvlc == null)
            return;
        EventHandler.getInstance().removeHandler(mHandler);
        libvlc.stop();
        libvlc.detachSurface();
        holder = null;
        libvlc.closeAout();
        libvlc.destroy();
        libvlc = null;

        mVideoWidth = 0;
        mVideoHeight = 0;
    }

    /*************
     * Events
     *************/
    private Handler mHandler = new MyHandler(this);

    private static class MyHandler extends Handler {
        private WeakReference<VideoActivity> mOwner;

        public MyHandler(VideoActivity owner) {
            mOwner = new WeakReference<VideoActivity>(owner);
        }

        @Override
        public void handleMessage(Message msg) {
            VideoActivity player = mOwner.get();

            // SamplePlayer events
            if (msg.what == VideoSizeChanged) {
                player.setSize(msg.arg1, msg.arg2);
                return;
            }

            // Libvlc events
            Bundle b = msg.getData();
            switch (b.getInt("event")) {
            case EventHandler.MediaPlayerEndReached:
                player.releasePlayer();
                break;
            case EventHandler.MediaPlayerPlaying:
            case EventHandler.MediaPlayerPaused:
            case EventHandler.MediaPlayerStopped:
            default:
                break;
            }
        }
    }
}
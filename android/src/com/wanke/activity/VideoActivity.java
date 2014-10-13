package com.wanke.activity;

import java.lang.ref.WeakReference;

import master.flame.danmaku.ui.widget.DanmakuSurfaceView;

import org.videolan.libvlc.EventHandler;
import org.videolan.libvlc.IVideoPlayer;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaList;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.wanke.danmaku.DanmakuController;
import com.wanke.danmaku.DanmakuController.DanmakuListener;
import com.wanke.danmaku.DanmakuManager;
import com.wanke.danmaku.protocol.PushChatResponse;
import com.wanke.tv.R;
import com.wanke.ui.ToastUtil;

@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
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
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

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

        mFilePath = "file:///storage/sdcard0/1.mp4";

        Log.d(TAG, "Playing back " + mFilePath);

        mSurface = (SurfaceView) findViewById(R.id.video_surface);
        holder = mSurface.getHolder();
        holder.addCallback(this);

        initVideoPanel();
        initDanmaku();
    }

    // 控制是否显示弹幕
    private View mDanmakuSwitch = null;
    private TextView mElapseTime = null;

    private View mTopPanel = null;

    /**
     * 初始化视频播放的工具栏
     * 
     * @param videoController
     */
    private void initVideoPanel() {
        mNavigationBarHeight = getNavigationBarHeight();
        Log.d(TAG, "navigation bar height:" + mNavigationBarHeight);

        mVideoControllContainer = findViewById(R.id.video_controll_container);
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        if (!isTablet(this)) {
            layoutParams.setMargins(0, 0, mNavigationBarHeight, 0);
        } else {
            layoutParams.setMargins(0, 0, 0, 0);
        }
        mVideoControllContainer.setLayoutParams(layoutParams);

        // init top panel
        mTopPanel = findViewById(R.id.video_top_panel);
        layoutParams = new RelativeLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        if (!isTablet(this)) {
            layoutParams.setMargins(0, 0, mNavigationBarHeight, 0);
        } else {
            layoutParams.setMargins(0, 0, 0, 0);
        }
        mTopPanel.setLayoutParams(layoutParams);

        mDanmakuSwitch = mVideoControllContainer.findViewById(R.id.danmuku_btn);

        mDanmakuSwitch.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                DanmakuController.getInstance().sendChat("Hello World!");
                //                DanmakuManager.getInstance().sendDanmaku("Hello World!");
            }
        });

        mElapseTime = (TextView) mVideoControllContainer.findViewById(R.id.elapse_time);
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

    private void deinitDanmaku() {
        DanmakuManager.getInstance().deinit();
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

    private int uiHideOptions = 0;
    private int uiVisibleOptions = 0;

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    public void requestFullScreen() {
        uiHideOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;

        uiVisibleOptions = View.SYSTEM_UI_FLAG_VISIBLE
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;

        getWindow().getDecorView()
                .setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
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
                    showVideoBar();
                } else {
                    hideVideoBar();
                }
            }
        });
    }

    // 显示视频播放工具栏
    private void showVideoBar() {
        Animation animation = AnimationUtils.loadAnimation(this,
                R.anim.slide_in_bottom);
        mVideoControllContainer.setVisibility(View.VISIBLE);
        mTopPanel.setVisibility(View.VISIBLE);
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

        mTopPanel.startAnimation(AnimationUtils.loadAnimation(this,
                R.anim.slide_in_top));
    }

    // 隐藏视频播放工具栏
    private void hideVideoBar() {
        Animation animation = AnimationUtils.loadAnimation(this,
                R.anim.slide_out_bottom);
        mVideoControllContainer.setVisibility(View.INVISIBLE);
        mTopPanel.setVisibility(View.INVISIBLE);
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
        mTopPanel.startAnimation(AnimationUtils.loadAnimation(this,
                R.anim.slide_out_top));
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
                Log.d(TAG, "Login Status:" + status);
                // 登录状态
                if (status < 0) {
                    mHandler.sendEmptyMessage(DANMAKU_LOGIN_FAILED);
                }
            }

            @Override
            public void onConnectionStatus(int status) {
                // 连接状态
                Log.d(TAG, "Connection Status:" + status);
                if (status < 0) {
                    mHandler.sendEmptyMessage(DANMAKU_CONNECTION_FAILED);
                }
            }
        });
    }

    private final static int DANMAKU_PUSH_CAHT = 0x01;

    // 连接弹幕服务器失败
    private final static int DANMAKU_CONNECTION_FAILED = 0x00010001;
    // 登录弹幕服务器失败
    private final static int DANMAKU_LOGIN_FAILED = 0x00010002;
    private Handler mDanmakuHandler = new Handler(new Callback() {

        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
            case DANMAKU_PUSH_CAHT:
                String chatContent = ((PushChatResponse) msg.obj).getContent();
                DanmakuManager.getInstance().sendDanmaku(chatContent);
                break;

            case DANMAKU_CONNECTION_FAILED:
                ToastUtil.showToast(VideoActivity.this,
                        R.string.danmaku_connection_failed);
                break;

            case DANMAKU_LOGIN_FAILED:
                ToastUtil.showToast(VideoActivity.this,
                        R.string.danmaku_login_failed);
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
        deinitDanmaku();
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

    @Override
    protected void onResume() {
        super.onResume();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_TICK);
        registerReceiver(mTimeTickReceiver, filter);
    }

    @Override
    protected void onPause() {
        super.onPause();

        unregisterReceiver(mTimeTickReceiver);
    }

    private final BroadcastReceiver mTimeTickReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_TIME_TICK)) {
            }
        }
    };
}

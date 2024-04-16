/*
 * Copyright (C) 2015 Bilibili
 * Copyright (C) 2015 Zhang Rui <bbcallen@gmail.com>
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
package tv.danmaku.ijk.media.example.activities;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TableLayout;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import tv.danmaku.ijk.media.example.R;
import tv.danmaku.ijk.media.example.fragments.TracksFragment;
import tv.danmaku.ijk.media.ijkplayerview.utils.Settings;
import tv.danmaku.ijk.media.ijkplayerview.widget.media.AndroidMediaController;
import tv.danmaku.ijk.media.ijkplayerview.widget.media.IRenderView;
import tv.danmaku.ijk.media.ijkplayerview.widget.media.IjkVideoView;
import tv.danmaku.ijk.media.ijkplayerview.widget.media.MeasureHelper;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;
import tv.danmaku.ijk.media.player.misc.ITrackInfo;

/**
 * ijkplayer 视频播放类.
 */
public class VideoActivity extends AppCompatActivity implements TracksFragment.ITrackHolder {
    private static final String TAG = "VideoActivity";

    private String mVideoPath;

    private AndroidMediaController mMediaController;
    private IjkVideoView mVideoView;
    private TextView mToastTextView;
    private TableLayout mHudView;
    private DrawerLayout mDrawerLayout;
    private ViewGroup mRightDrawer;
    private Settings mSettings;
    private boolean mBackPressed;
    private long mLastStartTime = 0;
    private SharedPreferences mSharedPreferences;

    public static Intent newIntent(Context context, String videoPath, String videoTitle) {
        Intent intent = new Intent(context, VideoActivity.class);
        intent.putExtra("videoPath", videoPath);
        intent.putExtra("videoTitle", videoTitle);
        return intent;
    }

    public static void intentTo(Context context, String videoPath, String videoTitle) {
        context.startActivity(newIntent(context, videoPath, videoTitle));
    }

    private static boolean isEmptyBitmap(final Bitmap src) {
        return src == null || src.getWidth() == 0 || src.getHeight() == 0;
    }

    public static boolean createOrExistsDir(final File file) {
        return file != null && (file.exists() ? file.isDirectory() : file.mkdirs());
    }

    public static boolean createFileByDeleteOldFile(final File file) {
        if (file == null) return false;
        // file exists and unsuccessfully delete then return false
        if (file.exists() && !file.delete()) return false;
        if (!createOrExistsDir(file.getParentFile())) return false;
        try {
            return file.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean save(final Bitmap src,
                               final File file,
                               final Bitmap.CompressFormat format,
                               final int quality,
                               final boolean recycle) {
        if (isEmptyBitmap(src)) {
            Log.e("ImageUtils", "bitmap is empty.");
            return false;
        }
        if (src.isRecycled()) {
            Log.e("ImageUtils", "bitmap is recycled.");
            return false;
        }
        if (!createFileByDeleteOldFile(file)) {
            Log.e("ImageUtils", "create or delete file <" + file + "> failed.");
            return false;
        }
        OutputStream os = null;
        boolean ret = false;
        try {
            os = new BufferedOutputStream(new FileOutputStream(file));
            ret = src.compress(format, quality, os);
            if (recycle && !src.isRecycled()) src.recycle();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (os != null) {
                    os.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return ret;
    }

    public boolean snapshotPicture() {
        IjkMediaPlayer iMediaPlayer = (IjkMediaPlayer) mVideoView.mMediaPlayer;
        int width = iMediaPlayer.getVideoWidth();
        int height = iMediaPlayer.getVideoHeight();
        Bitmap srcBitmap = Bitmap.createBitmap(width,
                height, Bitmap.Config.ARGB_8888);
        boolean flag = iMediaPlayer.getCurrentFrame(srcBitmap);
        if (flag) {
            // 保存图片
            String path = getFilesDir().getPath() + "/ijkplayer/snapshot";
            File screenshotsDirectory = new File(path);
            if (!screenshotsDirectory.exists()) {
                screenshotsDirectory.mkdirs();
            }
            File savePath = new File(
                    screenshotsDirectory.getPath()
                            + "/"
                            + new SimpleDateFormat("yyyyMMddHHmmss")
                            .format(new Date()) + ".jpg");
            save(srcBitmap, savePath, Bitmap.CompressFormat.PNG, 100, false);
        }
        return flag;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        TextView screenShotBtn = (TextView) findViewById(R.id.screenShotBtn);
        TextView startRecordBtn = (TextView) findViewById(R.id.startRecordBtn);
        TextView stopRecordBtn = (TextView) findViewById(R.id.stopRecordBtn);

        screenShotBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                snapshotPicture();
            }
        });
        startRecordBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                IjkMediaPlayer iMediaPlayer = (IjkMediaPlayer) mVideoView.mMediaPlayer;
                String path = getFilesDir().getPath() + "/"
                        + new SimpleDateFormat("yyyyMMddHHmmss")
                        .format(new Date()) + ".mp4";
                iMediaPlayer.startRecord(path);
            }
        });
        stopRecordBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                IjkMediaPlayer iMediaPlayer = (IjkMediaPlayer) mVideoView.mMediaPlayer;
                iMediaPlayer.stopRecord();
            }
        });

        mSettings = new Settings(this);
        //设置启用exoPlayer.
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String key = this.getString(tv.danmaku.ijk.media.ijkplayerview.R.string.pref_key_player);
        mSharedPreferences.edit().putString(key, String.valueOf(Settings.PV_PLAYER__IjkMediaPlayer)).apply();

        // handle arguments
//        mVideoPath = "rtsp://rtsp-test-server.viomic.com:554/stream";//IPC - h264.
        mVideoPath = "rtsp://admin:RFYOPK@10.1.11.27:554/h264/ch1/main/av_stream";//IPC - h264.
//        mVideoPath = "https://file-examples.com/storage/feed2327706616bd9a07caa/2017/04/file_example_MP4_640_3MG.mp4";//IPC - h264.

        // init UI
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ActionBar actionBar = getSupportActionBar();
        mMediaController = new AndroidMediaController(this, false);
        mMediaController.setSupportActionBar(actionBar);

        mToastTextView = (TextView) findViewById(R.id.toast_text_view);
        mHudView = (TableLayout) findViewById(R.id.hud_view);
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mRightDrawer = (ViewGroup) findViewById(R.id.right_drawer);

        mDrawerLayout.setScrimColor(Color.TRANSPARENT);

        // init player
        IjkMediaPlayer.loadLibrariesOnce(null);
        IjkMediaPlayer.native_profileBegin(IjkMediaPlayer.IJK_LIB_NAME_FFMPEG);

        mVideoView = (IjkVideoView) findViewById(R.id.video_view);
        mVideoView.setMediaController(mMediaController);
        mVideoView.setHudView(mHudView);
        mVideoView.setRender(IjkVideoView.RENDER_TEXTURE_VIEW);
        //打开opense,h264下有效.
        mVideoView.setAudioHardWare(true);
//        mVideoView.setLogLevel(IjkMediaPlayer.IJK_LOG_DEBUG);
        //set the headers properties in user-agent.
        mVideoView.setUserAgentStr("Android_Station_V1.1.1");
        //设置h265
        if (mVideoPath.startsWith("rtsp")) {
            mVideoView.setH265(true);
            //mVideoView.openZeroVideoDelay(true);
        } else {
            //打开视频0延迟.
            //mVideoView.openZeroVideoDelay(true);
        }

        // prefer mVideoPath
        if (mVideoPath != null)
            mVideoView.setVideoPath(mVideoPath, IjkVideoView.IJK_TYPE_LIVING_WATCH);
        else {
            Log.e(TAG, "Null Data Source\n");
            finish();
            return;
        }

        //准备就绪，做一些配置操作，比如音视频同步方式.
        mVideoView.setOnPreparedListener(new IMediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(IMediaPlayer mp) {
                Log.e(TAG, "onPrepared#done! ");
                //mVideoView.openZeroVideoDelay(true);
            }
        });

        mVideoView.setOnInfoListener(new IMediaPlayer.OnInfoListener() {
            @Override
            public boolean onInfo(IMediaPlayer mp, int what, int extra) {
                Log.e(TAG, "onInfo#position: " + mp.getCurrentPosition() + " what: " + what + " extra: " + extra);
                if (IjkMediaPlayer.MP_STATE_PREPARED == what) {
                    long takeTime = SystemClock.currentThreadTimeMillis() - mLastStartTime;
                    Log.i("poe", "加载视频prepare耗时#=====================> " + takeTime + " ms");
                    // DO: 2020/3/31 真正的准备完成了，准备播放 ，回调到外面通知状态改变！。
                }
                return false;

            }
        });


        mVideoView.setAspectRatio(IRenderView.AR_16_9_FIT_PARENT);
        mLastStartTime = SystemClock.currentThreadTimeMillis();
        Log.i(TAG, "start play ~~ #  " + mLastStartTime);
        mVideoView.start();
    }

    public void onBackPressed() {
        mBackPressed = true;
        super.onBackPressed();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i("poe", "onStop()");
        if (mBackPressed || !mVideoView.isBackgroundPlayEnabled()) {
            mVideoView.stopPlayback();
            mVideoView.release(true);
            mVideoView.stopBackgroundPlay();
        } else {
            mVideoView.enterBackground();
        }
        IjkMediaPlayer.native_profileEnd();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_player, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_toggle_ratio) {
            int aspectRatio = mVideoView.toggleAspectRatio();
            String aspectRatioText = MeasureHelper.getAspectRatioText(this, aspectRatio);
            mToastTextView.setText(aspectRatioText);
            mMediaController.showOnce(mToastTextView);
            return true;
        } else if (id == R.id.action_toggle_player) {
            int player = mVideoView.togglePlayer();
            String playerText = IjkVideoView.getPlayerText(this, player);
            mToastTextView.setText(playerText);
            mMediaController.showOnce(mToastTextView);
            return true;
        } else if (id == R.id.action_toggle_render) {
            int render = mVideoView.toggleRender();
            String renderText = IjkVideoView.getRenderText(this, render);
            mToastTextView.setText(renderText);
            mMediaController.showOnce(mToastTextView);
            return true;
        } else if (id == R.id.action_show_info) {
            mVideoView.showMediaInfo();
        } else if (id == R.id.action_show_tracks) {
            if (mDrawerLayout.isDrawerOpen(mRightDrawer)) {
                Fragment f = getSupportFragmentManager().findFragmentById(R.id.right_drawer);
                if (f != null) {
                    FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                    transaction.remove(f);
                    transaction.commit();
                }
                mDrawerLayout.closeDrawer(mRightDrawer);
            } else {
                Fragment f = TracksFragment.newInstance();
                FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                transaction.replace(R.id.right_drawer, f);
                transaction.commit();
                mDrawerLayout.openDrawer(mRightDrawer);
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public ITrackInfo[] getTrackInfo() {
        if (mVideoView == null)
            return null;
        return mVideoView.getTrackInfo();
    }

    @Override
    public void selectTrack(int stream) {
        mVideoView.selectTrack(stream);
    }

    @Override
    public void deselectTrack(int stream) {
        mVideoView.deselectTrack(stream);
    }

    @Override
    public int getSelectedTrack(int trackType) {
        if (mVideoView == null)
            return -1;
        return mVideoView.getSelectedTrack(trackType);
    }
}

package com.offline.conversion;

import static android.Manifest.permission.CAMERA;
import static android.Manifest.permission.READ_PHONE_STATE;
import static android.Manifest.permission.RECORD_AUDIO;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static io.agora.rtc2.Constants.RENDER_MODE_HIDDEN;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatEditText;
import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.TextureView;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Toast;
import io.agora.rtc2.ChannelMediaOptions;
import io.agora.rtc2.Constants;
import io.agora.rtc2.IMediaExtensionObserver;
import io.agora.rtc2.IRtcEngineEventHandler;
import io.agora.rtc2.RtcEngine;
import io.agora.rtc2.RtcEngineConfig;
import io.agora.rtc2.video.VideoCanvas;

public class MainActivity extends AppCompatActivity implements IMediaExtensionObserver, SeekBar.OnSeekBarChangeListener {

    private final static String APPID = "d19e2d42f90142b0889efaaa9883477f";

    public static final String EXTENSION_NAME = "databaker_offlinevc"; // Name of target link library used in CMakeLists.txt
    public static final String EXTENSION_VENDOR_NAME = "DataBaker"; // Provider name used for registering in agora-bytedance.cpp
    public static final String EXTENSION_AUDIO_FILTER_VOLUME = "OfflineVoiceConversion"; // Audio filter name defined in ExtensionProvider.h



    private String configJson = "{\"clientId\": \"xxx...\",\"clientSecret\": \"xxx...\",\"voiceName\": \"Vc_luoli\",\"isLog\": \"fasle\",\"enable\": \"fasle\"}\n";

    public final static String TAG = "TAG----->MainActivity";
    private RtcEngine engine;
    private boolean joined = false;
    private int myUid;

    private FrameLayout flLocal;
    private FrameLayout flRemote;
    private SeekBar recordingVol;
    private LinearLayout llJoin;
    private AppCompatEditText etChannel;
    private Button btnJoin;
    private Button btnInit;
    private RadioGroup rgYinse;
    private Button btnEnable;
    private Button btnLog;

    private final Handler mHandler = new Handler();
    String[] permissions = {CAMERA, RECORD_AUDIO, WRITE_EXTERNAL_STORAGE, READ_PHONE_STATE};

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (Utils.hasPermission(this, permissions)){
            initView();
        }else {
            Toast.makeText(this, "请赋予需要使用的权限", Toast.LENGTH_LONG).show();
        }

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getSupportActionBar().hide();
        if (Utils.hasPermission(this, permissions)) {
            initView();
        } else {
            requestPermissions(permissions, 1001);
        }
    }


    @SuppressLint("NonConstantResourceId")
    private void initView() {
        flLocal = (FrameLayout) findViewById(R.id.fl_local);
        flRemote = (FrameLayout) findViewById(R.id.fl_remote);
        recordingVol = (SeekBar) findViewById(R.id.recordingVol);
        llJoin = (LinearLayout) findViewById(R.id.ll_join);
        etChannel = (AppCompatEditText) findViewById(R.id.et_channel);
        btnJoin = (Button) findViewById(R.id.btn_join);

        btnInit = (Button) findViewById(R.id.btn_init);

        rgYinse = (RadioGroup) findViewById(R.id.rg_yinse);
        etChannel = (AppCompatEditText) findViewById(R.id.et_channel);


        btnEnable = (Button) findViewById(R.id.btn_enable);
        btnLog = (Button) findViewById(R.id.btn_log);

        btnEnable.setOnClickListener(view->{
            if (btnEnable.getText().equals("开启插件")){
                engine.setExtensionProperty(EXTENSION_VENDOR_NAME, EXTENSION_AUDIO_FILTER_VOLUME, "enable", "true");
                btnEnable.setText("关闭插件");
            }else {
                engine.setExtensionProperty(EXTENSION_VENDOR_NAME, EXTENSION_AUDIO_FILTER_VOLUME, "enable", "false");
                btnEnable.setText("开启插件");
            }
        });

        btnLog.setOnClickListener(view->{
            if (btnLog.getText().equals(getResources().getString(R.string.start_log))){
                engine.setExtensionProperty(EXTENSION_VENDOR_NAME, EXTENSION_AUDIO_FILTER_VOLUME, "isLog", "true");
                btnLog.setText(getResources().getString(R.string.close_log));
            }else {
                engine.setExtensionProperty(EXTENSION_VENDOR_NAME, EXTENSION_AUDIO_FILTER_VOLUME, "isLog", "false");
                btnLog.setText(getResources().getString(R.string.start_log));
            }
        });


         recordingVol.setOnSeekBarChangeListener(this);
        btnInit.setOnClickListener(view -> initSdk());
        rgYinse.setOnCheckedChangeListener((group, checkedId) -> {
            String voiceName;
            switch (checkedId) {
                default:
                    voiceName = "Vc_luoli";
                    break;
                case R.id.rb_dashu:
                    voiceName = "Vc_dashu";
                    break;
                case R.id.rb_kongling:
                    voiceName = "Vc_kongling";
                    break;
                case R.id.rb_bawanglong:
                    voiceName = "Vc_bawanglong";
                    break;
                case R.id.rb_zhongjinshu:
                    voiceName = "Vc_zhongjinshu";
                    break;
            }
            if (joined) {
                engine.setExtensionProperty(EXTENSION_VENDOR_NAME, EXTENSION_AUDIO_FILTER_VOLUME, "voiceName", voiceName);
                Toast.makeText(this, "设置成功", Toast.LENGTH_LONG).show();
            }
        });


        btnJoin.setOnClickListener(view -> {
            if (!joined) {
                String channelId = etChannel.getText().toString();
                joinChannel(channelId);
            } else {
                joined = false;
                engine.leaveChannel();
                btnJoin.setText("加入");
                recordingVol.setEnabled(false);
                recordingVol.setProgress(0);
            }
        });

    }

    private void joinChannel(String channelId) {

        String accessToken = getString(R.string.agora_access_token);
        if (TextUtils.equals(accessToken, "") || TextUtils.equals(accessToken, "<#YOUR ACCESS TOKEN#>")) {
            accessToken = null;
        }
        engine.enableAudioVolumeIndication(1000, 3, false);
        ChannelMediaOptions option = new ChannelMediaOptions();
        option.autoSubscribeAudio = true;
        int res = engine.joinChannel(accessToken, channelId, 0, option);
        if (res != 0) {
            Log.e(TAG, RtcEngine.getErrorDescription(Math.abs(res)));
            return;
        }
        btnJoin.setEnabled(false);
    }

    private void initSdk() {
        try {
            RtcEngineConfig config = new RtcEngineConfig();
            config.mContext = getApplicationContext();
            config.mAppId = APPID;
            config.mChannelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION;
            config.addExtension(EXTENSION_NAME);
            config.mExtensionObserver = this;
            config.mEventHandler = iRtcEngineEventHandler;
            engine = RtcEngine.create(config);
            engine.enableExtension(EXTENSION_VENDOR_NAME, EXTENSION_AUDIO_FILTER_VOLUME, true);

            engine.enableVideo();
            TextureView textureView = new TextureView(this);
            if (flLocal.getChildCount() > 0) {
                flLocal.removeAllViews();
            }
            flLocal.addView(textureView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            engine.setupLocalVideo(new VideoCanvas(textureView, RENDER_MODE_HIDDEN, 0));
            engine.startPreview();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private final IRtcEngineEventHandler iRtcEngineEventHandler = new IRtcEngineEventHandler() {

        @Override
        public void onWarning(int warn) {
            Log.w(TAG, String.format("onWarning code %d message %s", warn, RtcEngine.getErrorDescription(warn)));
        }


        @Override
        public void onLeaveChannel(RtcStats stats) {
            super.onLeaveChannel(stats);
            Log.i(TAG, String.format("local user %d leaveChannel!", myUid));
        }

        @Override
        public void onJoinChannelSuccess(String channel, int uid, int elapsed) {
            Log.i(TAG, String.format("onJoinChannelSuccess channel %s uid %d", channel, uid));
            myUid = uid;
            joined = true;
            mHandler.post(() -> {
                btnJoin.setEnabled(true);
                btnJoin.setText("离开");
                recordingVol.setEnabled(true);
                recordingVol.setProgress(100);
            });
        }


        @Override
        public void onUserJoined(int uid, int elapsed) {
            super.onUserJoined(uid, elapsed);
            Log.i(TAG, "onUserJoined->" + uid);

            mHandler.post(() -> {
                if (flRemote.getChildCount() > 0) {
                    flRemote.removeAllViews();
                }
                TextureView textureView = null;
                textureView = new TextureView(MainActivity.this);
                flRemote.addView(textureView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                engine.setupRemoteVideo(new VideoCanvas(textureView, RENDER_MODE_HIDDEN, uid));
            });

        }


        @Override
        public void onUserOffline(int uid, int reason) {
            Log.i(TAG, String.format("user %d offline! reason:%d", uid, reason));
            mHandler.post((Runnable) () -> {
                engine.setupRemoteVideo(new VideoCanvas(null, RENDER_MODE_HIDDEN, uid));
            });
        }

        @Override
        public void onActiveSpeaker(int uid) {
            super.onActiveSpeaker(uid);
            Log.i(TAG, String.format("onActiveSpeaker:%d", uid));
        }
    };


    //**********************************SDK 重写**********************************

    @Override
    public void onEvent(String provider, String extension, String key, String value) {
        runOnUiThread(() -> {
            if (provider.equals("DataBaker")){
                Toast.makeText(this,"错误信息:"+value,Toast.LENGTH_LONG).show();
            }
        });

        Log.e("TAGTAG----->MainActivity", "onEvent provider: " + provider + "  extension: " + extension + "  key: " + key + "  value: " + value);
    }

    @Override
    public void onStarted(String provider, String extension) {

    }

    @Override
    public void onStopped(String provider, String extension) {

    }

    @Override
    public void onError(String provider, String extension, int error, String message) {

    }


    //**********************************Seekbar**********************************
    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (joined && seekBar.getId() == recordingVol.getId()) {
            engine.setExtensionProperty(EXTENSION_VENDOR_NAME, EXTENSION_AUDIO_FILTER_VOLUME, "databaker", configJson);
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }
}
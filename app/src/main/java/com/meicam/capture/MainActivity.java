package com.meicam.capture;

import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.Environment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.Switch;

import com.meicam.sdk.*;

import java.io.File;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements NvsStreamingContext.CaptureDeviceCallback {

    private static final String TAG = "Capture";
    private static final int REQUEST_CAMERA_PERMISSION_CODE = 0;
    private static final int REQUEST_RECORD_AUDIO_PERMISSION_CODE = 1;
    private static final int REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_CODE = 2;

    private NvsLiveWindow m_liveWindow;
    private Button m_buttonRecord;
    private Switch m_switchBackFacing;
    private Switch m_switchFlash;
    private Switch m_switchAutoFocus;
    private SeekBar m_seekBarZoom;
    private SeekBar m_seekBarExporsure;
    private ImageView m_imageAutoFocusRect;
    private ListView m_listViewCaptureFx;

    private int m_currentDeviceIndex;
    private NvsStreamingContext m_streamingContext;
    private NvsRational m_aspectRatio;
    private StringBuilder m_fxPackageId;
    private ArrayList m_lstCaptureFx;
    private int m_minExporsure;
    private boolean m_permissionGranted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        m_streamingContext = NvsStreamingContext.init(this, null);
        setContentView(R.layout.activity_main);

        m_liveWindow = (NvsLiveWindow) findViewById(R.id.liveWindow);
        m_buttonRecord = (Button) findViewById(R.id.buttonRecord);
        m_switchBackFacing = (Switch) findViewById(R.id.switchBackFacing);
        m_switchFlash = (Switch) findViewById(R.id.switchFlash);
        m_switchAutoFocus = (Switch) findViewById(R.id.switchAutoFocus);
        m_seekBarZoom = (SeekBar) findViewById(R.id.seekBarZoom);
        m_seekBarExporsure = (SeekBar) findViewById(R.id.seekBarExporsure);
        m_imageAutoFocusRect = (ImageView) findViewById(R.id.imageAutoFocusRect);
        m_listViewCaptureFx = (ListView) findViewById(R.id.listViewFx);

        m_switchBackFacing.setEnabled(false);
        resetSettings();

        m_currentDeviceIndex = 0;
        m_minExporsure = 0;
        m_permissionGranted = false;

        if (null == m_streamingContext)
            return;

        m_streamingContext.setCaptureDeviceCallback(this);
        if (m_streamingContext.getCaptureDeviceCount() == 0)
            return;

        if (!m_streamingContext.connectCapturePreviewWithLiveWindow(m_liveWindow)) {
            Log.d(TAG, "连接预览窗口失败");
            return;
        }

        if (m_streamingContext.getCaptureDeviceCount() > 1)
            m_switchBackFacing.setEnabled(true);

        if (m_streamingContext.isCaptureDeviceBackFacing(0))
            m_switchBackFacing.setChecked(false);
        else
            m_switchBackFacing.setChecked(true);

        m_aspectRatio = new NvsRational(1, 1);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)) {
                if (PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)) {
                    if (PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                        m_permissionGranted = true;
                        if (!m_streamingContext.startCapturePreview(0, NvsStreamingContext.VIDEO_CAPTURE_RESOLUTION_GRADE_HIGH, 0, m_aspectRatio))
                            return;
                    } else {
                        setCaptureEnabled(false);
                        requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_CODE);
                    }
                } else {
                    setCaptureEnabled(false);
                    requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION_CODE);
                }
            }
            else {
                setCaptureEnabled(false);
                requestPermissions(new String[]{android.Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION_CODE);
            }
        } else {
            m_permissionGranted = true;
            if (!m_streamingContext.startCapturePreview(0, NvsStreamingContext.VIDEO_CAPTURE_RESOLUTION_GRADE_HIGH, 0, m_aspectRatio))
                return;
        }

        // install package
        boolean package1Valid = true;
        String package1Path = "assets:/7FFCF99A-5336-4464-BACD-9D32D5D2DC5E.videofx";
        m_fxPackageId = new StringBuilder();
        int error = m_streamingContext.getAssetPackageManager().installAssetPackage(package1Path, null, NvsAssetPackageManager.ASSET_PACKAGE_TYPE_VIDEOFX, true, m_fxPackageId);
        if (error != NvsAssetPackageManager.ASSET_PACKAGE_MANAGER_ERROR_NO_ERROR
                && error != NvsAssetPackageManager.ASSET_PACKAGE_MANAGER_ERROR_ALREADY_INSTALLED) {
            Log.d(TAG, "Failed to install asset package!");
            package1Valid = false;
        }

        m_lstCaptureFx = new ArrayList();
        m_lstCaptureFx.add("None");
        m_lstCaptureFx.add("Beauty");
        m_lstCaptureFx.addAll(m_streamingContext.getAllBuiltinCaptureVideoFxNames());
        if (package1Valid)
            m_lstCaptureFx.add("Package1");

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(MainActivity.this, android.R.layout.simple_list_item_1, m_lstCaptureFx);
        m_listViewCaptureFx.setAdapter(adapter);

        m_listViewCaptureFx.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, final int position, long id) {
                m_streamingContext.removeAllCaptureVideoFx();
                String fxName = String.valueOf(m_lstCaptureFx.get(position));
                if (fxName == "None")
                    return;
                if (fxName == "Beauty") {
                    m_streamingContext.appendBeautyCaptureVideoFx();
                    return;
                }
                if (fxName == "Package1") {
                    m_streamingContext.appendPackagedCaptureVideoFx(m_fxPackageId.toString());
                    return;
                }
                m_streamingContext.appendBuiltinCaptureVideoFx(fxName);
            }
        });

        m_buttonRecord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getCurrentEngineState() == m_streamingContext.STREAMING_ENGINE_STATE_CAPTURERECORDING) {
                    m_streamingContext.stopRecording();
                    m_buttonRecord.setText(R.string.record);
                    if (m_streamingContext.getCaptureDeviceCount() > 1)
                        m_switchBackFacing.setEnabled(true);
                    return;
                }

                File captureDir = new File(Environment.getExternalStorageDirectory(), "StreamingSdk" + File.separator + "Record");
                if (!captureDir.exists() && !captureDir.mkdirs()) {
                    Log.d(TAG, "Failed to make Record directory");
                    return;
                }

                File file = new File(captureDir, "capture.mp4");
                if (file.exists())
                    file.delete();
                Log.d(TAG, file.getAbsolutePath());

                if (!m_streamingContext.startRecording(file.getAbsolutePath()))
                    return;

                m_buttonRecord.setText(R.string.stop);
                m_switchBackFacing.setEnabled(false);
            }
        });

        m_switchBackFacing.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                resetSettings();
                if (isChecked) {
                    if (!m_streamingContext.startCapturePreview(1, NvsStreamingContext.VIDEO_CAPTURE_RESOLUTION_GRADE_HIGH, 0, m_aspectRatio)) {
                        Log.d(TAG, "启动预览失败");
                        return;
                    }
                    m_currentDeviceIndex = 1;
                }
                else {
                    if (!m_streamingContext.startCapturePreview(0, NvsStreamingContext.VIDEO_CAPTURE_RESOLUTION_GRADE_HIGH, 0, m_aspectRatio)) {
                        Log.d(TAG, "启动预览失败");
                        return;
                    }
                    m_currentDeviceIndex = 0;
                }
                m_buttonRecord.setEnabled(true);
            }
        });

        m_switchFlash.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                m_streamingContext.toggleFlash(isChecked);
            }
        });

        m_switchAutoFocus.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    m_imageAutoFocusRect.setX((m_liveWindow.getWidth() - m_imageAutoFocusRect.getWidth()) / 2);
                    m_imageAutoFocusRect.setY((m_liveWindow.getHeight() - m_imageAutoFocusRect.getHeight()) / 2);
                    Rect rectFrame = new Rect();
                    m_imageAutoFocusRect.getWindowVisibleDisplayFrame(rectFrame);
                    m_imageAutoFocusRect.setVisibility(View.VISIBLE);
                    m_streamingContext.startAutoFocus(new RectF(rectFrame));
                } else {
                    m_streamingContext.cancelAutoFocus();
                    m_imageAutoFocusRect.setVisibility(View.INVISIBLE);
                }
            }
        });

        m_seekBarZoom.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            private boolean startTracking = false;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (startTracking)
                    m_streamingContext.setZoom(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                startTracking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                startTracking = false;
            }
        });

        m_seekBarExporsure.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            private boolean startTracking = false;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (startTracking)
                    m_streamingContext.setExposureCompensation(progress + m_minExporsure);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                startTracking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                startTracking = false;
            }
        });

        m_liveWindow.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (m_switchAutoFocus.isChecked()) {
                    float rectHalfWidth = m_imageAutoFocusRect.getWidth() / 2;
                    if (event.getX() - rectHalfWidth >= 0 && event.getX() + rectHalfWidth <= m_liveWindow.getWidth()
                            && event.getY() - rectHalfWidth >= 0 && event.getY() + rectHalfWidth <= m_liveWindow.getHeight()) {
                        m_imageAutoFocusRect.setX(event.getX() - rectHalfWidth);
                        m_imageAutoFocusRect.setY(event.getY() - rectHalfWidth);
                        Rect rectFrame = new Rect();
                        m_imageAutoFocusRect.getWindowVisibleDisplayFrame(rectFrame);
                        m_streamingContext.startAutoFocus(new RectF(rectFrame));
                    }
                }
                return false;
            }
        });
    }

    private void setCaptureEnabled(boolean enabled) {
        m_listViewCaptureFx.setEnabled(enabled);
        m_buttonRecord.setEnabled(enabled);
        if (enabled) {
            if (m_streamingContext.getCaptureDeviceCount() > 1)
                m_switchBackFacing.setEnabled(true);

            if (m_streamingContext.isCaptureDeviceBackFacing(0))
                m_switchBackFacing.setChecked(false);
            else
                m_switchBackFacing.setChecked(true);
        } else
            m_switchBackFacing.setEnabled(false);
    }

    private void resetSettings() {
        m_buttonRecord.setEnabled(false);
        m_switchFlash.setEnabled(false);
        m_switchAutoFocus.setEnabled(false);
        m_seekBarZoom.setEnabled(false);
        m_seekBarExporsure.setEnabled(false);
        m_imageAutoFocusRect.setVisibility(View.INVISIBLE);
    }

    private void updateSettingsWithCapability(int deviceIndex) {
        NvsStreamingContext.CaptureDeviceCapability capability = m_streamingContext.getCaptureDeviceCapability(deviceIndex);
        if (null == capability)
            return;

        if (capability.supportFlash) {
            m_switchFlash.setChecked(m_streamingContext.isFlashOn());
            m_switchFlash.setEnabled(true);
        }

        if (capability.supportAutoFocus) {
            m_switchAutoFocus.setChecked(false);
            m_switchAutoFocus.setEnabled(true);
        }

        if (capability.supportZoom) {
            m_seekBarZoom.setMax(capability.maxZoom);
            m_seekBarZoom.setProgress(m_streamingContext.getZoom());
            m_seekBarZoom.setEnabled(true);
        }

        if (capability.supportExposureCompensation) {
            m_minExporsure = capability.minExposureCompensation;
            m_seekBarExporsure.setMax(capability.maxExposureCompensation - m_minExporsure);
            m_seekBarExporsure.setProgress(m_streamingContext.getExposureCompensation() - m_minExporsure);
            m_seekBarExporsure.setEnabled(true);
        }
    }

    private int getCurrentEngineState() {
        return m_streamingContext.getStreamingEngineState();
    }

    @Override
    protected void onDestroy() {
        m_streamingContext = null;
        NvsStreamingContext.close();

        super.onDestroy();
    }

    @Override
    protected void onPause() {
        m_buttonRecord.setText(R.string.record);
        m_streamingContext.stop();
        super.onPause();
    }

    @Override
    protected void onResume() {
        m_streamingContext.startCapturePreview(m_currentDeviceIndex, NvsStreamingContext.VIDEO_CAPTURE_RESOLUTION_GRADE_HIGH, 0, m_aspectRatio);
        super.onResume();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            return;

        if (grantResults.length <= 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED)
            return;

        switch (requestCode) {
            case REQUEST_CAMERA_PERMISSION_CODE:
                if (PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)) {
                    if (PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                        m_permissionGranted = true;
                        m_streamingContext.startCapturePreview(0, NvsStreamingContext.VIDEO_CAPTURE_RESOLUTION_GRADE_HIGH, 0, m_aspectRatio);
                    } else
                        requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_CODE);
                } else {
                    requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION_CODE);
                }
                break;
            case REQUEST_RECORD_AUDIO_PERMISSION_CODE:
                if (PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    m_permissionGranted = true;
                    m_streamingContext.startCapturePreview(0, NvsStreamingContext.VIDEO_CAPTURE_RESOLUTION_GRADE_HIGH, 0, m_aspectRatio);
                } else
                    requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_CODE);
                break;
            case REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_CODE:
                m_permissionGranted = true;
                m_streamingContext.startCapturePreview(0, NvsStreamingContext.VIDEO_CAPTURE_RESOLUTION_GRADE_HIGH, 0, m_aspectRatio);
                break;
        }
    }

    @Override
    public void onCaptureDeviceCapsReady(int captureDeviceIndex)
    {
        if (captureDeviceIndex != m_currentDeviceIndex)
            return;
        if (m_permissionGranted) {
            setCaptureEnabled(true);
            m_permissionGranted = false;
        }
        updateSettingsWithCapability(captureDeviceIndex);
    }

    @Override
    public void onCaptureDevicePreviewResolutionReady(int captureDeviceIndex)
    {

    }

    @Override
    public void onCaptureDevicePreviewStarted(int captureDeviceIndex)
    {
        Log.d(TAG, "onCaptureDevicePreviewStarted");
    }

    @Override
    public void onCaptureDeviceError(int captureDeviceIndex, int errorCode)
    {

    }

    @Override
    public void onCaptureDeviceStopped(int captureDeviceIndex)
    {

    }

    @Override
    public void onCaptureDeviceAutoFocusComplete(int captureDeviceIndex, boolean succeeded)
    {

    }

    @Override
    public void onCaptureRecordingFinished(int captureDeviceIndex)
    {

    }

    @Override
    public void onCaptureRecordingError(int captureDeviceIndex)
    {

    }
}

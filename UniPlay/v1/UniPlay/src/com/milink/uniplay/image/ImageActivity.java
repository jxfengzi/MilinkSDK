
package com.milink.uniplay.image;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.ImageView;
import android.widget.Toast;

import com.milink.api.v1.MilinkClientManager;
import com.milink.api.v1.type.DeviceType;
import com.milink.api.v1.type.ErrorCode;
import com.milink.api.v1.type.MediaType;
import com.milink.api.v1.type.ReturnCode;
import com.milink.uniplay.Device;
import com.milink.uniplay.MilinkClient;
import com.milink.uniplay.R;
import com.milink.uniplay.image.ImageTabContentFragment.Photos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ImageActivity extends Activity implements IImageCallback {
    private String TAG = ImageActivity.class.getSimpleName();

    private final int LEFT = 0;
    private final int RIGHT = 1;
    private final int TIMEOUT = 5000;

    private MilinkClientManager mMilinkClientManager = null;

//    private List<ImageInfo> mImageList = null;
    private List<String> imageTitleList = null;
    private List<String> imagePathList = null;
    private int mCurrentPosition = 0;
    private ImageView mImageView = null;

    private boolean connected = false;

    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            if (msg.what == LEFT) {
                Log.d(TAG, "left message.");
                mCurrentPosition += 1;
                mCurrentPosition %= imagePathList.size();
            }
            else if (msg.what == RIGHT) {
                Log.d(TAG, "right message.");
                mCurrentPosition = mCurrentPosition - 1 + imagePathList.size();
                mCurrentPosition %= imagePathList.size();
            }
            else {
                return;
            }
            setImageInfo();
            showPhoto();
        };
    };

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.image_details);

        MilinkClient.mMilinkClient.setCallback(this);
        mMilinkClientManager = MilinkClient.mMilinkClient.getManagerInstance();

        Bundle mBundle = getIntent().getExtras();
        imageTitleList = mBundle.getStringArrayList("imageTitleList");
        imagePathList = mBundle.getStringArrayList("imagePathList");
        // mImageList = (ArrayList<ImageInfo>) mBundle.get("imageInfoList");
        mCurrentPosition = (Integer) mBundle.get("position");
        mImageView = (ImageView) findViewById(R.id.img);
        mImageView.setOnTouchListener(new OnTouchListener() {
            float beginX = 0, endX = 0;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        beginX = event.getX();
                        break;
                    case MotionEvent.ACTION_UP:
                        endX = event.getX();
                        if (beginX > endX) {
                            mHandler.sendEmptyMessage(LEFT);
                        } else if (beginX < endX) {
                            mHandler.sendEmptyMessage(RIGHT);
                        }
                        break;
                    default:
                        break;
                }
                return true;
            }
        });

        setImageInfo();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem mMenuItem = menu.add("push");
        mMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        mMenuItem.setIcon(android.R.drawable.ic_menu_share);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getTitle().equals("push")) {
            Log.d(TAG, "push");

            final ArrayList<Device> finalDeviceList = new ArrayList<Device>();
            synchronized (MilinkClient.mDeviceList) {
                finalDeviceList.add(MilinkClient.mDeviceList.get(0));
                for (int i = 1; i < MilinkClient.mDeviceList.size(); ++i) {
                    if (MilinkClient.mDeviceList.get(i).type == DeviceType.TV) {
                        finalDeviceList.add(MilinkClient.mDeviceList.get(i));
                    }
                }
            }
            final ArrayList<String> names = new ArrayList<String>();
            for (Device device : finalDeviceList) {
                names.add(device.name);
            }
            String[] deviceNames = new String[names.size()];
            names.toArray(deviceNames);

            new AlertDialog.Builder(this).setTitle(R.string.deviceListName).setItems(
                    deviceNames,
                    new DialogInterface.OnClickListener() {

                        @Override
                        public void onClick(DialogInterface dialog, int pos) {
                            if (pos == 0) {
                                stopShow();
                                return;
                            }
                            String deviceId = finalDeviceList.get(pos).id;
                            MilinkClientManager mMilinkClientManager = MilinkClient.mMilinkClient
                                    .getManagerInstance();
                            ReturnCode retcode = mMilinkClientManager.connect(deviceId,
                                    Integer.valueOf(TIMEOUT));
                            Log.d(TAG, "connect ret code: " + retcode);
                        }

                    })
                    .create().show();

            return true;
        }
        return false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopShow();
    }

    private void setImageInfo() {
        String path = imagePathList.get(mCurrentPosition);
        String title = imageTitleList.get(mCurrentPosition);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int width = dm.widthPixels;
        int height = dm.heightPixels;
        
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);
        
        double scale = Math.max(options.outWidth / width, options.outHeight / height);
//        options.inSampleSize = scale > 1 ? (int) scale + 1 : 1;
        options.inSampleSize = 4;
        options.inJustDecodeBounds = false;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        options.inPurgeable = true;
        options.inInputShareable = true;
        Bitmap bm = BitmapFactory.decodeFile(path, options);
        mImageView.setImageBitmap(bm);
        
        getActionBar().setTitle(title);
    }

    public void initShowPhoto() {
        ReturnCode retcode = mMilinkClientManager.startShow();
        Log.d(TAG, "init show photo ret code: " + retcode);
    }

    public void showPhoto() {
        if (connected) {
            String path = imagePathList.get(mCurrentPosition);
            ReturnCode retcode = mMilinkClientManager.show(path);
            Log.d(TAG, "show photo ret code: " + retcode);
        }
    }

    public void stopShow() {
        if (connected) {
            ReturnCode retcode = mMilinkClientManager.stopShow();
            ReturnCode retcode2 = mMilinkClientManager.disconnect();
            connected = false;
            Log.d(TAG, "stop show ret code: " + retcode);
            Log.d(TAG, "disconnect ret code: " + retcode2);
        }
    }

    @Override
    public String getPrevPhoto(String uri, boolean isRecyle) {
        Log.d(TAG, "get prev photo uri: " + uri);
        int pos = imagePathList.indexOf(uri);
        if (pos > 0) {
            return imagePathList.get(pos - 1);
        } else if (pos == 0 && isRecyle) {
            return imagePathList.get(imagePathList.size() - 1);
        } else {
            return null;
        }
    }

    @Override
    public String getNextPhoto(String uri, boolean isRecyle) {
        Log.d(TAG, "get next photo uri: " + uri);
        int pos = imagePathList.indexOf(uri);
        if (pos == -1) {
            return null;
        } else if (pos >= 0 && pos < imagePathList.size() - 1) {
            return imagePathList.get(pos + 1);
        } else if (pos == imagePathList.size() - 1 && isRecyle) {
            return imagePathList.get(0);
        } else {
            return null;
        }
    }

    @Override
    public void onConnected() {
        connected = true;
        Toast.makeText(this, R.string.connected, Toast.LENGTH_SHORT).show();
        initShowPhoto();
        showPhoto();
    }

    @Override
    public void onConnectedFailed(ErrorCode errorCode) {
        connected = false;
        Toast.makeText(this, R.string.connectFailed, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDisconnected() {
        connected = false;
    }

    @Override
    public void onLoading() {
    }

    @Override
    public void onPlaying() {
    }

    @Override
    public void onStopped() {
    }

    @Override
    public void onPaused() {
    }

    @Override
    public void onVolume(int volume) {
    }

}

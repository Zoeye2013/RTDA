package fi.aalto.rtda;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Binder;
import android.os.IBinder;
import java.util.Timer;
import java.util.TimerTask;

public class BluetoothBackgroundService extends Service {

    private Context appContext;
    private int serviceAction;

    /** Bluetooth **/
    private BluetoothAdapter btAdapter;
    private BTBroadcastReceiver btReceiver;
    private String localBTAddress;

    /** Class Responsible for business logics **/
    private CalibrationManageClass calibrationManage;
    private MeasuringManageClass measureManage;
    private long timedifference;

    /** This is the object that receives interactions from clients **/
    private IBinder btServiceBinder;

    /** User selected measurement site **/
    private String siteName;

    /** Checking measurement **/
    private boolean isTrainingStopped;
    private boolean continueInquiry;
    public boolean isMeasurementStopped;

    /** For Signal vectors recording **/
    private Timer dataTransferTimer;

    @Override
    public void onCreate() {
        super.onCreate();
        btServiceBinder = new BTServiceLocalBinder();
        dataTransferTimer = new Timer();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        appContext = getApplicationContext();
        serviceAction = intent.getIntExtra(HomeActivity.KEY_SERVICE_ACTION,0);
        timedifference = intent.getLongExtra(HomeActivity.KEY_SERVER_TIME_DIFF,0);
        localBTAddress = BluetoothAdapter.getDefaultAdapter().getAddress();
        siteName = "";

        /** Calibration & Measurement Share same type of Background Service **/
        /** So use serviceAction to distinguish **/
        if(serviceAction > 0) {
            switch (serviceAction) {
                /** Perform Calibration works **/
                case HomeActivity.ACTION_CALIBRATION:
                    /** Java class that handle calibration related data **/
                    calibrationManage = new CalibrationManageClass(appContext);
                    /** Fetch full list of allowed devices from Server, preapare for calibration **/
                    calibrationManage.fetchAllowedList();

                    /** Start CalibrationActivity, present to user the Calibration process **/
                    Intent activtyIntent = new Intent(appContext,CalibrationActivity.class);
                    activtyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(activtyIntent);

                    break;
                /** Perform Measurement works **/
                case HomeActivity.ACTION_MEASUREMENT:
                    siteName = intent.getStringExtra(HomeActivity.KEY_SITE_NAME);
                    /** Java class that handle measurement related data **/
                    measureManage = new MeasuringManageClass(appContext);
                    /** Fetch devices list of the measurement site selected by user **/
                    measureManage.fetchSiteDevicesList(siteName);
                    break;
            }

            /** Prepare to start Bluetooth discovery **/
            btReceiver = new BTBroadcastReceiver();
            btAdapter = BluetoothAdapter.getDefaultAdapter();
            IntentFilter filter = new IntentFilter();
            filter.addAction(BluetoothDevice.ACTION_FOUND);
            filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
            filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
            registerReceiver(btReceiver, filter);

            continueInquiry = true;
            enableBTAdapter();

            /** Set timer to send data to server periodically **/
            dataTransferTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    synchToServer();
                }
            }, 0, 1*60*1000);//1 min for testing purpose
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        disableBTAdapter();
        super.onDestroy();
    }

    /** Return a binder for other Activity to interact with this Service when it's running in the background **/
    @Override
    public IBinder onBind(Intent intent) { return btServiceBinder;}

    /** Class for clients to access.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC. */
    public class BTServiceLocalBinder extends Binder {
        BluetoothBackgroundService getService() { return BluetoothBackgroundService.this; }
    }

    public CalibrationManageClass getCalibrationManage(){
        return calibrationManage;
    }
    public MeasuringManageClass getMeasureManage(){
        return measureManage;
    }

    /** Enable BT adapter **/
    public void enableBTAdapter(){
        if(btAdapter.isEnabled())
        {
            continueInquiry = true;
            btAdapter.startDiscovery();
        }else{
            btAdapter.enable();
        }
    }
    /** Disable BT adapter **/
    public void disableBTAdapter(){
        if(btAdapter.isEnabled())
            btAdapter.disable();
    }

    /** End Calibration **/
    public void endCalibrationOrMeasurement(){
        switch (serviceAction){
            case HomeActivity.ACTION_CALIBRATION:
                isTrainingStopped = true;
                break;
            case HomeActivity.ACTION_MEASUREMENT:
                isMeasurementStopped = true;
                break;
        }
        continueInquiry = false;
        synchToServer();
        unregisterReceiver(btReceiver);
        if(dataTransferTimer != null) {
            dataTransferTimer.cancel();
            dataTransferTimer = null;
        }
    }

    /** Synchronize Calibration/Measurement records to Server **/
    public void synchToServer(){
        ConnectivityManager connectManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo wifiInfo = connectManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if(wifiInfo.isConnected()) {
            switch (serviceAction){
                /** When Service is performing Calibration works **/
                case HomeActivity.ACTION_CALIBRATION:
                    /** Synchronize Calibration records **/
                    calibrationManage.synchCalibrationToServer(timedifference,localBTAddress);
                    break;
                /** When Service is performing Measurement works **/
                case HomeActivity.ACTION_MEASUREMENT:
                    /** Synchronize Measurement records **/
                    measureManage.synchMesurementToServer(timedifference,localBTAddress);
                    break;
            }
        }
    }

    /** Inner class Broadcast receiver listen to Bluetooth discovering results */
    public class BTBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            /** Catch remote Bluetooth device found action */
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {

                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                switch (serviceAction){
                    /** When Service is performing Calibration works **/
                    case HomeActivity.ACTION_CALIBRATION:
                        if(!isTrainingStopped){
                            if(!calibrationManage.isIrrelevant(device)) {
                                short btRSSI = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);
                                /** Add new found device to site devices list if it's not irrelevant and it isn't exist **/
                                calibrationManage.addSiteDevice(device, btRSSI);
                            }
                        }
                        break;
                    /** When Service is performing Measurement works **/
                    case HomeActivity.ACTION_MEASUREMENT:
                        if(!isMeasurementStopped){
                            short btRSSI = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);
                            /** If new found device isn't irrelevant, update the rssi for this device **/
                            measureManage.updateTempRSSI(device,btRSSI);
                        }
                        break;
                }
            }/** Catch Bluetooth discovery finished action */
            else if(BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)){
                /** Notify UI to update ListView when one Bluetooth discovery is finished **/
                switch (serviceAction) {
                    case HomeActivity.ACTION_CALIBRATION:
                        if(!isTrainingStopped) {
                            calibrationManage.getSiteDevicesAdapter().notifyDataSetChanged();
                            calibrationManage.recordData();
                        }
                        break;
                    case HomeActivity.ACTION_MEASUREMENT:
                        if(!isMeasurementStopped) {
                            measureManage.getSiteDevicesAdapter().notifyDataSetChanged();
                            measureManage.recordData();
                        }
                        break;
                }
                /** If continuously discovering is true then start next round of discovery */
                if(continueInquiry == true) {
                    btAdapter.startDiscovery();
                }
            } else if(BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)){
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
                switch (state){
                    /** In some case need to wait until BluetoothAdapter is on, then can start Bluetooth discovery **/
                    case BluetoothAdapter.STATE_ON:
                        btAdapter.startDiscovery();
                        break;
                }
            }
        }
    }
}

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
import android.util.Log;

public class MeasuringBackgroundService extends Service {
    /** Broadcast receiver listen to Bluetooth discovering results
     *  focus on getting Bluetooth RSSI values */
    private String currUser;
    private String siteName;
    private Context appContext;
    private BluetoothAdapter btAdapter;
    private MeasuringManageClass measuringManage;
    private BTBroadcastReceiver btReceiver;
    private boolean continueInquiry;

    /** This is the object that receives interactions from clients*/
    private IBinder signalVectorServiceBinder;

    //Temp SignalVectorRecord
    private MeasuringManageClass.SignalVectorRecordItem tempItem;

    public MeasuringBackgroundService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        signalVectorServiceBinder = new SignalVectorLocalBinder();

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        appContext = getApplicationContext();
        siteName = intent.getStringExtra(MeasuringActivity.KEY_SITENAME);
        currUser = intent.getStringExtra(LoginActivity.SHARED_KEY_LOGGED_USER);
        measuringManage = new MeasuringManageClass(appContext,currUser,siteName);
        measuringManage.loadAllowedDevices();
        //notify chart activity
        Intent notifyChartIntent = new Intent();
        notifyChartIntent.setAction(RealtimeChartActivity.INTENT_ACTION_DEVICE_NUMBER);
        notifyChartIntent.putExtra(RealtimeChartActivity.KEY_DEVICE_NUMBER,measuringManage.getAllowedDevicesNumber());
        appContext.sendBroadcast(notifyChartIntent);

        btReceiver = new BTBroadcastReceiver();
        btAdapter = BluetoothAdapter.getDefaultAdapter();

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(btReceiver, filter);
        continueInquiry = true;
        tempItem = measuringManage.newTempSignalVectorRecord();
        btAdapter.startDiscovery();
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return signalVectorServiceBinder;
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(btReceiver);
        super.onDestroy();
    }

    public MeasuringManageClass getMeasuringManager(){
        return measuringManage;
    }

    /** Inner class Broadcast receiver listen to Bluetooth discovering results */
    public class BTBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            /** Catch remote Bluetooth device found action */
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                /** Get BluetoothDevice object of the found remote BT device */
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                /** Get Bluetooth RSSI value of the found remote BT device */
                short btRSSI = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);
                int deviceIndex = measuringManage.getDeviceIndex(device);
                if(deviceIndex >= 0) {
                    tempItem.setSignalVector(deviceIndex, btRSSI);
                }
                //measuringManage.setBluetoothRSSI(btRSSI, device.getAddress());
            }
            /** Catch Bluetooth discovery finished action */
            else if(BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)){
                recordData();
                /** If continuously discovering is true then start next round of discovery */
                if(continueInquiry == true) {
                    tempItem = measuringManage.newTempSignalVectorRecord();
                    btAdapter.startDiscovery();
                }
            }
        }
    }

    public void recordData(){
        tempItem.getSystemCurrentTime();
        measuringManage.addSignalVectorRecord(tempItem);
    }

    public boolean checkWifi()
    {
        ConnectivityManager connec = (ConnectivityManager) getApplicationContext().getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo wifi = connec.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

        if (wifi.isConnected()) {
            return true;
        }
        return false;
    }
    public boolean checkMobileNetwork()
    {
        ConnectivityManager connec = (ConnectivityManager) getApplicationContext().getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo mobile = connec.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);

        if (mobile.isConnected()) {
            return true;
        }
        return false;
    }

    /** Class for clients to access.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC. */
    public class SignalVectorLocalBinder extends Binder {

        MeasuringBackgroundService getService() {
            return MeasuringBackgroundService.this;
        }
    }
}

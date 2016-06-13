package fi.aalto.rtda;

import android.app.Activity;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.util.ArrayList;

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

    //For real-time chart
    private ArrayList<ArrayList<Entry>> listOfEntryList; //list of several entry lists
    private ArrayList<String> xVals;
    private int allowDevicesNum;
    private ArrayList<ILineDataSet> dataSets;

    //Callback for notifying activity data changes
    Callbacks activityForCallback;

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

        //For Real-time Chart
        createEntryListsForAllowedDevices();
        //notify chart activity
        /*Intent notifyChartIntent = new Intent();
        notifyChartIntent.setAction(RealtimeChartActivity.INTENT_ACTION_DEVICE_NUMBER);
        notifyChartIntent.putExtra(RealtimeChartActivity.KEY_DEVICE_NUMBER,measuringManage.getAllowedDevicesNumber());
        appContext.sendBroadcast(notifyChartIntent);*/

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

    //Create Entry lists for each allowed device
    public void createEntryListsForAllowedDevices(){
        listOfEntryList = new ArrayList<ArrayList<Entry>>();
        xVals = new ArrayList<String>();
        dataSets = new ArrayList<ILineDataSet>();
        allowDevicesNum = measuringManage.getAllowedDevicesNumber();
        for(int i = 0; i < allowDevicesNum; i++){
            ArrayList<Entry> newEntryList = new ArrayList<Entry>();
            listOfEntryList.add(newEntryList);

            String lineName = "Device " + (i+1);
            LineDataSet dataSet = new LineDataSet(listOfEntryList.get(i),lineName);

            /* Give datasets different color */
            String colorName = "color"+(i+1);
            int color = getResources().obtainTypedArray(R.array.chartcolors).getColor(i,0);
            //int test = R.color.color1;
            dataSet.setColor(color);
            dataSet.setCircleColor(color);
            //dataSet.setAxisDependency(YAxis.AxisDependency.LEFT);
            dataSets.add(dataSet);
        }
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

        //Create Real-time Chart Entry
        xVals.add(tempItem.getTimeString());
        int xPosition = xVals.size()-1;
        ArrayList<Short> signalVectors = tempItem.getSignalVectors();
        for(int i = 0; i < signalVectors.size(); i++){
            Entry temp = new Entry(signalVectors.get(i), xPosition);
            ArrayList<Entry> entryList = listOfEntryList.get(i);
            entryList.add(temp);

            //Add data sets
            /*String lineName = "Device " + (i+1);
            LineDataSet dataSet = new LineDataSet(entryList,lineName);
            dataSet.setAxisDependency(YAxis.AxisDependency.LEFT);
            dataSets.add(dataSet);*/
        }
        activityForCallback.notifyDataChange();
    }

    //For Real time Chart activity to attrieve
    public ArrayList<String> getXValues(){
        return xVals;
    }
    public ArrayList<ILineDataSet> getDataSets(){
        return dataSets;
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

    //Here Activity register to the service as Callbacks client
    public void registerClient(Activity activity){
        activityForCallback = (Callbacks)activity;
    }

    //Callback for notifying activity data changes
    public interface Callbacks{
        public void notifyDataChange();
    }
}

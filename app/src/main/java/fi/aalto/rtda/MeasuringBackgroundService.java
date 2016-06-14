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
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkError;
import com.android.volley.NoConnectionError;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.ServerError;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

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
    private static final int maxEntriedNum = 30;

    //Callback for notifying activity data changes
    Callbacks activityForCallback;

    /** This is the object that receives interactions from clients*/
    private IBinder signalVectorServiceBinder;

    //Temp SignalVectorRecord
    private MeasuringManageClass.SignalVectorRecordItem tempItem;
    private Timer dataTransferTimer;
    public static final String SAVE_RECORDS_URL = "http://rtda.atwebpages.com/saverecords.php";
    private static final String RESPONSE_SAVE_SUCCESSFUL = "Tried save";
    //Keys for transfering data to server
    public static final String KEY_USERNAME = "username";
    public static final String KEY_SITENAME = "sitename";
    public static final String KEY_RECORDS = "records";

    public MeasuringBackgroundService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i("RTDA","Service created");
        signalVectorServiceBinder = new SignalVectorLocalBinder();
        dataTransferTimer = new Timer();

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("RTDA","Service strated");
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
        //Set timer to send data to server periodically
        dataTransferTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Log.i("RTDA Save", "run timer task");
                synchDataToServer();   //Your code here
            }
        }, 0, 1*60*1000);//5 Minutes
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return signalVectorServiceBinder;
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(btReceiver);
        if(dataTransferTimer != null) {
            dataTransferTimer.cancel();
            dataTransferTimer = null;
        }
        Log.i("RTDA","Service destroyed");
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
        Log.i("RTDA","Service found new record");
        tempItem.getSystemCurrentTime();
        measuringManage.addSignalVectorRecord(tempItem);

        //Create Real-time Chart Entry

        int xPosition = xVals.size();

        xVals.add(tempItem.getTimeString());
        ArrayList<Short> signalVectors = tempItem.getSignalVectors();
        for(int i = 0; i < signalVectors.size(); i++){
            Entry temp = new Entry(signalVectors.get(i), xPosition);
            ArrayList<Entry> entryList = listOfEntryList.get(i);
            entryList.add(temp);
        }

        //Remove oldest
        if(xPosition >= maxEntriedNum){
            xVals.remove(0);
            for(int i = 0; i < listOfEntryList.size(); i++){
                listOfEntryList.get(i).remove(0);
                for (Entry entry : listOfEntryList.get(i)) {
                    entry.setXIndex(entry.getXIndex() - 1);
                }
            }
        }

        activityForCallback.notifyDataChange();
    }

    public void synchDataToServer(){
        final ArrayList<MeasuringManageClass.SignalVectorRecordItem> tempRecords = measuringManage.getCopySignalVectorRecord();
        //Sending allowed devices list to Server
        ConnectivityManager connectManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo wifiInfo = connectManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if(wifiInfo.isConnected() && tempRecords.size() > 0){
            Log.i("RTDA Save", "Start transfer");
            String tempString = "";
            for (MeasuringManageClass.SignalVectorRecordItem s: tempRecords){
                tempString += s.getTimeMilli() + ";";
                for(Short v: s.getSignalVectors()){
                    tempString += v + ",";
                }
                tempString += "!";
            }
            final String recordsString = tempString;
            Log.i("RTDA Save", tempString);
            StringRequest stringRequest = new StringRequest(Request.Method.POST,SAVE_RECORDS_URL,
                    new Response.Listener<String>() {
                        @Override
                        public void onResponse(String response) {
                            //showProgress(false);
                            Toast.makeText(appContext, response, Toast.LENGTH_LONG).show();
                            Log.i("RTDA save",response);
                            if(response.trim().contains(RESPONSE_SAVE_SUCCESSFUL)){
                                int recordsNum = tempRecords.size();
                                measuringManage.deleteSavedRecords(recordsNum);
                                Log.i("RTDA Save", "transfer finished");
                                Log.i("RTDA SAVE","Send " + recordsNum + "," + response);
                            }
                        }
                    }, new Response.ErrorListener(){
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.i("RTDA Save", "transfer finished, but error");
                    //showProgress(false);
                    if (error != null) {
                        Log.e("Volley", "Error. HTTP Status Code:"+error.toString());
                    }

                    if (error instanceof TimeoutError) {
                        Toast.makeText(appContext, "Timeout, please retry.", Toast.LENGTH_LONG).show();
                    }else if(error instanceof NoConnectionError){
                        Log.e("Volley", "NoConnectionError");
                    } else if (error instanceof AuthFailureError) {
                        Log.e("Volley", "AuthFailureError");
                    } else if (error instanceof ServerError) {
                        Log.e("Volley", "ServerError");
                    } else if (error instanceof NetworkError) {
                        Log.e("Volley", "NetworkError");
                    } else if (error instanceof ParseError) {
                        Log.e("Volley", "ParseError");
                    }
                    //Toast.makeText(appContext,error.toString(),Toast.LENGTH_LONG).show();
                    //showProgress(false);
                }
            }){
                @Override
                protected Map<String, String> getParams() throws AuthFailureError {
                    Map<String,String> params = new HashMap<String, String>();
                    params.put(KEY_USERNAME,currUser);
                    params.put(KEY_SITENAME,siteName);
                    params.put(KEY_RECORDS,recordsString);
                    return params;
                }
            };

            RequestQueue requestQueue = Volley.newRequestQueue(appContext);
            requestQueue.add(stringRequest);
        }
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

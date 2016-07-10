package fi.aalto.rtda;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Jave Class for keeping data before persistent data to Server's database **/
public class MeasuringManageClass {
    private Context appContext;

    /** Lists **/
    private ArrayList<BluetoothDevice> siteDevicesList; //Devices list of user selected measurement site
    private ArrayList<Short> rssiList; //For present Listview rssi field

    private MeasurementDevicesAdapter siteDevicesAdapter;

    /** For recording measurement record **/
    private RecordItemClass tempRecordItem;
    private ArrayList<Short> signalsList;    //Temporarily record one calibration record's RSSIs of all allowed devices.
    private ArrayList<RecordItemClass> recordList;   //List of calibration records

    /** Class Constructor, initiation works  **/
    public MeasuringManageClass(Context context){
        appContext = context;

        siteDevicesList = new ArrayList<BluetoothDevice>();
        rssiList = new ArrayList<Short>();
        signalsList = new ArrayList<Short>();

        recordList = new ArrayList<RecordItemClass>();
        siteDevicesAdapter = new MeasurementDevicesAdapter(appContext,siteDevicesList);
    }

    /** Fetch devices list of user selected measurement site from Server **/
    public void fetchSiteDevicesList(final String siteName){
        StringRequest stringRequest = new StringRequest(Request.Method.POST, HomeActivity.URL_FETCH_SITE_DEVICES_LIST, new Response.Listener<String>(){
            @Override
            public void onResponse(String response) {
                ArrayList<String>tempList = new ArrayList<String>(Arrays.asList(response.split(",")));
                for(int i = 0; i < tempList.size(); i++){
                    BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(tempList.get(i));
                    siteDevicesList.add(device);
                    rssiList.add((short)0);
                    signalsList.add((short)0);
                }
                siteDevicesAdapter.notifyDataSetChanged();
            }
        }, new Response.ErrorListener(){
            @Override
            public void onErrorResponse(VolleyError error) {
                if (error != null) {
                    Log.e("Volley", "Error. HTTP Status Code:"+error.toString());
                }

                if (error instanceof TimeoutError) {
                    Log.e("Volley", "TimeoutError");
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
                Toast.makeText(appContext,error.toString(),Toast.LENGTH_LONG).show();
            }
        }){
            @Override
            protected Map<String, String> getParams() throws AuthFailureError {
                Map<String,String> params = new HashMap<String, String>();
                params.put(HomeActivity.KEY_SITE_NAME,siteName);
                return params;
            }
        };
        RequestQueue requestQueue = Volley.newRequestQueue(appContext);
        requestQueue.add(stringRequest);
    }

    /** Update RSSIs for Temporary Record **/
    public void updateTempRSSI(BluetoothDevice device, short rssi){
        int index = siteDevicesList.indexOf(device);
        if(index >= 0){//Is one of site devices
            rssiList.set(index,rssi);
            signalsList.set(index,rssi);
        }
    }

    /** Get List Adapter for ListView to present data **/
    public  MeasurementDevicesAdapter getMeasurementDevicesAdapter(){
        return siteDevicesAdapter;
    }

    /** Record one Measurement record, and add it to measurement records list **/
    public void recordData(){
        tempRecordItem = new RecordItemClass();
        tempRecordItem.setSignalVector(signalsList);
        recordList.add(tempRecordItem);
        Collections.fill(signalsList,(short)0);
    }

    /** Delete measurement records that are successfully synchronized to Server **/
    public void deleteSavedRecords(int saveRecordsNum){
        for(int i = 0; i < saveRecordsNum; i++){
            recordList.remove(0);
        }
    }
    /** Synchronize Measurement Data to Server **/
    public void synchMesurementToServer(Long timedifference,String localBTAddress){
        final int recordsNum = recordList.size();
        final String localBT = localBTAddress;
        if(recordsNum > 0){
            String date = "";
            String tempString = "";
            for (int i= 0; i<recordsNum; i++){
                RecordItemClass s = recordList.get(i);
                tempString += (s.getTimeMilli()+timedifference) + ";";
                for(Short v: s.getSignalVectors()){
                    tempString += v + ",";
                }
                tempString += "!";

                /**  Get date from first record **/
                if(i == 0)
                    date = s.getDateString();
            }
            final String dateString = date;
            final String recordsString = tempString;

            StringRequest stringRequest = new StringRequest(Request.Method.POST,HomeActivity.URL_SAVE_MEASUREMENT,
                    new Response.Listener<String>() {
                        @Override
                        public void onResponse(String response) {
                            Toast.makeText(appContext, response, Toast.LENGTH_LONG).show();
                            if(response.trim().contains(HomeActivity.RESPONSE_SAVE_SUCCESSFUL)){
                                deleteSavedRecords(recordsNum);
                            }
                        }
                    }, new Response.ErrorListener(){
                @Override
                public void onErrorResponse(VolleyError error) {
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
                    params.put(HomeActivity.KEY_RECORDS,recordsString);
                    params.put(HomeActivity.KEY_LOCAL_BT_ADDRESS,localBT);
                    params.put(HomeActivity.KEY_DATE,dateString);
                    return params;
                }
            };
            RequestQueue requestQueue = Volley.newRequestQueue(appContext);
            requestQueue.add(stringRequest);
        }
    }

    /** ArrayAdpater for render Bluetooth devices list **/
    public class MeasurementDevicesAdapter extends ArrayAdapter<BluetoothDevice> {
        public MeasurementDevicesAdapter(Context context, ArrayList<BluetoothDevice> allowedDevices){
            super(context,0,allowedDevices);
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            BluetoothDevice allowedDevice = getItem(position);
            if(convertView == null){
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_device,parent,false);
            }
            ImageView deviceIcon = (ImageView) convertView.findViewById(R.id.image_device);
            TextView deviceNameView = (TextView) convertView.findViewById(R.id.text_device_name);
            TextView deviceAddressView = (TextView) convertView.findViewById(R.id.text_device_address);
            TextView rssiView = (TextView) convertView.findViewById(R.id.text_rssi);

            /** Set device icon according to device type **/
            int deviceClass = allowedDevice.getBluetoothClass().getMajorDeviceClass();
            switch(deviceClass) {
                case BluetoothClass.Device.Major.COMPUTER:
                    deviceIcon.setImageResource(R.drawable.ic_computer);
                    break;
                case BluetoothClass.Device.Major.PHONE:
                    deviceIcon.setImageResource(R.drawable.ic_phone);
                    break;
                case BluetoothClass.Device.Major.PERIPHERAL:
                    deviceIcon.setImageResource(R.drawable.ic_peripheral);
                    break;
                default:
                    deviceIcon.setImageResource(R.drawable.ic_bluetooth_others);
                    break;
            }
            String name = allowedDevice.getName();
            deviceNameView.setText(name);
            String address = allowedDevice.getAddress();
            deviceAddressView.setText(address);

            rssiView.setText(rssiList.get(position).toString());
            rssiList.set(position,Short.valueOf("0"));
            return convertView;
        }
    }
}

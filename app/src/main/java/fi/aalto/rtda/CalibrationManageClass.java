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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Jave Class for keeping data before persistent data to Server's database**/
public class CalibrationManageClass {

    private Context appContext;

    /** Lists **/
    private ArrayList<BluetoothDevice> allowedDevicesList; //Full list of all allowed devices
    private ArrayList<BluetoothDevice> siteDevicesList;    //Subset of allowed devices, for specific measurement site
    private ArrayList<BluetoothDevice> irrelevantList;     //In range irrelevant devices that are not allowed devices
    private ArrayList<Short> rssiList; //For present Listview rssi field
    private SiteDevicesAdapter siteDevicesAdapter;

    /** For recording calibration record **/
    private RecordItemClass tempRecordItem;
    private ArrayList<Short> signalsList;       //Temporarily record one calibration record's RSSIs of all allowed devices.
    private ArrayList<RecordItemClass> recordList;  //List of calibration records

    /** Class Constructor, initiation works  **/
    public CalibrationManageClass(Context context){
        appContext = context;
        siteDevicesList = new ArrayList<BluetoothDevice>();
        rssiList = new ArrayList<Short>();
        irrelevantList = new ArrayList<BluetoothDevice>();
        allowedDevicesList = new ArrayList<BluetoothDevice>();
        recordList = new ArrayList<RecordItemClass>();
        signalsList = new ArrayList<Short>();
        siteDevicesAdapter = new SiteDevicesAdapter(appContext,siteDevicesList);
    }

    /** Fetch full list of allowed devices from Server **/
    public void fetchAllowedList(){
        StringRequest stringRequest = new StringRequest(Request.Method.GET, HomeActivity.URL_FETCH_ALLOWED_LIST, new Response.Listener<String>(){
            @Override
            public void onResponse(String response) {
                try {
                    JSONObject jsonObject = new JSONObject(response);
                    JSONArray jsonArray = jsonObject.getJSONArray(HomeActivity.KEY_JSONARRAY_LIST);
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject row = jsonArray.getJSONObject(i);
                        String address = row.getString(HomeActivity.KEY_JSONARRAY_ADDRESS).toUpperCase().replaceAll("(.{2})", "$1"+":").substring(0,17);
                        BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address);
                        allowedDevicesList.add(device);
                        signalsList.add((short)0);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
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
        });

        RequestQueue requestQueue = Volley.newRequestQueue(appContext);
        requestQueue.add(stringRequest);
    }

    /** Add a found device that is included in allowed devices list,
     * to form new devices list for a specific measurment site**/
    public void addSiteDevice(BluetoothDevice device, short rssi){
        int index = allowedDevicesList.indexOf(device);
        if(index >= 0){//Is one of allowed devices
            int i = siteDevicesList.indexOf(device);
            /** If not exist in site devices list, add it, otherwise update the RSSI **/
            if(i<0){
                siteDevicesList.add(0,device);
                rssiList.add(0,rssi);
            }else{
                rssiList.set(i,rssi);
            }
            /** Update the RSSI for recording calibration **/
            signalsList.set(index,rssi);

        }else if(!irrelevantList.contains(device)){
            irrelevantList.add(device);
        }
    }

    /** Irrelevant devices will be put into a list **/
    public boolean isIrrelevant(BluetoothDevice device){
        return irrelevantList.contains(device)? true:false;
    }

    /** Get List Adapter for ListView to present data **/
    public  SiteDevicesAdapter getSiteDevicesAdapter(){
        return siteDevicesAdapter;
    }

    public int getSiteDevicesNumber(){
        return siteDevicesList.size();
    }

    /** Delete calibrations records that are successfully synchronized to Server **/
    public void deleteSavedRecords(int saveRecordsNum){
        for(int i = 0; i < saveRecordsNum; i++){
            recordList.remove(0);
        }
    }

    /** Get list of devices for a spacific measurement site **/
    public  ArrayList<BluetoothDevice> getSiteDevicesList(){
        return  siteDevicesList;
    }

    /** Record one Calibration record, and add it to calibration records list **/
    public void recordData(){
        tempRecordItem = new RecordItemClass();
        tempRecordItem.setSignalVector(signalsList);
        recordList.add(tempRecordItem);
        Collections.fill(signalsList,(short)0);
    }

    /** Synchronize Calibration Records to Server **/
    public void synchCalibrationToServer(Long timedifference,String localBTAddress){
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


            StringRequest stringRequest = new StringRequest(Request.Method.POST,HomeActivity.URL_SAVE_CALIBRATION,
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
    public class SiteDevicesAdapter extends ArrayAdapter<BluetoothDevice> {
        public SiteDevicesAdapter(Context context, ArrayList<BluetoothDevice> allowedDevices){
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

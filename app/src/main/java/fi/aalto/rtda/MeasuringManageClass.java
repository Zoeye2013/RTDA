package fi.aalto.rtda;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.logging.SimpleFormatter;

/**
 * Created by zhangy17 on 02/06/16.
 */
public class MeasuringManageClass {
    private Context appContext;
    private String currUser;
    private String siteName;
    private ArrayList<BluetoothDevice> allowedDevices;

    private ArrayList<SignalVectorRecordItem> signalVectorRecords;

    public MeasuringManageClass(Context context,String user,String site){
        appContext = context;
        currUser = user;
        siteName = site;
        allowedDevices = new ArrayList<BluetoothDevice>();
        signalVectorRecords = new ArrayList<SignalVectorRecordItem>();
    }

    //add a new signal vectors record
    public SignalVectorRecordItem newTempSignalVectorRecord(){
        SignalVectorRecordItem item = new SignalVectorRecordItem();
        return item;
    }

    public void addSignalVectorRecord(SignalVectorRecordItem record){
        signalVectorRecords.add(record);
    }

    public ArrayList<SignalVectorRecordItem> getCopySignalVectorRecord(){
        return (ArrayList<SignalVectorRecordItem>) signalVectorRecords.clone();
    }

    public void deleteSavedRecords(int saveRecordsNum){
        for(int i = 0; i < saveRecordsNum; i++){
            signalVectorRecords.remove(0);
        }
    }

    public int getRecordsNum(){
        return signalVectorRecords.size();
    }



    public void stopTraining(){
        /*cancelDiscovery();
        isTrainingStopped = true;
        allowedDevicesAdapter.notifyDataSetChanged();*/
    }

    public void clearAlllowedDevices(){
        Log.i("New Device","Clear list");
        allowedDevices.clear();
    }

    public int getAllowedDevicesNumber(){
        return allowedDevices.size();
    }

    public ArrayList<BluetoothDevice> getAllowedDevices(){
        return allowedDevices;
    }

    public int getDeviceIndex(BluetoothDevice device){
        return allowedDevices.indexOf(device);
    }

    public void loadAllowedDevices(){
        try {
            allowedDevices.clear();
            File userDir = appContext.getDir(currUser, Context.MODE_PRIVATE);
            File fileName = new File(userDir, siteName);
            FileInputStream fileInPut = new FileInputStream(fileName);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fileInPut));
            StringBuilder sBuilder = new StringBuilder();
            String line = null;
            while((line = reader.readLine())!= null){
                sBuilder.append(line).append("\n");
            }
            reader.close();

            String  allowedDevicesInfo = sBuilder.toString();
            fileInPut.close();

            //Convert to Json array
            JSONArray allowedDevicesJSON = new JSONArray(allowedDevicesInfo);

            //Convert Json array to Arraylist
            if (allowedDevicesJSON != null) {
                for (int i=0;i<allowedDevicesJSON.length();i++){
                    String address = allowedDevicesJSON.get(i).toString();
                    BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address);
                    allowedDevices.add(device);
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e){
            e.printStackTrace();
        } catch (JSONException e){
            e.printStackTrace();
        };
    }


    public class SignalVectorRecordItem {
        private Calendar calendar;
        private SimpleDateFormat sdf;
        private String timeFullString;
        private long timeMilli;
        private String timeString;

        private ArrayList<Short> signalVectors;
        private int vectorSize;

        public SignalVectorRecordItem(){
            signalVectors = new ArrayList<Short>();
            vectorSize = allowedDevices.size();
            for(int i = 0; i < vectorSize; i++){
                signalVectors.add((short)0);
            }
        }

        public void getSystemCurrentTime(){
            //Get current time
            calendar = Calendar.getInstance();
            sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
            timeFullString = sdf.format(calendar.getTime());
            sdf = new SimpleDateFormat("hh:mm:ss");
            timeString = sdf.format(calendar.getTime());
            timeMilli = calendar.getTimeInMillis();
        }

        public void setSignalVector(int index, short rssi){
            signalVectors.set(index,rssi);
        }

        public String getTimeFullString(){
            return timeFullString;
        }

        public String getTimeString() {return  timeString;}

        public long getTimeMilli(){
            return timeMilli;
        }

        public ArrayList<Short> getSignalVectors(){
            return signalVectors;
        }
    }
}

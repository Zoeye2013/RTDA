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

import java.util.ArrayList;

/**
 * Created by zhangy17 on 02/06/16.
 */
public class BluetoothHandleClass {
    private Context appContext;
    private static BluetoothAdapter bluetoothAdapter;

    public static final int BLUETOOTH_STATE_IDLE = 0;
    public static final int BLUETOOTH_STATE_TRAINING = 1;
    public static final int BLUETOOTH_STATE_MEASURING = 2;
    private int bluetoothDetectState;
    private boolean continueInquiry;

    private ArrayList<BluetoothDevice> allowedDevices;
    private AllowedDevicesAdapter allowedDevicesAdapter;
    private BTBroadcastReceiver btReceiver;


    public BluetoothHandleClass(Context context){
        appContext = context;
        bluetoothDetectState = BLUETOOTH_STATE_IDLE;
        continueInquiry = true;
        allowedDevices = new ArrayList<BluetoothDevice>();
        allowedDevicesAdapter = new AllowedDevicesAdapter(appContext,allowedDevices);
        btReceiver = new BTBroadcastReceiver();

    }

    public  AllowedDevicesAdapter getAllowedDevicesAdapter(){
        return allowedDevicesAdapter;
    }
    public BTBroadcastReceiver getBtReceiver(){
        return btReceiver;
    }

    /**Get local BT adapter and enable Bluetooth*/
    public static boolean openBTAdapter(Context appContext)
    {
        boolean btSuccessful = true;
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null)
        {
            /** If the device doesn't support Bluetooth, the end this module */
            Toast toast = Toast.makeText(appContext, R.string.error_no_bluetooth, Toast.LENGTH_LONG);
            btSuccessful = false;
        }
        if(!bluetoothAdapter.isEnabled())
        {
            /** Call enable() to enable Bluetooth without request for user permission
             *  This requires android.permission.BLUETOOTH_ADMIN Permission */
            bluetoothAdapter.enable();
        }
        return btSuccessful;
    }

    public void startDiscovery(int state){
        continueInquiry = true;
        bluetoothAdapter.startDiscovery();
        bluetoothDetectState = state;
        allowedDevices.clear();
    }

    public void addAllowedDevice(BluetoothDevice device){
        if(!allowedDevices.contains(device)){
            allowedDevices.add(device);
            allowedDevicesAdapter.notifyDataSetChanged();
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

                /** Depends on the state of the application will do different processes */
                switch(bluetoothDetectState){
                    case BLUETOOTH_STATE_IDLE:
                        break;
                    /** When use starts training allowed devices sets,
                        newly discovered devices will be recorded as allowed devices */
                    case BLUETOOTH_STATE_TRAINING:
                        addAllowedDevice(device);
                        break;
                    /** When user start measuring, the application starts recording activity records */
                    case BLUETOOTH_STATE_MEASURING:
                        //deviceMag.recordEnterTime(device);
                        break;
                    }

                    //updateCanvas();
                }
            /** Catch Bluetooth discovery finished action */
            else if(BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)){
                /** Check is previous discovered BT devices still in scope */
                //deviceMag.checkIsInScope();
                //updateCanvas();
                //deviceMag.setIsCurrentFalse();

                /** If continuously discovering is true then start next round of discovery */
                if(continueInquiry == true) {
                    bluetoothAdapter.startDiscovery();
                }
            }
        }
    }

    //ArrayAdpater for render Bluetooth devices list
    public class AllowedDevicesAdapter extends ArrayAdapter<BluetoothDevice> {
        public AllowedDevicesAdapter(Context context, ArrayList<BluetoothDevice> allowedDevices){
            super(context,0,allowedDevices);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            BluetoothDevice allowedDevice = getItem(position);
            if(convertView == null){
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.allowed_device_item,parent,false);
            }
            ImageView deviceIcon = (ImageView) convertView.findViewById(R.id.device_icon);
            TextView deviceName = (TextView) convertView.findViewById(R.id.device_name);
            TextView deviceAddress = (TextView) convertView.findViewById(R.id.device_address);
            Button removeButton = (Button) convertView.findViewById(R.id.remove_button);
            removeButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                }
            });

            //Set device icon according to device type
            int deviceClass = allowedDevice.getBluetoothClass().getMajorDeviceClass();
            switch(deviceClass)
            {
                case BluetoothClass.Device.Major.COMPUTER:
                    deviceIcon.setImageResource(R.drawable.ic_menu_share);
                    break;
                case BluetoothClass.Device.Major.PHONE:
                    deviceIcon.setImageResource(R.drawable.ic_menu_share);
                    break;
                case BluetoothClass.Device.Major.PERIPHERAL:
                    deviceIcon.setImageResource(R.drawable.ic_menu_share);
                    break;
                default:
                    deviceIcon.setImageResource(R.drawable.ic_menu_share);
                    break;
            }
            String name = allowedDevice.getName();
            deviceName.setText(name);
            String address = allowedDevice.getAddress();
            deviceAddress.setText(address);
            return convertView;
        }
    }
}

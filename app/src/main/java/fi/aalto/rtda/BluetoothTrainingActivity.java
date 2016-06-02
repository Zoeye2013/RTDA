package fi.aalto.rtda;

import android.app.Activity;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;

public class BluetoothTrainingActivity extends Activity {


    private ListView listView;
    private BluetoothHandleClass bluetoothManage;
    private Context appContext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth_training);
        appContext = this;
        bluetoothManage = new BluetoothHandleClass(appContext);
        listView = (ListView) findViewById(R.id.list_bluetooth_device);
        listView.setAdapter(bluetoothManage.getAllowedDevicesAdapter());

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(bluetoothManage.getBtReceiver(), filter);
        bluetoothManage.startDiscovery(BluetoothHandleClass.BLUETOOTH_STATE_TRAINING);
    }
}

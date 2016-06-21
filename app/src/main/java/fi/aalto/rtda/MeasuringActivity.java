package fi.aalto.rtda;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.RadioGroup.LayoutParams;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

public class MeasuringActivity extends AppCompatActivity implements AdapterView.OnItemClickListener, View.OnClickListener {

    private Context appContext;
    private String currUser;
    private File userDir;

    //private ViewGroup sitesGroupView;
    private ArrayList<String> siteList;
    private ListView sitesListView;
    private SiteArrayAdapter sitesArrayAdapter;
    private int selectIndex = -1;
    private String siteName;
    public static final String KEY_SITENAME = "sitename";

    private Button startButton;
    private Button checkMasurementButton;
    private Button stopMeasurementButton;
    private TextView selectSiteHintView;
    private TextView measuringHintView;

    /* SharedPreferences to save user info and server time */
    private SharedPreferences sharedPref;
    private SharedPreferences.Editor editor;
    public static final String SHARED_KEY_MEASUREMENT_STATUS = "status";
    public static final int SHARED_MEASUREMENT_STATUS_ONGOING = 2;

    //Enable bluetooth adapater
    private static BluetoothAdapter bluetoothAdapter;
    private BroadcastReceiver btReceiver;

    //Stop measurement
    public static boolean isMeasurementStopped;
    //Interaction with background service
    private MeasuringBackgroundService signalVectorService;
    private ServiceConnection serviceConnection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_measure_site_select);

        appContext = this;
        currUser = getIntent().getStringExtra(LoginActivity.SHARED_KEY_LOGGED_USER);
        sharedPref = appContext.getSharedPreferences(LoginActivity.SHAREDPREFERENCES,Context.MODE_PRIVATE);
        editor = sharedPref.edit();
        //editor.remove(SHARED_KEY_MEASUREMENT_STATUS); //test
        //editor.commit(); //test

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
        isMeasurementStopped = false;

        //sitesGroupView = (ViewGroup) findViewById(R.id.radio_group_sites);
        sitesListView = (ListView) findViewById(R.id.list_sites); //Testing
        userDir  = appContext.getDir(currUser, Context.MODE_PRIVATE);

        startButton = (Button) findViewById(R.id.start_measurement_button);
        checkMasurementButton = (Button) findViewById(R.id.check_measurement_button);
        stopMeasurementButton = (Button) findViewById(R.id.stop_measurement_button);

        selectSiteHintView = (TextView) findViewById(R.id.hint_choose_site);
        measuringHintView = (TextView) findViewById(R.id.hint_measurement_ongoing);
        startButton.setOnClickListener(this);
        checkMasurementButton.setOnClickListener(this);
        stopMeasurementButton.setOnClickListener(this);

        //Enable bluetooth adapter
        btReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                    int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
                    switch (state) {
                        case BluetoothAdapter.STATE_ON: //Start measuring chart and service
                            unregisterReceiver(btReceiver);
                            Intent measuringServiceIntent = new Intent(appContext,MeasuringBackgroundService.class);
                            measuringServiceIntent.putExtra(KEY_SITENAME,siteName);
                            measuringServiceIntent.putExtra(LoginActivity.SHARED_KEY_LOGGED_USER,currUser);
                            startService(measuringServiceIntent);
                            Intent realTimeChartIntent = new Intent(appContext,RealtimeChartActivity.class);
                            startActivity(realTimeChartIntent);
                            break;
                    }
                }
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(!isMeasurementStopped)
            checkElements();
        else
            finish();
    }

    @Override
    protected void onDestroy() {
        try{
            unregisterReceiver(btReceiver);
        }catch (IllegalArgumentException e){

        }
        super.onDestroy();
    }

    //Update elements according to measurement status
    public void checkElements(){
        //Get measurement status
        int measurementStatus = sharedPref.getInt(SHARED_KEY_MEASUREMENT_STATUS,0);
        if(measurementStatus == SHARED_MEASUREMENT_STATUS_ONGOING) {
            //Hide elements when measurement on going
            startButton.setVisibility(View.GONE);
            selectSiteHintView.setVisibility(View.GONE);
            sitesListView.setVisibility(View.GONE);

            //Show elements when measurement on going
            checkMasurementButton.setVisibility(View.VISIBLE);
            stopMeasurementButton.setVisibility(View.VISIBLE);
            measuringHintView.setVisibility(View.VISIBLE);
        }else {
            //If measurement isn't ongoing, register broadcast to receive Bluetooth adapter enabled actions
            IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
            registerReceiver(btReceiver, filter);

            //Hide elements when measurement on going
            startButton.setVisibility(View.VISIBLE);
            selectSiteHintView.setVisibility(View.VISIBLE);
            sitesListView.setVisibility(View.VISIBLE);
            enableStartButton();

            //Show elements when measurement on going
            checkMasurementButton.setVisibility(View.GONE);
            stopMeasurementButton.setVisibility(View.GONE);
            measuringHintView.setVisibility(View.GONE);

            loadSitesList();
        }
    }

    public void loadSitesList() {
        File lister = userDir.getAbsoluteFile();
        String[] list = lister.list();
        siteList = new ArrayList<String>(Arrays.asList(list));
        sitesArrayAdapter = new SiteArrayAdapter(appContext,siteList);
        sitesListView.setAdapter(sitesArrayAdapter);
        sitesListView.setOnItemClickListener(this);
    }

    public void setSelectIndex(int index){
        selectIndex = index;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        setSelectIndex(position);
        siteName = siteList.get(position);
        enableStartButton();
        sitesArrayAdapter.notifyDataSetChanged();
    }

    public void enableStartButton(){
        //Start button is disabled
        if(selectIndex >= 0){
            startButton.setEnabled(true);
            startButton.setBackgroundColor(getResources().getColor(R.color.colorEnabled));
        }
        else{
            startButton.setEnabled(false);
            startButton.setBackgroundColor(Color.GRAY);
        }
    }

    /**Get local BT adapter and enable Bluetooth*/
    public boolean openBTAdapter(){
        boolean btSuccessful = true;
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null){
            /** If the device doesn't support Bluetooth, the end this module */
            Toast toast = Toast.makeText(appContext, R.string.error_no_bluetooth, Toast.LENGTH_LONG);
            btSuccessful = false;
        }
        return btSuccessful;
    }

    public void disableBTAdapter(){
        if(bluetoothAdapter.isEnabled())
            bluetoothAdapter.disable();
    }

    public void loadAllowedDevices(){

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.start_measurement_button:
                editor.putInt(SHARED_KEY_MEASUREMENT_STATUS,SHARED_MEASUREMENT_STATUS_ONGOING);
                editor.commit();
                if(openBTAdapter()){
                    if(bluetoothAdapter.isEnabled()){
                        unregisterReceiver(btReceiver);
                        Intent measuringServiceIntent = new Intent(appContext,MeasuringBackgroundService.class);
                        measuringServiceIntent.putExtra(KEY_SITENAME,siteName);
                        measuringServiceIntent.putExtra(LoginActivity.SHARED_KEY_LOGGED_USER,currUser);
                        startService(measuringServiceIntent);
                        Intent realTimeChartIntent = new Intent(appContext,RealtimeChartActivity.class);
                        startActivity(realTimeChartIntent);
                        // Bind background service
                        //bindBackgroundService();
                    }else{
                        bluetoothAdapter.enable();
                    }
                }
                isMeasurementStopped = false; //Measurement isn't stopped
                break;
            case R.id.stop_measurement_button:
                editor.remove(SHARED_KEY_MEASUREMENT_STATUS);
                editor.commit();
                isMeasurementStopped = true;
                /*if(signalVectorService.getMeasuringManager().getRecordsNum() >0){
                    signalVectorService.synchDataToServer();
                }*/
                stopService(new Intent(appContext, MeasuringBackgroundService.class));
                finish();
                break;
            case R.id.check_measurement_button:
                Intent realTimeChartIntent = new Intent(appContext,RealtimeChartActivity.class);
                startActivity(realTimeChartIntent);
                break;
        }
    }

    public void bindBackgroundService(){
        serviceConnection = new ServiceConnection() {

            /** When the connection with the service has been established */
            public void onServiceConnected(ComponentName className, IBinder service) {

                /** Get the service object we can use to interact with the service */
                signalVectorService = ((MeasuringBackgroundService.SignalVectorLocalBinder) service).getService();
                signalVectorService.registerClient(MeasuringActivity.this);
            }

            public void onServiceDisconnected(ComponentName arg0) { }
        };

        /** Bind service that responsible for recording Bluetooth RSSI value on RUN_STATE */
        Intent signalVectorServiceIntent = new Intent(this, MeasuringBackgroundService.class);
        bindService(signalVectorServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    //ArrayAdpater for render Bluetooth devices list
    public class SiteArrayAdapter extends ArrayAdapter<String> {

        public SiteArrayAdapter(Context context,ArrayList<String> siteNames){
            super(context,0,siteNames);
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            String name = getItem(position);
            if(convertView == null){
                convertView = ((Activity)appContext).getLayoutInflater().inflate(R.layout.site_item,parent,false);
            }
            ImageView checkboxIcon = (ImageView) convertView.findViewById(R.id.checkbox_icon);
            TextView siteNameView = (TextView) convertView.findViewById(R.id.site_name);
            siteNameView.setText(name);
            if(position == selectIndex) {
                checkboxIcon.setImageResource(R.drawable.ic_checkbox_checked);
                convertView.setBackgroundColor(getResources().getColor(R.color.colorSelected));
            }
            else {
                checkboxIcon.setImageResource(R.drawable.ic_checkbox_uncheck);
                convertView.setBackgroundColor(Color.TRANSPARENT);
            }
            return convertView;
        }
    }
}

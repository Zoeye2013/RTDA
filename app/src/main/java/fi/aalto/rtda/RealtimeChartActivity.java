package fi.aalto.rtda;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.utils.ColorTemplate;

public class RealtimeChartActivity extends AppCompatActivity implements MeasuringBackgroundService.Callbacks, View.OnClickListener{

    private LineChart lineChart;
    private LineData data;

    //Intent for broadcast receiver
    public static final String INTENT_ACTION_DEVICE_NUMBER = "devicenum";
    public static final String INTENT_ACTION_NEW_RECORD = "newrecord";
    public static final String KEY_DEVICE_NUMBER = "number";

    //For representing lines
    int deviceNum;



    //Interaction with background service
    private MeasuringBackgroundService signalVectorService;
    private ServiceConnection serviceConnection;

    /* SharedPreferences to save user info and server time */
    private SharedPreferences sharedPref;
    private SharedPreferences.Editor editor;

    //Elements
    Button stopMeasurementButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_realtime_chart);
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

        sharedPref = this.getSharedPreferences(LoginActivity.SHAREDPREFERENCES,Context.MODE_PRIVATE);
        editor = sharedPref.edit();

        lineChart = (LineChart) findViewById(R.id.lineChart);
        stopMeasurementButton = (Button) findViewById(R.id.stop_measurement_button);
        stopMeasurementButton.setOnClickListener(this);
        bindBackgroundService();
        //deviceNum = signalVectorService.getMeasuringManager().getAllowedDevicesNumber();


        // no description text
        lineChart.setDescription("");
        lineChart.setNoDataTextDescription(getString(R.string.hint_waiting_measurement));
        // enable scaling and dragging
        lineChart.setScaleEnabled(true);
        lineChart.setDrawGridBackground(false);
        // set an alternative background color
        lineChart.setBackgroundColor(Color.LTGRAY);

        XAxis xl = lineChart.getXAxis();
        xl.setTextColor(Color.WHITE);
        xl.setDrawGridLines(false);
        xl.setAvoidFirstLastClipping(true);
        xl.setSpaceBetweenLabels(5);
        xl.setEnabled(true);
        xl.setPosition(XAxis.XAxisPosition.BOTTOM);

        YAxis leftAxis = lineChart.getAxisLeft();
        leftAxis.setTextColor(Color.WHITE);
        leftAxis.setAxisMaxValue(20f);
        leftAxis.setAxisMinValue(-100f);
        leftAxis.setDrawGridLines(true);

        YAxis rightAxis = lineChart.getAxisRight();
        rightAxis.setEnabled(false);
    }

    @Override
    protected void onDestroy() {
        unbindService(serviceConnection);
        super.onDestroy();
    }

    public void bindBackgroundService(){
        serviceConnection = new ServiceConnection() {

            /** When the connection with the service has been established */
            public void onServiceConnected(ComponentName className, IBinder service) {

                /** Get the service object we can use to interact with the service */
                signalVectorService = ((MeasuringBackgroundService.SignalVectorLocalBinder) service).getService();
                signalVectorService.registerClient(RealtimeChartActivity.this);
                deviceNum = signalVectorService.getMeasuringManager().getAllowedDevicesNumber();
                setChartData();
            }

            public void onServiceDisconnected(ComponentName arg0) { }
        };

        /** Bind service that responsible for recording Bluetooth RSSI value on RUN_STATE */
        Intent signalVectorServiceIntent = new Intent(this, MeasuringBackgroundService.class);
        bindService(signalVectorServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    //Implement Background service callback to get data change notification
    @Override
    public void notifyDataChange() {

        lineChart.notifyDataSetChanged();
        lineChart.invalidate();
        // limit the number of visible entries
        lineChart.setVisibleXRangeMaximum(20);
        // mChart.setVisibleYRange(30, AxisDependency.LEFT);

        // move to the latest entry
        lineChart.moveViewToX(data.getXValCount() - 21);
    }

    public void setChartData(){
        data = new LineData(signalVectorService.getXValues(),signalVectorService.getDataSets());
        data.setValueTextColor(Color.WHITE);
        // add empty data
        lineChart.setData(data);


        // get the legend (only possible after setting data)
        Legend l = lineChart.getLegend();

        // modify the legend ...
        l.setPosition(Legend.LegendPosition.ABOVE_CHART_RIGHT);
        l.setForm(Legend.LegendForm.LINE);
        l.setTextColor(Color.WHITE);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.stop_measurement_button:
                editor.remove(MeasuringActivity.SHARED_KEY_MEASUREMENT_STATUS);
                editor.commit();
                MeasuringActivity.isMeasurementStopped=true;
                //Save unsaved data
                if(signalVectorService.getMeasuringManager().getRecordsNum() >0){
                    signalVectorService.synchDataToServer();
                }
                stopService(new Intent(this, MeasuringBackgroundService.class));
                finish();
                break;
        }
    }
}

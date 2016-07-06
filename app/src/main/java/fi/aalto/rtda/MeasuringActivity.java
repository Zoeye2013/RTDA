package fi.aalto.rtda;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
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

public class MeasuringActivity extends AppCompatActivity implements AdapterView.OnItemClickListener, View.OnClickListener {

    private Context appContext;

    private int measurementStatus;

    /** For Site Selecting **/
    private ArrayList<String> sitesList;
    private ListView sitesListView;
    private SiteArrayAdapter sitesArrayAdapter;
    private int selectIndex = -1;
    private String siteName;

    /** For Measuring **/
    private ListView devicesListView;

    /** UI Elements **/
    private Button startButton;
    private Button stopMeasurementButton;
    private Button toBackgroundButton;
    private TextView selectSiteHintView;
    private TextView measuringHintView;
    private ProgressBar progressView;

    /* SharedPreferences to save user info and server time */
    private SharedPreferences sharedPref;
    private SharedPreferences.Editor editor;

    private long timeDifference;

    /** Interacting with background service **/
    private BluetoothBackgroundService btService;
    private ServiceConnection serviceConnection;
    private boolean isBind;
    private MeasuringManageClass measureManage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_measurement);

        appContext = this;
        timeDifference = getIntent().getLongExtra(HomeActivity.KEY_SERVER_TIME_DIFF,0);

        sharedPref = appContext.getSharedPreferences(HomeActivity.SHARED_PREFERENCES,Context.MODE_PRIVATE);
        editor = sharedPref.edit();
        measurementStatus = sharedPref.getInt(HomeActivity.SHARE_KEY_MEASUREMENT_STATUS,HomeActivity.STATUS_MEASUREMENT_IDLE);

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

        /** UI elements **/
        sitesListView = (ListView) findViewById(R.id.list_sites);
        devicesListView = (ListView) findViewById(R.id.list_bluetooth_device);

        progressView = (ProgressBar) findViewById(R.id.progress_measurement);

        startButton = (Button) findViewById(R.id.button_measurement);
        stopMeasurementButton = (Button) findViewById(R.id.button_end_measurement);
        toBackgroundButton = (Button) findViewById(R.id.button_to_background);

        selectSiteHintView = (TextView) findViewById(R.id.hint_choose_site);
        measuringHintView = (TextView) findViewById(R.id.hint_measurement_ongoing);
        startButton.setOnClickListener(this);
        stopMeasurementButton.setOnClickListener(this);
        toBackgroundButton.setOnClickListener(this);

        uiOnMeasurementStatus();
    }

    @Override
    protected void onDestroy() {
        /** Unbind background service **/
        if(isBind)
            unbindService(serviceConnection);
        super.onDestroy();
    }

    /** Load UI depends on Measurement Status **/
    public void uiOnMeasurementStatus(){
        switch (measurementStatus){
            /** When no measurement ongoing **/
            case HomeActivity.STATUS_MEASUREMENT_IDLE:
                devicesListView.setVisibility(View.GONE);
                stopMeasurementButton.setVisibility(View.GONE);
                toBackgroundButton.setVisibility(View.GONE);
                measuringHintView.setVisibility(View.GONE);

                /** Show list of sites for user to choose the measurement site **/
                sitesListView.setVisibility(View.VISIBLE);
                startButton.setVisibility(View.VISIBLE);
                selectSiteHintView.setVisibility(View.VISIBLE);
                enableStartButton();

                /** Init UI **/
                sitesList = new ArrayList<String>();
                sitesArrayAdapter = new SiteArrayAdapter(appContext,sitesList);
                sitesListView.setAdapter(sitesArrayAdapter);
                sitesListView.setOnItemClickListener(this);

                /** Fetch list of sites from server **/
                fetchSitesList();
                break;
            /** When there is measurement ongoing **/
            case HomeActivity.STATUS_MEASUREMENT_ONGING:
                sitesListView.setVisibility(View.GONE);
                startButton.setVisibility(View.GONE);
                selectSiteHintView.setVisibility(View.GONE);

                /** Show devices list of the selected measurement site **/
                devicesListView.setVisibility(View.VISIBLE);
                stopMeasurementButton.setVisibility(View.VISIBLE);
                measuringHintView.setVisibility(View.VISIBLE);
                toBackgroundButton.setVisibility(View.VISIBLE);
                bindBackgroundService();
                showProgress(true);
                break;
        }
    }

    /** Fetch List of sites from server, user can choose which is measurement site for this time **/
    public void fetchSitesList(){
        StringRequest stringRequest = new StringRequest(Request.Method.GET, HomeActivity.URL_FETCH_SITE_LIST, new Response.Listener<String>(){
            @Override
            public void onResponse(String response) {
                try {
                    JSONObject jsonObject = new JSONObject(response);
                    JSONArray jsonArray = jsonObject.getJSONArray(HomeActivity.KEY_JSONARRAY_LIST);
                    for (int i = 0; i < jsonArray.length(); i++) {
                        sitesList.add(jsonArray.getString(i));
                    }
                    sitesArrayAdapter.notifyDataSetChanged();
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


    /** Mark the site selected by user **/
    public void setSelectIndex(int index){
        selectIndex = index;
    }

    /** List of sites, item clicked listener **/
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        setSelectIndex(position);
        siteName = sitesList.get(position);
        enableStartButton();
        sitesArrayAdapter.notifyDataSetChanged();
    }

    /** Enable Start measurement button if there is site selected by user **/
    public void enableStartButton(){
        if(selectIndex >= 0){
            startButton.setEnabled(true);
            startButton.setBackgroundColor(getResources().getColor(R.color.button_enable));
        }
        else{
            startButton.setEnabled(false);
            startButton.setBackgroundColor(Color.GRAY);
        }
    }


    /** Handle buttons (Start measurement, end measurement & put to background) clicked actions **/
    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.button_measurement:
                /** Start measurement **/
                measurementStatus = HomeActivity.STATUS_MEASUREMENT_ONGING;
                editor.putInt(HomeActivity.SHARE_KEY_MEASUREMENT_STATUS,measurementStatus);
                editor.commit();

                /** Start Background Service for Bluetooth Detecting **/
                Intent btServiceIntent = new Intent(appContext,BluetoothBackgroundService.class);
                btServiceIntent.putExtra(HomeActivity.KEY_SERVER_TIME_DIFF,timeDifference);
                btServiceIntent.putExtra(HomeActivity.KEY_SERVICE_ACTION,HomeActivity.ACTION_MEASUREMENT);
                btServiceIntent.putExtra(HomeActivity.KEY_SITE_NAME,siteName);
                startService(btServiceIntent);

                /** Load UI depends on Measurement Status **/
                uiOnMeasurementStatus();
                break;
            case R.id.button_end_measurement:
                /** End measurement **/
                measurementStatus = HomeActivity.STATUS_MEASUREMENT_IDLE;
                editor.putInt(HomeActivity.SHARE_KEY_MEASUREMENT_STATUS,measurementStatus);
                editor.commit();
                btService.endCalibrationOrMeasurement();
                showProgress(false);
                if(isServiceRunning(BluetoothBackgroundService.class))
                    stopService(new Intent(this, BluetoothBackgroundService.class));
                finish();
                break;
            case R.id.button_to_background:
                /** Close Activity and the background service keep running **/
                showProgress(false);
                finish();
                break;
        }
    }

    /** Bind Background Bluetooth Detecting Service **/
    public void bindBackgroundService(){
        serviceConnection = new ServiceConnection() {

            /** When the connection with the service has been established */
            public void onServiceConnected(ComponentName className, IBinder service) {
                /** Get the service object we can use to interact with the service */
                btService = ((BluetoothBackgroundService.BTServiceLocalBinder) service).getService();
                measureManage = btService.getMeasureManage();
                //uiOnMeasurementStatus();
                devicesListView.setAdapter(measureManage.getSiteDevicesAdapter());
                isBind = true;
            }

            public void onServiceDisconnected(ComponentName arg0) { }
        };
        Intent btServiceIntent = new Intent(this, BluetoothBackgroundService.class);
        bindService(btServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    /** Check if Background Service is running **/
    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    /** ArrayAdpater for render site devices list **/
    public class SiteArrayAdapter extends ArrayAdapter<String> {

        public SiteArrayAdapter(Context context,ArrayList<String> siteNames){
            super(context,0,siteNames);
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            String name = getItem(position);
            if(convertView == null){
                convertView = ((Activity)appContext).getLayoutInflater().inflate(R.layout.item_site,parent,false);
            }
            ImageView checkboxIcon = (ImageView) convertView.findViewById(R.id.image_checkbox);
            TextView siteNameView = (TextView) convertView.findViewById(R.id.text_site_name);
            siteNameView.setText(name);
            if(position == selectIndex) {
                checkboxIcon.setImageResource(R.drawable.ic_checkbox_checked);
                convertView.setBackgroundColor(getResources().getColor(R.color.checkbox_selected));
            }
            else {
                checkboxIcon.setImageResource(R.drawable.ic_checkbox_uncheck);
                convertView.setBackgroundColor(Color.TRANSPARENT);
            }
            return convertView;
        }
    }

    /** Shows the progress bar  **/
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
    private void showProgress(final boolean show) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
            int shortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);

            progressView.setVisibility(show ? View.VISIBLE : View.GONE);
            progressView.animate().setDuration(shortAnimTime).alpha(
                    show ? 1 : 0).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    progressView.setVisibility(show ? View.VISIBLE : View.GONE);
                }
            });
        } else {
            progressView.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }
}

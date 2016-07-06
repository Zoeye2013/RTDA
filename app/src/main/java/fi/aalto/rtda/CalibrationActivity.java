package fi.aalto.rtda;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class CalibrationActivity extends AppCompatActivity implements View.OnClickListener {

    private Context appContext;

    /** UI elements **/
    private ListView listView;
    private EditText siteNameView;
    private ProgressBar progressView;
    private Button endCalibrationButton;
    private View saveHintView;
    private Button saveButton;
    private TextView devicesNumView;

    /** Interaction with background service **/
    private BluetoothBackgroundService btService;
    private ServiceConnection serviceConnection;
    private boolean isBind;
    private CalibrationManageClass calibrationManage;

    private String siteName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calibration);
        appContext = this;

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showProgress(false);
                finish();
            }
        });
        devicesNumView = (TextView) findViewById(R.id.text_allowed_devices_num);

        siteNameView = (EditText) findViewById(R.id.input_site_name);
        saveHintView = (View) findViewById(R.id.layout_hint);

        progressView = (ProgressBar) findViewById(R.id.progress_calibration);

        endCalibrationButton = (Button) findViewById(R.id.button_end_calibration);
        endCalibrationButton.setOnClickListener(this);
        saveButton = (Button) findViewById(R.id.button_save_site_devices);

        listView = (ListView) findViewById(R.id.list_bluetooth_device);

        /** Bind the background Bluetooth detection service for presenting results on UI **/
        bindBackgroundService();
        /** Progress bar will show until calibration is ended **/
        showProgress(true);
    }

    /** Handled before activity is destroyed **/
    @Override
    protected void onDestroy() {
        /** Unbind background service **/
        if(isBind)
            unbindService(serviceConnection);
        /** Stop background service **/
        if(isServiceRunning(BluetoothBackgroundService.class))
            stopService(new Intent(this, BluetoothBackgroundService.class));
        super.onDestroy();
    }

    /** Bind Background Bluetooth Detecting Service **/
    public void bindBackgroundService(){
        serviceConnection = new ServiceConnection() {

            /** When the connection with the service has been established */
            public void onServiceConnected(ComponentName className, IBinder service) {

                /** Get the service object we can use to interact with the service */
                btService = ((BluetoothBackgroundService.BTServiceLocalBinder) service).getService();
                calibrationManage = btService.getCalibrationManage();
                listView.setAdapter(calibrationManage.getSiteDevicesAdapter());
                isBind = true;
            }

            public void onServiceDisconnected(ComponentName arg0) { }
        };
        Intent intent = new Intent(this, BluetoothBackgroundService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
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

    /** Handle buttons (End Calibration & Save Site Devices) clicked actions **/
    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.button_end_calibration:
                /** End calibration **/
                showProgress(false);
                devicesNumView.setText(" " + calibrationManage.getSiteDevicesNumber() + " ");
                siteNameView.setVisibility(View.VISIBLE);
                saveHintView.setVisibility(View.VISIBLE);
                saveButton.setVisibility(View.VISIBLE);
                saveButton.setOnClickListener(this);
                endCalibrationButton.setVisibility(View.GONE);

                btService.endCalibrationOrMeasurement();
                if(isBind) {
                    unbindService(serviceConnection);
                    isBind = false;
                }
                break;
            case R.id.button_save_site_devices:
                /** Try to save detected devices to site **/
                attemptSaveSiteDevices();
                break;
        }
    }

    /** Save detected devices as site to Server **/
    private void attemptSaveSiteDevices(){
        siteNameView.setError(null);

        siteName = siteNameView.getText().toString();

        boolean cancel = false;
        View focusView = null;

        /** Front end validation **/
        if(siteName == null || TextUtils.isEmpty(siteName)){
            siteNameView.setError(getString(R.string.error_field_required));
            focusView = siteNameView;
            focusView.requestFocus();
            cancel = true;
        }else if(siteName.length() < 4){
            siteNameView.setError(getString(R.string.error_invalid_sitename));
            focusView = siteNameView;
            focusView.requestFocus();
            cancel = true;
        }

        if (!cancel) {
            /** Show a progress spinner, and kick off a background task to perform the user login attempt **/
            showProgress(true);
            saveToServer();
        }
    }

    /** Update site devices to Server **/
    public void saveToServer(){
        ConnectivityManager connectManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo wifiInfo = connectManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

        if(wifiInfo.isConnected()){
            String tempStr = "";
            ArrayList<BluetoothDevice> tempList = btService.getCalibrationManage().getSiteDevicesList();
            for(BluetoothDevice d: tempList){
                tempStr += d.toString() + ",";
            }
            final String siteDevicesString = tempStr;

            StringRequest stringRequest = new StringRequest(Request.Method.POST,HomeActivity.URL_SAVE_SITE,
                    new Response.Listener<String>() {
                        @Override
                        public void onResponse(String response) {
                            showProgress(false);
                            Toast.makeText(appContext, response, Toast.LENGTH_LONG).show();
                            if(response.trim().equals(HomeActivity.RESPONSE_SITE_EXIST)){
                                siteNameView.setError(getString(R.string.error_site_exist));
                                siteNameView.requestFocus();
                            }else if(response.trim().equals(HomeActivity.RESPONSE_SAVE_SITE_SUCCESSFUL)){
                                Toast.makeText(appContext, response, Toast.LENGTH_LONG).show();
                                finish();
                            }
                        }
                    }, new Response.ErrorListener(){
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            showProgress(false);
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
                            } else if (error instanceof NetworkError ) {
                                Log.e("Volley", "NetworkError");
                            } else if (error instanceof ParseError) {
                                Log.e("Volley", "ParseError");
                            }
                            Toast.makeText(appContext,error.toString(),Toast.LENGTH_LONG).show();
                            showProgress(false);
                        }
            }){
                @Override
                protected Map<String, String> getParams() throws AuthFailureError {
                    Map<String,String> params = new HashMap<String, String>();
                    params.put(HomeActivity.KEY_SITE_NAME,siteName);
                    params.put(HomeActivity.KEY_SITE_DEVICES,siteDevicesString);
                    return params;
                }
            };
            RequestQueue requestQueue = Volley.newRequestQueue(appContext);
            requestQueue.add(stringRequest);
        }
    }
}

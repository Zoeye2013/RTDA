package fi.aalto.rtda;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
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
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class BluetoothTrainingActivity extends AppCompatActivity implements View.OnClickListener {
    public static final String SAVE_ALLOWED_URL = "http://rtda.atwebpages.com/saveallowed.php";

    private ListView listView;
    private EditText siteNameView;
    private BluetoothHandleClass bluetoothManage;
    private Context appContext;
    private ProgressBar progressView;
    private Button stopTrainingButton;
    private View saveHintView;
    private Button saveButton;
    private TextView devicesNumView;
    private SaveAllowedDevicesTask saveTask = null;
    private String currUser;
    private String siteName;

    private boolean fileSavedSuccessfully = false;

    //Keys for transfering data to server
    public static final String KEY_USERNAME = "username";
    public static final String KEY_SITENAME = "sitename";
    public static final String KEY_ALLOWEDDEVICES = "alloweddevices";
    private static final String RESPONSE_SAVE_SUCCESSFUL = "Allowed devices saved";
    private static final String RESPONSE_SAVE_FAILED = "Save allowed devices failed";

    public static final String FILE_SAVED = "File saved successfully.";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth_training);
        appContext = this;
        currUser = getIntent().getStringExtra(LoginActivity.SHARED_KEY_LOGGED_USER);

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
        devicesNumView = (TextView) findViewById(R.id.allowed_devices_num);

        bluetoothManage = new BluetoothHandleClass(appContext,devicesNumView);
        listView = (ListView) findViewById(R.id.list_bluetooth_device);
        listView.setAdapter(bluetoothManage.getAllowedDevicesAdapter());

        siteNameView = (EditText) findViewById(R.id.site_name);
        saveHintView = (View) findViewById(R.id.layout_hint);


        progressView = (ProgressBar) findViewById(R.id.enable_bluetooth_progress);

        stopTrainingButton = (Button) findViewById(R.id.stop_training_button);
        stopTrainingButton.setOnClickListener(this);
        saveButton = (Button) findViewById(R.id.save_button);

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(bluetoothManage.getBtReceiver(), filter);

        bluetoothManage.clearAlllowedDevices();
        bluetoothManage.enableBTAdapter();
        showProgress(true);
    }

    @Override
    protected void onDestroy() {
        bluetoothManage.cancelDiscovery();
        if (bluetoothManage.getBtReceiver() != null) {
            unregisterReceiver(bluetoothManage.getBtReceiver());
        }
        bluetoothManage.disableBTAdapter();
        super.onDestroy();
    }


    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.stop_training_button:
                showProgress(false);
                devicesNumView.setText(" " + bluetoothManage.getAllowedDevicesNumber() + " ");
                siteNameView.setVisibility(View.VISIBLE);
                saveHintView.setVisibility(View.VISIBLE);
                saveButton.setVisibility(View.VISIBLE);
                saveButton.setOnClickListener(this);
                stopTrainingButton.setVisibility(View.GONE);
                bluetoothManage.stopTraining();
                break;
            case R.id.save_button:
                attemptSaveAllowedDevices();
                break;
        }
    }

    private void attemptSaveAllowedDevices(){
        // Reset errors.
        siteNameView.setError(null);

        // Store values at the time of the save attempt.
        siteName = siteNameView.getText().toString();

        boolean cancel = false;
        View focusView = null;

        // Check for a valid sitename
        if(siteName == null || TextUtils.isEmpty(siteName)){
            siteNameView.setError(getString(R.string.error_field_required));
            focusView = siteNameView;
            focusView.requestFocus();
            cancel = true;
        }else if(siteName.length() <= 4){
            siteNameView.setError(getString(R.string.error_invalid_password));
            focusView = siteNameView;
            focusView.requestFocus();
            cancel = true;
        }else{
            /* External option */
            /*String sdPath = Environment.getExternalStorageDirectory() + "/";
            //File file = new File(sdPath + LoginActivity.appHomeFolder + "/" + currUser + "/" + LoginActivity.rtdaAllowedFolder + "/" + siteName + ".csv");
            if(file.exists()){
                siteNameView.setError(getString(R.string.error_allowed_file_exist));
                focusView = siteNameView;
                focusView.requestFocus();
                cancel = true;
            }*/

            /* Internal Option */
            File userDir  = appContext.getDir(currUser, Context.MODE_PRIVATE);
            File fileName = new File(userDir,siteName);
            if(fileName.exists()) {
                siteNameView.setError(getString(R.string.error_allowed_file_exist));
                focusView = siteNameView;
                focusView.requestFocus();
                cancel = true;
            }
        }



        if (!cancel) {
            // Show a progress spinner, and kick off a background task to
            // perform the user login attempt.
            showProgress(true);
            saveToServer(bluetoothManage.getAllowedDevices());

            /*  Choose only one option save to internal or external */
            //saveTask = new SaveAllowedDevicesTask(bluetoothManage.getAllowedDevices());
            //saveTask.execute((Void) null);


        }
    }

    /** Shows the progress UI and hides the login form. */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
    private void showProgress(final boolean show) {
        // On Honeycomb MR2 we have the ViewPropertyAnimator APIs, which allow
        // for very easy animations. If available, use these APIs to fade-in
        // the progress spinner.
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
            // The ViewPropertyAnimator APIs are not available, so simply show
            // and hide the relevant UI components.
            progressView.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    /** Represents an asynchronous login/registration task used to authenticate the user.  */
    public class SaveAllowedDevicesTask extends AsyncTask<Void, Void, Boolean> {

        private final String userName;
        private final ArrayList<BluetoothDevice> allowedDevices;
        private final String fileName;

        SaveAllowedDevicesTask(ArrayList<BluetoothDevice> list) {
            userName = currUser;
            allowedDevices = list;
            fileName = siteName;
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            saveToPhoneStorage(allowedDevices);
            return true;
        }

        @Override
        protected void onPostExecute(final Boolean success) {
            showProgress(false);
            if(fileSavedSuccessfully){
                Toast.makeText(appContext, FILE_SAVED, Toast.LENGTH_LONG).show();
                finish();
            }
        }

        @Override
        protected void onCancelled() {
            showProgress(false);
        }
    }

    /* Save to External storage as csv file */
    public void saveToPhoneStorage(ArrayList<BluetoothDevice> allowedDevices){
        String allowedDeviceInfo = "";
        try {
            String sdPath = Environment.getExternalStorageDirectory() + "/";
            File file = new File(sdPath + LoginActivity.appHomeFolder + "/" + currUser +
                    "/" + LoginActivity.rtdaAllowedFolder + "/" + siteName + ".csv");
            FileOutputStream fileOut = new FileOutputStream(file); //only allow this App access it, overwrite mode
            OutputStreamWriter outWriter = new OutputStreamWriter(fileOut);
            BufferedWriter bfWriter = new BufferedWriter(outWriter);

            allowedDeviceInfo += "Device Number,Device Name,Device Class,Address\n";

            Log.i("RTDA", allowedDeviceInfo);
            for(int i = 0; i < allowedDevices.size(); i++)
            {
                allowedDeviceInfo += (i+1) + ","
                        + allowedDevices.get(i).getName() + ","
                        + allowedDevices.get(i).getBluetoothClass().getMajorDeviceClass()
                        + "," + allowedDevices.get(i).getAddress() + "\n";
            }
            bfWriter.write(allowedDeviceInfo);
            bfWriter.close();
            fileSavedSuccessfully = true;
        } catch (FileNotFoundException e1) {
            fileSavedSuccessfully = false;
            e1.printStackTrace();
        } catch (IOException e1) {
            fileSavedSuccessfully = false;
            e1.printStackTrace();
        }
    }

    public void saveToServer(ArrayList<BluetoothDevice> allowedDevices){

        JSONArray allowedDevicesJSON = new JSONArray(allowedDevices);
        //Save to Internal storage
        String allowedDeviceInfo = allowedDevicesJSON.toString();
        try {
            File userDir  = appContext.getDir(currUser, Context.MODE_PRIVATE);
            File fileName = new File(userDir,siteName);
            FileOutputStream fileOutPut = new FileOutputStream(fileName);
            fileOutPut.write(allowedDeviceInfo.getBytes());
            fileOutPut.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e){
            e.printStackTrace();
        }

        //Sending allowed devices list to Server
        ConnectivityManager connectManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo wifiInfo = connectManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if(wifiInfo.isConnected()){
            String tempString = "";
            for (BluetoothDevice s: allowedDevices){
                tempString += s.toString() + "\t";
            }
            final String allowedDevicesString = tempString;
            StringRequest stringRequest = new StringRequest(Request.Method.POST,SAVE_ALLOWED_URL,
                    new Response.Listener<String>() {
                        @Override
                        public void onResponse(String response) {
                            showProgress(false);
                            Toast.makeText(appContext, response, Toast.LENGTH_LONG).show();
                            if(response.trim().equals(RESPONSE_SAVE_SUCCESSFUL)){
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
                    params.put(KEY_USERNAME,currUser);
                    params.put(KEY_SITENAME,siteName);
                    params.put(KEY_ALLOWEDDEVICES,allowedDevicesString);
                    return params;
                }
            };

            RequestQueue requestQueue = Volley.newRequestQueue(appContext);
            requestQueue.add(stringRequest);
        }
    }
}

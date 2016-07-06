package fi.aalto.rtda;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class HomeActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, View.OnClickListener {

    private Context appContext = this;

    /** Shared Preferences **/
    public static SharedPreferences sharedPref;
    public static SharedPreferences.Editor editor;

    /** UI Elements **/
    private Button calibrationBtn;
    private Button measurementBtn;
    private NavigationView navigationView;
    private TextView loggedInUserView;
    private TextView welcomeView;

    /** Manager role **/
    private String currUser;
    private long timeDifference;

    /** Start Activity for Results **/
    public static final int REQUEST_CODE_LOGIN = 1;
    public static final int REQUEST_CODE_UPLOAD_FILE = 2;

    /** KEY Strings **/
    public static final String KEY_USERNAME = "username";
    public static final String KEY_PASSWORD = "password";
    public static final String KEY_SERVER_TIME_DIFF = "timediff";

    public static final String KEY_IF_LOGIN_SUCCESSFUL = "ifloggin";

    public static final String SHARED_PREFERENCES = "RTDA";
    public static final String SHARED_KEY_LOGGED_USER = "loggeduser";
    public static final String SHARED_KEY_PASSWORD = "password";
    public static final String SHARED_KEY_SERVER_TIME = "servertime";
    public static final String SHARE_KEY_MEASUREMENT_STATUS = "measurement_status";

    public static final String KEY_SERVICE_ACTION = "service_action";

    public static final String KEY_RECORDS = "records";
    public static final String KEY_LOCAL_BT_ADDRESS = "localbtaddress";
    public static final String KEY_DATE ="date";
    public static final String KEY_SITE_NAME = "sitename";
    public static final String KEY_SITE_DEVICES = "sitedevices";

    public static final String KEY_JSONARRAY_LIST = "result";
    public static final String KEY_JSONARRAY_ADDRESS = "btaddress";

    /** Server URLs */
    public static final String URL_LOGIN = "http://rtda.atwebpages.com/login.php";

    public static final String URL_SAVE_SITE = "http://rtda.atwebpages.com/savesite.php";
    public static final String URL_SAVE_CALIBRATION = "http://rtda.atwebpages.com/savecalibration.php";
    public static final String URL_SAVE_MEASUREMENT = "http://rtda.atwebpages.com/savemeasurement.php";

    public static final String URL_FETCH_ALLOWED_LIST = "http://rtda.atwebpages.com/fetchallowed.php";
    public static final String URL_FETCH_SITE_LIST = "http://rtda.atwebpages.com/fetchsites.php";
    public static final String URL_FETCH_SITE_DEVICES_LIST = "http://rtda.atwebpages.com/fetchsitedevices.php";

    /** URL responses **/
    public static final String RESPONSE_SAVE_SUCCESSFUL = "Tried save";
    public static final String RESPONSE_SAVE_SITE_SUCCESSFUL = "Site devices saved";
    public static final String RESPONSE_SITE_EXIST = "Site exist";
    public static final String RESPONSE_SAVE_SITE_FAILED = "Save site devices failed";
    public static final String RESPONSE_LOGIN_FAILURE = "Login failure";
    public static final String RESPONSE_LOGIN_SUCCESS = "Login success";

    /** Folder Paths **/
    public static final String PATH_SDCARD = Environment.getExternalStorageDirectory().toString();
    public static final String PATH_FOLDER_HOME = PATH_SDCARD + "/RTDA";
    public static final String PATH_FOLDER_ALLOWED_SETS = "Allowed_Devices";
    public static final String PATH_FOLDER_SIGNAL_VECTORS = "Signal_Vectors";

    /** Action ints **/
    public static final int ACTION_CALIBRATION = 1;
    public static final int ACTION_MEASUREMENT = 2;

    /** Status ints **/
    public static final int STATUS_MEASUREMENT_ONGING = 2;
    public static final int STATUS_MEASUREMENT_IDLE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        sharedPref = appContext.getSharedPreferences(SHARED_PREFERENCES,Context.MODE_PRIVATE);
        editor = sharedPref.edit();
        currUser = sharedPref.getString(SHARED_KEY_LOGGED_USER,"");
        timeDifference = sharedPref.getLong(SHARED_KEY_SERVER_TIME,0);

        View homeLayout = findViewById(R.id.layout_home);
        View homeContentLayout = homeLayout.findViewById(R.id.layout_home_content);
        calibrationBtn = (Button) homeContentLayout.findViewById(R.id.button_calibration);
        measurementBtn = (Button) homeContentLayout.findViewById(R.id.button_measurement);
        calibrationBtn.setOnClickListener(this);
        measurementBtn.setOnClickListener(this);


        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        /** Set Navigation Information **/
        navigationView = (NavigationView) findViewById(R.id.nav_view);
        View navHeaderView = navigationView.getHeaderView(0);
        loggedInUserView = (TextView) navHeaderView.findViewById(R.id.text_header_welcome);
        welcomeView = (TextView) navHeaderView.findViewById(R.id.text_header_login_user);
        navigationView.setNavigationItemSelectedListener(this);

        isLoggedIn();
    }

    @Override
    protected void onResume() {
        super.onResume();
        /** Based on measurement status (idle/ongoing) updates measurement button text **/
        int measurementStatus = sharedPref.getInt(SHARE_KEY_MEASUREMENT_STATUS,HomeActivity.STATUS_MEASUREMENT_IDLE);
        switch (measurementStatus){
            case STATUS_MEASUREMENT_IDLE:
                measurementBtn.setText(R.string.button_measurement);
                break;
            case STATUS_MEASUREMENT_ONGING:
                measurementBtn.setText(R.string.button_measuring);
                break;
        }
    }

    /** Handle the action of system's back button cliced **/
    @Override
    public void onBackPressed() {
        /** If navigation drawer open, close navigation drawer, else close HomeActivity **/
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    /** Handle navigation menu item clicked action **/
    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_settings) {
        } else if (id == R.id.nav_profile) {

        } else if (id == R.id.nav_logout) {
            currUser = "";
            editor.remove(SHARED_KEY_LOGGED_USER);
            editor.apply();
            isLoggedIn();

        }/** Login Menu item clicked, start LoginActivity for result **/
        else if(id == R.id.nav_login){
            Intent loginIntent = new Intent(appContext,LoginActivity.class);
            startActivityForResult(loginIntent,REQUEST_CODE_LOGIN);
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    /** Handle buttons(Calibration & Measurement) clicked actions **/
    @Override
    public void onClick(View v) {
        /** Only when Bluetooth is avaible on the phone **/
        if(openBTAdapter()){
            switch (v.getId()){
                case R.id.button_calibration:
                    /** Start Background Service to take care of calibration work **/
                    Intent btServiceIntent = new Intent(appContext,BluetoothBackgroundService.class);
                    btServiceIntent.putExtra(KEY_SERVER_TIME_DIFF,timeDifference);
                    btServiceIntent.putExtra(KEY_SERVICE_ACTION,ACTION_CALIBRATION);
                    startService(btServiceIntent);
                    break;
                case R.id.button_measurement:
                    /** Start MeasurementActivity for measurement **/
                    Intent intent = new Intent(appContext, MeasuringActivity.class);
                    intent.putExtra(KEY_SERVER_TIME_DIFF,timeDifference);
                    startActivity(intent);
                    break;
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        /** Handle results from LoginActivity **/
        if(requestCode == REQUEST_CODE_LOGIN && data != null){
            if(data.getBooleanExtra(KEY_IF_LOGIN_SUCCESSFUL,false)) {
                currUser = data.getStringExtra(KEY_USERNAME);
                timeDifference = data.getLongExtra(SHARED_KEY_SERVER_TIME,0);
                if(timeDifference > 0)
                    editor.putLong(SHARED_KEY_SERVER_TIME,timeDifference);
                editor.putString(SHARED_KEY_LOGGED_USER, currUser);
                editor.commit();
                isLoggedIn();
            }
        }
    }

    /** Update Homepage UI based on whether admin logged in **/
    public void isLoggedIn(){
        boolean login = currUser.length() > 0;

        navigationView.getMenu().getItem(0).setVisible(!login);
        navigationView.getMenu().getItem(1).setVisible(login);
        loggedInUserView.setText(currUser);

        if(login) {
            calibrationBtn.setVisibility(View.VISIBLE);
            welcomeView.setText(R.string.text_welcome_as_admin);
        }
        else {
            calibrationBtn.setVisibility(View.GONE);
            welcomeView.setText(R.string.text_welcome);
        }
    }

    /** Check whether the device has Bluetooth before process functions that require Bluetooth **/
    public boolean openBTAdapter()
    {
        boolean btSuccessful = true;
        if (BluetoothAdapter.getDefaultAdapter() == null)
        {
            /** If the device doesn't support Bluetooth, the end this module */
            Toast toast = Toast.makeText(appContext, R.string.error_no_bluetooth, Toast.LENGTH_LONG);
            btSuccessful = false;
        }
        return btSuccessful;
    }
}

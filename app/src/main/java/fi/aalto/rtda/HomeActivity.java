package fi.aalto.rtda;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.util.Log;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class HomeActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, View.OnClickListener {

    private Context appContext = this;
    private SharedPreferences sharedPref;
    SharedPreferences.Editor editor;

    //UI
    private Button trainingBtn;
    private Button measurementBtn;
    private String currUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        currUser = getIntent().getStringExtra(LoginActivity.SHARED_KEY_LOGGED_USER);

        sharedPref = appContext.getSharedPreferences(LoginActivity.SHAREDPREFERENCES,Context.MODE_PRIVATE);
        editor = sharedPref.edit();

        View homeLayout = findViewById(R.id.layout_home);
        View homeContentLayout = homeLayout.findViewById(R.id.layout_home_content);
        trainingBtn = (Button) homeContentLayout.findViewById(R.id.start_training_button);
        measurementBtn = (Button) homeContentLayout.findViewById(R.id.start_measurement_button);
        trainingBtn.setOnClickListener(this);
        measurementBtn.setOnClickListener(this);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        View navHeaderView = navigationView.getHeaderView(0);
        TextView loggedInUserView = (TextView) navHeaderView.findViewById(R.id.logged_in_user);
        loggedInUserView.setText(currUser);
        navigationView.setNavigationItemSelectedListener(this);
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_settings) {
            // Handle the camera action
        } else if (id == R.id.nav_profile) {

        } else if (id == R.id.nav_logout) {
            editor.remove(LoginActivity.SHARED_KEY_LOGGED_USER);
            editor.remove(LoginActivity.SHARED_KEY_PASSWORD);
            editor.apply();
            Intent intent = new Intent(appContext,LoginActivity.class);
            startActivity(intent);
            finish();
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        switch (id){
            case R.id.start_training_button:
                if(BluetoothHandleClass.openBTAdapter(appContext)){
                    Intent intent = new Intent(appContext,BluetoothTrainingActivity.class);
                    intent.putExtra(LoginActivity.SHARED_KEY_LOGGED_USER,currUser);
                    startActivity(intent);
                }
                break;
            case R.id.stop_measurement_button:
                break;
        }
    }



}

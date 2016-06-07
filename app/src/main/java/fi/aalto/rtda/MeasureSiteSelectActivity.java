package fi.aalto.rtda;

import android.app.Activity;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
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

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

public class MeasureSiteSelectActivity extends AppCompatActivity implements AdapterView.OnItemClickListener, View.OnClickListener {

    private Context appContext;
    private String currUser;
    private File userDir;

    //private ViewGroup sitesGroupView;
    private ListView sitesListView;
    private SiteArrayAdapter sitesArrayAdapter;
    private int selectIndex = -1;

    private Button startButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_measure_site_select);

        appContext = this;
        currUser = getIntent().getStringExtra(LoginActivity.SHARED_KEY_LOGGED_USER);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        //sitesGroupView = (ViewGroup) findViewById(R.id.radio_group_sites);
        sitesListView = (ListView) findViewById(R.id.list_sites); //Testing
        userDir  = appContext.getDir(currUser, Context.MODE_PRIVATE);
        loadSitesList();

        startButton = (Button) findViewById(R.id.start_measurement_button);
        enableStartButton();
        startButton.setOnClickListener(this);
    }

    public void loadSitesList() {
        File lister = userDir.getAbsoluteFile();
        String[] list = lister.list();
        ArrayList<String> stringList = new ArrayList<String>(Arrays.asList(list));
        sitesArrayAdapter = new SiteArrayAdapter(appContext,stringList);
        sitesListView.setAdapter(sitesArrayAdapter);
        sitesListView.setOnItemClickListener(this);
    }

    public void setSelectIndex(int index){
        selectIndex = index;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        setSelectIndex(position);
        enableStartButton();
        sitesArrayAdapter.notifyDataSetChanged();

    }

    public void enableStartButton(){
        if(selectIndex >= 0){
            startButton.setEnabled(true);
            startButton.setBackgroundColor(getResources().getColor(R.color.colorEnabled));
        }
        else{
            startButton.setEnabled(false);
            startButton.setBackgroundColor(Color.GRAY);
        }

    }

    public void loadAllowedDevices(){

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.start_measurement_button:
                break;
        }
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
                checkboxIcon.setVisibility(View.VISIBLE);
                convertView.setBackgroundColor(getResources().getColor(R.color.colorSelected));
            }
            else {
                checkboxIcon.setVisibility(View.INVISIBLE);
                convertView.setBackgroundColor(Color.TRANSPARENT);
            }
            return convertView;
        }
    }
}

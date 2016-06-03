package fi.aalto.rtda;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.ContentResolver;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build.VERSION;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

import org.json.JSONException;
import org.json.JSONObject;

import static android.Manifest.permission.READ_CONTACTS;

/** A login screen that offers login via email/password. */
public class LoginActivity extends AppCompatActivity{

    /** Id to identity READ_CONTACTS permission request. */
    private static final int REQUEST_READ_CONTACTS = 0;
    /** Server URL */
    public static final String REGISTER_URL = "http://rtda.atwebpages.com/register.php";
    public static final String LOGIN_URL = "http://rtda.atwebpages.com/login.php";

    public static final String KEY_USERNAME = "username";
    public static final String KEY_PASSWORD = "password";
    private static final int ACTION_REGISTER = 1;
    private static final int ACTION_LOGIN = 2;
    private static final String RESPONSE_USER_EXIST = "Could not register";
    private static final String RESPONSE_REGISTER_SUCCESS = "Successfully Registered";
    private static final String RESPONSE_LOGIN_FAILURE = "Login failure";
    private static final String RESPONSE_LOGIN_SUCCESS = "Login success";

    private final Context appContext = this;

    /** Keep track of the login task to ensure we can cancel it if requested. */
    private UserRegisterOrLoginTask authTask = null;

    /*  UI references. */
    private EditText usernameView;
    private EditText passwordView;
    private View progressView;
    private View loginFormView;
    private Button signInButton;
    private Button registerButton;

    /* SharedPreferences to save user info and server time */
    private SharedPreferences sharedPref;
    private SharedPreferences.Editor editor;
    public static final String SHAREDPREFERENCES = "RTDA";
    public static final String SHARED_KEY_LOGGED_USER = "loggeduser";
    public static final String SHARED_KEY_PASSWORD = "password";
    public static final String SHARED_KEY_SERVER_TIME = "servertime";

    /* Save files on phone storage */
    /** Folder or file names of modules in the App */
    private String appHomePath;
    /** Phone SDcard Path */
    public static final String sdCardPath = Environment.getExternalStorageDirectory().toString();
    /** Hospital Helper Home Folder */
    public static final String appHomeFolder = "RTDA";
    /** Folders for Bluetooth Process Data Acquisition Module */
    public static final String rtdaAllowedFolder = "Allowed_Devices";
    public static final String rtdaSignalVectorFolder = "Signal_Vectors";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sharedPref = appContext.getSharedPreferences(SHAREDPREFERENCES,Context.MODE_PRIVATE);
        editor = sharedPref.edit();

        createAppHomeFolder();

        String currUser = sharedPref.getString(SHARED_KEY_LOGGED_USER,"");
        if(!checkIfUserLoggedIn(currUser)){
            setContentView(R.layout.activity_login);

            // Set up the login form.
            usernameView = (EditText) findViewById(R.id.username);
            passwordView = (EditText) findViewById(R.id.password);

            signInButton = (Button) findViewById(R.id.sign_in_button);
            registerButton = (Button) findViewById(R.id.register_button);
            signInButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    attemptRegisterOrLogin(ACTION_LOGIN);
                }
            });
            registerButton.setOnClickListener(new OnClickListener(){
                @Override
                public void onClick(View view) {
                    attemptRegisterOrLogin(ACTION_REGISTER);
                }
            });

            loginFormView = findViewById(R.id.login_form);
            progressView = findViewById(R.id.login_progress);
        }else{
            createUserFolders(currUser);
            Intent intent = new Intent(appContext,HomeActivity.class);
            intent.putExtra(SHARED_KEY_LOGGED_USER,currUser);
            startActivity(intent);
            finish();
        }

    }

    /** Check if user already logged in */
    private boolean checkIfUserLoggedIn(String loggedUser){
        boolean ifLoggedIn = false;
        if(loggedUser!= null && loggedUser.length()>0)
            ifLoggedIn = true;
        return ifLoggedIn;
    }

    private void attemptRegisterOrLogin(int actionCode){
        // Reset errors.
        usernameView.setError(null);
        passwordView.setError(null);

        // Store values at the time of the login attempt.
        final String username = usernameView.getText().toString();
        final String password = passwordView.getText().toString();

        boolean cancel = false;
        View focusView = null;

        // Check for a valid password
        if(TextUtils.isEmpty(password)){
            passwordView.setError(getString(R.string.error_field_required));
            focusView = passwordView;
            cancel = true;
        }else if(actionCode == ACTION_REGISTER && !isPasswordValid(password)){
            passwordView.setError(getString(R.string.error_invalid_password));
            focusView = passwordView;
            cancel = true;
        }

        // Check for a valid username
        if (TextUtils.isEmpty(username)) {
            usernameView.setError(getString(R.string.error_field_required));
            focusView = usernameView;
            cancel = true;
        } else if (actionCode == ACTION_REGISTER && !isUsernameValid(username)) {
            usernameView.setError(getString(R.string.error_invalid_username));
            focusView = usernameView;
            cancel = true;
        }

        if (cancel) {
            // There was an error; don't attempt login and focus the first
            // form field with an error.
            focusView.requestFocus();
        } else {
            // Show a progress spinner, and kick off a background task to
            // perform the user login attempt.
            showProgress(true);
            authTask = new UserRegisterOrLoginTask(username, password,actionCode);
            authTask.execute((Void) null);

        }
    }


    private boolean isUsernameValid(String username) {
        //TODO: Replace this with your own logic
        return username.length() > 4;
    }

    private boolean isPasswordValid(String password) {
        //TODO: Replace this with your own logic
        return password.length() > 4;
    }

    /** Shows the progress UI and hides the login form. */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
    private void showProgress(final boolean show) {
        // On Honeycomb MR2 we have the ViewPropertyAnimator APIs, which allow
        // for very easy animations. If available, use these APIs to fade-in
        // the progress spinner.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
            int shortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);

            loginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
            loginFormView.animate().setDuration(shortAnimTime).alpha(
                    show ? 0 : 1).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    loginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
                }
            });

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
            loginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
        }
    }

    /** Represents an asynchronous login/registration task used to authenticate the user.  */
    public class UserRegisterOrLoginTask extends AsyncTask<Void, Void, Boolean> {

        private final String username;
        private final String password;
        private final int actionCode;

        UserRegisterOrLoginTask(String user, String pwd, int action) {
            username = user;
            password = pwd;
            actionCode = action;
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            String requstURL = LOGIN_URL;
            switch (actionCode){
                case ACTION_REGISTER:
                    requstURL = REGISTER_URL;
                    break;
                case ACTION_LOGIN:
                    requstURL = LOGIN_URL;
                    break;
            }
            StringRequest stringRequest = new StringRequest(Request.Method.POST, requstURL, new Response.Listener<String>(){
                @Override
                public void onResponse(String response) {
                    showProgress(false);
                    switch(actionCode){
                        case ACTION_REGISTER:
                            if(response.trim().equals(RESPONSE_USER_EXIST)){
                                usernameView.setError(getString(R.string.error_exsit_username));
                                usernameView.requestFocus();
                            }else if(response.trim().equals(RESPONSE_REGISTER_SUCCESS)){
                                Toast.makeText(appContext, response, Toast.LENGTH_LONG).show();
                                createUserFolders(username);
                                editor.putString(SHARED_KEY_LOGGED_USER,username);
                                editor.putString(SHARED_KEY_PASSWORD,password);
                                editor.commit();
                                Intent intent = new Intent(appContext,HomeActivity.class);
                                intent.putExtra(SHARED_KEY_LOGGED_USER,username);
                                startActivity(intent);
                                finish();
                            }
                            break;
                        case ACTION_LOGIN:
                            if(response.trim().equals(RESPONSE_LOGIN_FAILURE)){
                                passwordView.setError(getString(R.string.error_incorrect_username_or_password));
                                passwordView.requestFocus();
                            }else if(response.trim().contains(RESPONSE_LOGIN_SUCCESS)){
                                double serverTime = 0;
                                editor.putString(SHARED_KEY_LOGGED_USER,username);
                                editor.putString(SHARED_KEY_PASSWORD,password);
                                editor.putLong(SHARED_KEY_SERVER_TIME,Double.doubleToRawLongBits(serverTime));
                                editor.commit();
                                try {
                                    JSONObject jsonObject = new JSONObject(response);
                                    serverTime = jsonObject.getDouble("Login success");
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                                Toast.makeText(appContext, RESPONSE_LOGIN_SUCCESS, Toast.LENGTH_LONG).show();
                                Intent intent = new Intent(appContext,HomeActivity.class);
                                intent.putExtra(SHARED_KEY_LOGGED_USER,username);
                                startActivity(intent);
                                finish();
                            }
                            break;
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
                    params.put(KEY_USERNAME,username);
                    params.put(KEY_PASSWORD,password);
                    return params;
                }
            };

            RequestQueue requestQueue = Volley.newRequestQueue(appContext);
            requestQueue.add(stringRequest);

            return true;
        }

        @Override
        protected void onPostExecute(final Boolean success) {
            authTask = null;
            //showProgress(false);

            if (success) {
                //finish();
            } else {
                //passwordView.setError(getString(R.string.error_incorrect_password));
                //passwordView.requestFocus();
            }
        }

        @Override
        protected void onCancelled() {
            authTask = null;
            showProgress(false);
        }
    }

    /** Create Application Home folder if it doesn't exist */
    public void createAppHomeFolder()
    {
        /** If Phone's SDcard is available, then create Home folder for the application */
        if(Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED))
        {
            appHomePath = Environment.getExternalStorageDirectory() + "/" + appHomeFolder;
            File homeDir = new File(appHomePath);
            if(!homeDir.exists())
            {
                Log.i("RTDA", "create App home folder");
                homeDir.mkdir();
            }
        }
        /** If SDcard is not available, inform user
         * 	that data will not be saved and end the application */
        else {
            Toast toast = Toast.makeText(appContext, R.string.error_sd_card_not_available, Toast.LENGTH_LONG);
            toast.show();
        }
    }

    public void createUserFolders(String userName){
        if(Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            String userFolderPath = Environment.getExternalStorageDirectory()+ "/" + appHomeFolder + "/" + userName;
            File userDir = new File(userFolderPath);
            if(!userDir.exists()) {
                userDir.mkdir();

                String folderDir = userFolderPath + "/" + rtdaAllowedFolder;
                File temp = new File(folderDir);
                if(!temp.exists())
                    temp.mkdir();

                folderDir = userFolderPath + "/" + rtdaSignalVectorFolder;
                temp = new File(folderDir);
                if(!temp.exists())
                    temp.mkdir();
            }
        }
    }
}


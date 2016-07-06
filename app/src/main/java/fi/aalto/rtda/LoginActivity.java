package fi.aalto.rtda;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import java.util.Calendar;
import java.util.HashMap;
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

/** A login screen that offers login via email/password. */
public class LoginActivity extends AppCompatActivity{
    private Context appContext;

    /*  UI Elements */
    private EditText usernameView;
    private EditText passwordView;
    private View progressView;
    private Button signInButton;

    private String username;
    private String password;

    private Intent resultIntent; //Return results to HomeActivity
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        appContext = this;
        setTitle("");

        usernameView = (EditText) findViewById(R.id.input_username);
        passwordView = (EditText) findViewById(R.id.input_password);
        signInButton = (Button) findViewById(R.id.button_signin);
        signInButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                loginFrontEndValidation();
            }
        });
        progressView = findViewById(R.id.progress_login);

        resultIntent = new Intent();

        //createAppHomeFolder();
    }

    /** Front-end validation for login action **/
    private void loginFrontEndValidation(){
        /** Reset errors **/
        usernameView.setError(null);
        passwordView.setError(null);

        /** Values at the time of the login attempt. **/
        username = usernameView.getText().toString();
        password = passwordView.getText().toString();

        boolean cancel = false;
        View focusView = null;

        /** Check for a valid password **/
        if(TextUtils.isEmpty(password)){
            passwordView.setError(getString(R.string.error_field_required));
            focusView = passwordView;
            cancel = true;
        }

        /** Check for a valid username **/
        if (TextUtils.isEmpty(username)) {
            usernameView.setError(getString(R.string.error_field_required));
            focusView = usernameView;
            cancel = true;
        }

        if (cancel) {
            /** Form fields with error **/
            focusView.requestFocus();
        } else {
            /** Show a progress spinner, and try to perform the user login attempt **/
            showProgress(true);
            loginTask(username,password);
        }
    }


    /** Shows the progress spinner */
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

    /** Login to Server with Volley Library **/
    /** Http POST request to login with user entered username and password **/
    public void loginTask(String user, String pwd){
        final String username = user;
        final String password = pwd;

        StringRequest stringRequest = new StringRequest(Request.Method.POST, HomeActivity.URL_LOGIN, new Response.Listener<String>(){
            @Override
            public void onResponse(String response) {
                showProgress(false);
                if(response.trim().equals(HomeActivity.RESPONSE_LOGIN_FAILURE)){
                    /** Back-end validation for login action **/
                    passwordView.setError(getString(R.string.error_incorrect_username_or_password));
                    passwordView.requestFocus();
                }else if(response.trim().contains(HomeActivity.RESPONSE_LOGIN_SUCCESS)){
                    Calendar calendar = Calendar.getInstance();
                    Long timeInMills = calendar.getTimeInMillis();
                    /** Synchronize Server Time **/
                    long serverTime = 0;
                    try {
                        JSONObject jsonObject = new JSONObject(response);
                        serverTime = jsonObject.getLong(HomeActivity.RESPONSE_LOGIN_SUCCESS);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                    Toast.makeText(appContext, HomeActivity.RESPONSE_LOGIN_SUCCESS, Toast.LENGTH_LONG).show();
                    /** Return results to HomeActivity **/
                    resultIntent.putExtra(HomeActivity.KEY_IF_LOGIN_SUCCESSFUL,true);
                    resultIntent.putExtra(HomeActivity.KEY_USERNAME,username);
                    resultIntent.putExtra(HomeActivity.SHARED_KEY_SERVER_TIME,serverTime - timeInMills);
                    setResult(HomeActivity.REQUEST_CODE_LOGIN,resultIntent);
                    finish();
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
                params.put(HomeActivity.KEY_USERNAME,username);
                params.put(HomeActivity.KEY_PASSWORD,password);
                return params;
            }
        };

        RequestQueue requestQueue = Volley.newRequestQueue(appContext);
        requestQueue.add(stringRequest);
    }
}


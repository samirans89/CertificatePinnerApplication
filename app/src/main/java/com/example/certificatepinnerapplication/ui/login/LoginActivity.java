package com.example.certificatepinnerapplication.ui.login;

import android.app.Activity;

import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.CertificatePinner;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.certificatepinnerapplication.R;
import com.example.certificatepinnerapplication.ui.login.LoginViewModel;
import com.example.certificatepinnerapplication.ui.login.LoginViewModelFactory;

import java.io.IOException;
import java.security.cert.Certificate;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

import static okhttp3.OkHttpClient.*;

public class LoginActivity extends AppCompatActivity {

    private LoginViewModel loginViewModel;
    String hostname = "";
    String shaPublicKey = "";
    private String returnVal = "";
    private Handler mHandler;
    private String mMessage;
    private TextView resultEditText;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        loginViewModel = ViewModelProviders.of(this, new LoginViewModelFactory())
                .get(LoginViewModel.class);

        final EditText usernameEditText = findViewById(R.id.username);
        final EditText passwordEditText = findViewById(R.id.password);
        resultEditText = findViewById(R.id.result);

        final Button loginButton = findViewById(R.id.login);
        final ProgressBar loadingProgressBar = findViewById(R.id.loading);
        mHandler = new Handler(Looper.getMainLooper());

        loginViewModel.getLoginFormState().observe(this, new Observer<LoginFormState>() {
            @Override
            public void onChanged(@Nullable LoginFormState loginFormState) {
                if (loginFormState == null) {
                    return;
                }
                loginButton.setEnabled(loginFormState.isDataValid());
                if (loginFormState.getUsernameError() != null) {
                    usernameEditText.setError(getString(loginFormState.getUsernameError()));
                }
                if (loginFormState.getPasswordError() != null) {
                    passwordEditText.setError(getString(loginFormState.getPasswordError()));
                }
            }
        });

        loginViewModel.getLoginResult().observe(this, new Observer<LoginResult>() {
            @Override
            public void onChanged(@Nullable LoginResult loginResult) {
                if (loginResult == null) {
                    return;
                }
                loadingProgressBar.setVisibility(View.GONE);
                if (loginResult.getError() != null) {
                    showLoginFailed(loginResult.getError());
                }
                if (loginResult.getSuccess() != null) {
                    updateUiWithUser(loginResult.getSuccess());
                }
                setResult(Activity.RESULT_OK);

                //Complete and destroy login activity once successful
                finish();
            }
        });

        TextWatcher afterTextChangedListener = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // ignore
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // ignore
            }

            @Override
            public void afterTextChanged(Editable s) {
                loginViewModel.loginDataChanged(usernameEditText.getText().toString(),
                        passwordEditText.getText().toString());
            }
        };
        usernameEditText.addTextChangedListener(afterTextChangedListener);
        passwordEditText.addTextChangedListener(afterTextChangedListener);
        passwordEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {

            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    loginViewModel.login(usernameEditText.getText().toString(),
                            passwordEditText.getText().toString());
                }
                return false;
            }
        });


        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                hostname = usernameEditText.getText().toString();
                shaPublicKey = passwordEditText.getText().toString();
                // loadingProgressBar.setVisibility(View.VISIBLE);
                loginViewModel.login(usernameEditText.getText().toString(),
                        passwordEditText.getText().toString());

                try {
                    testCertificatePin(hostname, shaPublicKey);
                    System.out.println("Return value is " + returnVal);
                    resultEditText.setText(returnVal);
                } catch (Exception e) {
                    e.printStackTrace();
                }


            }
        });
    }


    private void updateUiWithUser(LoggedInUserView model) {
        String welcome = getString(R.string.welcome) + model.getDisplayName();
        // TODO : initiate successful logged in experience
        Toast.makeText(getApplicationContext(), welcome, Toast.LENGTH_LONG).show();
    }

    private void showLoginFailed(@StringRes Integer errorString) {
        Toast.makeText(getApplicationContext(), errorString, Toast.LENGTH_SHORT).show();
    }

    public void testCertificatePin(String host, String certPin) throws Exception {

        CertificatePinner certificatePinner = new CertificatePinner.Builder()
                .add(host, certPin)
                .build();

        System.out.println("Building OkHttpClient client");
        OkHttpClient.Builder builder = new OkHttpClient.Builder();

        builder.hostnameVerifier(new HostnameVerifier() {
            @Override
            public boolean verify(String hostname, SSLSession session) {
                System.out.println("Hostname is: " + hostname);
                if (hostname.equals("<your_private_ip_address>")) {
                    return true;
                }
                return false;
            }
        });
        OkHttpClient client = builder
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .certificatePinner(certificatePinner)
                .build();

        System.out.println("Built OkHttpClient client");
        System.out.println("Sending request");
        String url = "https://" + host + "/sitemap.xml";
        System.out.println("Request URL " + url);
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "Certificate Pinning App")
                .build();
        System.out.println("Sent request");
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                System.out.println("Error response received");
                mMessage = e.getMessage();
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        resultEditText.setText(mMessage);
                    }
                });
                e.printStackTrace();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (ResponseBody responseBody = response.body()) {
                    System.out.println("Response received");
                    if (!response.isSuccessful()) {
                        System.out.println("Unsuccessful Response received");
                        throw new IOException("Unexpected code " + response);
                    }

                    System.out.println("Successful Response received");
                    System.out.println("Printing Headers in Response");
                    Headers responseHeaders = response.headers();
                    for (int i = 0, size = responseHeaders.size(); i < size; i++) {
                        System.out.println(responseHeaders.name(i) + ": " + responseHeaders.value(i));
                    }

                    System.out.println("Printing Body in Response");
                    mMessage = responseBody.string();
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            resultEditText.setText(mMessage);
                        }
                    });
                    System.out.println(mMessage);
                }
            }
        });
    }

    public void handleResponse()
    {

    }
}



package com.sdl.hellosdlandroid;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemSelectedListener;

import com.smartdevicelink.proxy.rpc.enums.AppHMIType;

import java.lang.ref.WeakReference;

public class MainActivity extends AppCompatActivity implements OnItemSelectedListener {
    private static final String TAG = "MainActivity";
    boolean mBound = false;
    SdlService mService;
    TextView outputText;
    public static WeakReference<MainActivity> instance;
    // Spinner element
    private Spinner spinner;
//    TextView logs;
//    // @BindView(R.id.scrollView)
//    ScrollView scrollView;
    //private StringBuilder sb;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
//        scrollView = (ScrollView) findViewById(R.id.scrollView);
//        logs = (TextView) findViewById(R.id.logs);
        // outputText = (TextView) findViewById(R.id.terminalOutput);
        // outputText.setMovementMethod(new ScrollingMovementMethod());
        instance = new WeakReference<MainActivity>(this);
        spinner = (Spinner) findViewById(R.id.spinner1);
        // Spinner click listener
        spinner.setOnItemSelectedListener(this);
        setAllButtonsEnabled(false);
        //sb = new StringBuilder(10000);
        Log("MainActivity::onCreate");
    }


    public void Log(String text) {
        Log.d(TAG, "MainActivity::Log");
        outputText = (TextView) findViewById(R.id.terminalOutput);
        outputText.setMovementMethod(new ScrollingMovementMethod());
        //outputText.append("\n" + text);
        outputText.setText(outputText.getText() + "\n" + text);
    }


//    public void Log(String log) {
//        StringBuilder sb = new StringBuilder();
//        sb.append(log).append("\n");
//        logs.append(sb.toString());
//        scrollView.smoothScrollTo(0, logs.getBottom());
//    }

    public void setAllButtonsEnabled(Boolean state) {
        Button pasBtn = (Button) findViewById(R.id.pas);
        Button apsdBtn = (Button) findViewById(R.id.on_appsvc_data);
        pasBtn.setEnabled(state);
        apsdBtn.setEnabled(state);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log("MainActivity::onStart");
        //If we are connected to a module we want to start our SdlService
        if (BuildConfig.TRANSPORT.equals("MULTI") || BuildConfig.TRANSPORT.equals("MULTI_HB")) {
            Log("MainActivity::SdlReceiver.queryForConnectedService");
            SdlReceiver.queryForConnectedService(this);
            Intent proxyIntent = new Intent(this, SdlService.class);
            // Bind to SdlService
            bindService(proxyIntent, mConnection, Context.BIND_AUTO_CREATE);
            Log("MainActivity::Binding Service");
        } else if (BuildConfig.TRANSPORT.equals("TCP")) {
            Log("MainActivity::startService");
            Intent proxyIntent = new Intent(this, SdlService.class);
            startService(proxyIntent);
            // Bind to SdlService
            bindService(proxyIntent, mConnection, Context.BIND_AUTO_CREATE);
            Log("MainActivity::Binding Service");
        }
    }

    @Override
    protected void onStop() {
        Log("MainActivity::onStop");
        super.onStop();
        // Unbind from the service
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        // On selecting a spinner item
        String item = parent.getItemAtPosition(position).toString();
        // Showing selected spinner item
        Toast.makeText(parent.getContext(), "Selected: " + item, Toast.LENGTH_LONG).show();
    }

    public void onNothingSelected(AdapterView<?> arg0) {
        // TODO Auto-generated method stub
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    public boolean isAppIDEntered() {
        final EditText appIdInput = (EditText) findViewById(R.id.appIdInput);
        final String appID = String.valueOf(appIdInput.getText());
        return appID != null;
    }

    public String getAppID() {
        final EditText appIdInput = (EditText) findViewById(R.id.appIdInput);
        final String appID = String.valueOf(appIdInput.getText());
        return appID;
    }

    public String getIPAddress() {
        final EditText ipInput = (EditText) findViewById(R.id.ipInput);
        final String ipAddress = String.valueOf(ipInput.getText());
        return ipAddress;
    }

    public int getPort() {
        final EditText portInput = (EditText) findViewById(R.id.portInput);
        String value = portInput.getText().toString();
        final int portNumber = Integer.parseInt(value);
        return portNumber;
    }

    public AppHMIType getAppHMIType() {
        Spinner spinner1 = (Spinner) findViewById(R.id.spinner1);
        final String value = String.valueOf(spinner1.getSelectedItem());
        Log("AppHMI Type : " + value);
        final AppHMIType type = getAppHMITypeFromString(value);
        return type;
    }

    private AppHMIType getAppHMITypeFromString(final String appHmiType) {

        if (appHmiType.equals("MEDIA")) {
            return AppHMIType.MEDIA;
        }
        if (appHmiType.equals("COMMUNICATION")) {
            return AppHMIType.COMMUNICATION;
        }
        if (appHmiType.equals("MESSAGING")) {
            return AppHMIType.MESSAGING;
        }
        if (appHmiType.equals("NAVIGATION")) {
            return AppHMIType.NAVIGATION;
        }
        if (appHmiType.equals("INFORMATION")) {
            return AppHMIType.INFORMATION;
        }
        if (appHmiType.equals("TESTING")) {
            return AppHMIType.TESTING;
        }
        if (appHmiType.equals("PROJECTION")) {
            return AppHMIType.PROJECTION;
        }
        if (appHmiType.equals("REMOTE_CONTROL")) {
            return AppHMIType.REMOTE_CONTROL;
        }
        return AppHMIType.DEFAULT;
    }


    public void startProxy(View v) {
        if (mBound) {
            mService.startProxy();
        }
    }

    /**
     * Called when a button is clicked (the button in the layout file attaches to
     * this method with the android:onClick attribute)
     */
    public void sendPASRequest(View v) {
        if (mBound) {
            // Call a method from the SdlService.
            // However, if this call were something that might hang, then this request should
            // occur in a separate thread to avoid slowing down the activity performance.
            mService.publishAppServiceRequest();
        }
    }

    public void onAppServiceDataNotification(View view) {
        if (mBound) {
            // Call a method from the SdlService.
            // However, if this call were something that might hang, then this request should
            // occur in a separate thread to avoid slowing down the activity performance.
            mService.onAppServiceDataNotification();

        }
    }

    /**
     * Defines callbacks for service binding, passed to bindService()
     */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            Log("MainActivity::OnServiceConnected");
            // We've bound to SdlService, cast the IBinder and get SdlService instance
            SdlService.LocalBinder binder = (SdlService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
            Log("SDL Service bound ");
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            Log("MainActivity::OnServiceDisconnected");
            mBound = false;
            mService = null;
        }
    };

}

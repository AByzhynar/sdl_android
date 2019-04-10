package com.sdl.app_services_consumer;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemSelectedListener;
import android.view.WindowManager;

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


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        instance = new WeakReference<MainActivity>(this);
        spinner = (Spinner) findViewById(R.id.spinner1);
        // Spinner click listener
        spinner.setOnItemSelectedListener(this);
        setAllButtonsEnabled(false);
        Log("MainActivity::onCreate");

        Log("Current version: " + currentVersion());
        final double release = Double.parseDouble(Build.VERSION.RELEASE.replaceAll("(\\d+[.]\\d+)(.*)", "$1"));
        if (currentVersion().equals("Unsupported")) {
            showIncompatibleWarning();
            final Button startProxy = (Button) findViewById(R.id.startProxy);
            startProxy.setEnabled(false);
        }
    }

    //Current Android version data
    public static String currentVersion() {
        double release = Double.parseDouble(Build.VERSION.RELEASE.replaceAll("(\\d+[.]\\d+)(.*)", "$1"));
        String codeName = ""; // below Jelly bean OR above Oreo
        if (release < 5) codeName = "Unsupported";
        else if (5 <= release && release < 6) codeName = "Lollipop";
        else if (6 <= release && release < 7) codeName = "Marshmallow";
        else if (7 <= release && release < 8) codeName = "Nougat";
        else if (8 <= release && release < 9) codeName = "Oreo";
        else if (9 <= release && release < 10) codeName = "Pie";
        return codeName;
    }

    //Required Android version data
    public static String requiredVersion() {
        return "\nRequired version: Any version higher than 4.4.4";
    }


    public void showIncompatibleWarning() {
        Toast toast = Toast.makeText(MainActivity.this, "INCOMPATIBLE Android OS: " + currentVersion() + requiredVersion(), Toast.LENGTH_LONG);
        toast.setGravity(Gravity.CENTER, 0, 0);
        LinearLayout toastImage = (LinearLayout) toast.getView();
        ImageView imageView = new ImageView(MainActivity.this);
        imageView.setImageResource(R.drawable.image);
        toastImage.addView(imageView, 0);
        toast.show();
    }

    public void Log(String text) {
        Log.d(TAG, "MainActivity::Log");
        outputText = (TextView) findViewById(R.id.terminalOutput);
        outputText.setMovementMethod(new ScrollingMovementMethod());
        outputText.setText(outputText.getText() + "\n" + text);
    }

    public void setAllButtonsEnabled(Boolean state) {
        Button gasdBtn = (Button) findViewById(R.id.gasd);
        Button pasiBtn = (Button) findViewById(R.id.pasi);
        Button sendLocBtn = (Button) findViewById(R.id.sendloc);
        Button buttonPressBtn = (Button) findViewById(R.id.button_press);
        gasdBtn.setEnabled(state);
        pasiBtn.setEnabled(state);
        sendLocBtn.setEnabled(state);
        buttonPressBtn.setEnabled(state);
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
    public void getAppServiceData(View v) {
        if (mBound) {
            // Call a method from the SdlService.
            // However, if this call were something that might hang, then this request should
            // occur in a separate thread to avoid slowing down the activity performance.
            mService.getAppServiceDataRequest();
        }
    }

    public void performAppServiceInteraction(View view) {
        if (mBound) {
            // Call a method from the SdlService.
            // However, if this call were something that might hang, then this request should
            // occur in a separate thread to avoid slowing down the activity performance.
            mService.performAppServicesInteraction();

        }
    }

    public void sendLocation(View view) {
        if (mBound) {
            // Call a method from the SdlService.
            // However, if this call were something that might hang, then this request should
            // occur in a separate thread to avoid slowing down the activity performance.
            mService.sendLocationRequest();

        }
    }

    public void buttonPress(View view) {
        if (mBound) {
            // Call a method from the SdlService.
            // However, if this call were something that might hang, then this request should
            // occur in a separate thread to avoid slowing down the activity performance.
            mService.buttonPressRequest();
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

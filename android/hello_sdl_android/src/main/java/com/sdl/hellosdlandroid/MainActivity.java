package com.sdl.hellosdlandroid;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {
	private static final String TAG = "MainActivity";
	boolean mBound = false;
	//private Intent proxyIntent = null;
	SdlService mService;
	TextView tvOut;
	TextView outputText;


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		tvOut = (TextView) findViewById(R.id.tvOut);
		outputText = (TextView) findViewById(R.id.terminalOutput);
		outputText.setMovementMethod(new ScrollingMovementMethod());
		outputText.append("\n"+"MainActivity::onCreate");

	}


	@Override
	protected void onStart() {
		super.onStart();
		outputText.append("\n" + "MainActivity::onStart");
		//If we are connected to a module we want to start our SdlService
		if(BuildConfig.TRANSPORT.equals("MULTI") || BuildConfig.TRANSPORT.equals("MULTI_HB")) {
			outputText.append("\n"+"MainActivity::SdlReceiver.queryForConnectedService");
			SdlReceiver.queryForConnectedService(this);
		}else if(BuildConfig.TRANSPORT.equals("TCP")) {
			outputText.append("\n"+"MainActivity::startService");
			Intent proxyIntent = new Intent(this, SdlService.class);
			startService(proxyIntent);
			// Bind to SdlService
			bindService(proxyIntent, mConnection, Context.BIND_AUTO_CREATE);
			outputText.append("\n"+"MainActivity::Binding Service");
		}
	}

	@Override
	protected void onStop() {
		outputText.append("\n"+"MainActivity::onStop");
		super.onStop();
		// Unbind from the service
		if (mBound) {
			unbindService(mConnection);
			mBound = false;
		}
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


	/** Called when a button is clicked (the button in the layout file attaches to
	 * this method with the android:onClick attribute) */
	public void sendPASRequest(View v) {
		tvOut.setText("Нажата кнопка Publish Chosen Service");
		if (mBound) {
			tvOut.setText("Service bound ");
			// Call a method from the LocalService.
			// However, if this call were something that might hang, then this request should
			// occur in a separate thread to avoid slowing down the activity performance.
			//mService.publishAppServiceRequest();

		}
	}

	public void checkButtonWork(View view) {
		tvOut.setText("Нажата кнопка OnAppService Data");
	}

	/** Defines callbacks for service binding, passed to bindService() */
	private ServiceConnection mConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName className,
									   IBinder service) {
			outputText.append("\n"+"MainActivity::OnServiceConnected");
			// We've bound to SdlService, cast the IBinder and get SdlService instance
//			SdlService.LocalBinder binder = (SdlService.LocalBinder) service;
//			mService = binder.getService();
			mBound = true;
		}

		@Override
		public void onServiceDisconnected(ComponentName className) {
			outputText.append("\n"+"MainActivity::OnServiceDisconnected");
			mBound = false;
			mService = null;
		}
	};


}

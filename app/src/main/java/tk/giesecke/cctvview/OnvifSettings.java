package tk.giesecke.cctvview;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;

import tk.giesecke.cctvview.BuildConfig;

import static tk.giesecke.cctvview.CCTVview.DEBUG_LOG_TAG;
import static tk.giesecke.cctvview.CCTVview.sharedPref;

public class OnvifSettings extends AppCompatActivity
		implements View.OnClickListener {

	static final String PREF_IP = "url_txt";
	static final String PREF_RTSP_PATH = "rtsp_path_txt";
	static final String PREF_RTSP_PORT = "rtsp_port_txt";
	static final String PREF_WEB_PATH = "web_path_txt";
	static final String PREF_WEB_PORT = "web_port_txt";
	static final String PREF_USER = "user_txt";
	static final String PREF_PWD = "pwd_txt";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.onvif_settings);
		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		toolbar.setTitle(R.string.title_activity_onvif_settings);
		getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

		ActionBar discoveryActionBar = getSupportActionBar();
		if (discoveryActionBar != null) {
			discoveryActionBar.setDisplayHomeAsUpEnabled(true);
		} else {
			// SOMETHING REALLY BAD HAPPENED !!!!!!!!!!!!!!!!!!!
			finish();
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		// Fill fields with stored or default data
		EditText putField = (EditText) findViewById(R.id.et_cam_ip);
		putField.setText(sharedPref.getString(OnvifSettings.PREF_IP,getString(R.string.pref_default_cam_ip)));

		putField = (EditText) findViewById(R.id.et_cam_rtsppath);
		putField.setText(sharedPref.getString(OnvifSettings.PREF_RTSP_PATH,getString(R.string.pref_default_rtsp_path)));

		putField = (EditText) findViewById(R.id.et_cam_rtspport);
		putField.setText(sharedPref.getString(OnvifSettings.PREF_RTSP_PORT,getString(R.string.pref_default_rtsp_port)));

		putField = (EditText) findViewById(R.id.et_cam_webpath);
		putField.setText(sharedPref.getString(OnvifSettings.PREF_WEB_PATH,getString(R.string.pref_default_web_path)));

		putField = (EditText) findViewById(R.id.et_cam_webport);
		putField.setText(sharedPref.getString(OnvifSettings.PREF_WEB_PORT,getString(R.string.pref_default_web_port)));

		putField = (EditText) findViewById(R.id.et_cam_user);
		putField.setText(sharedPref.getString(OnvifSettings.PREF_USER,getString(R.string.pref_default_username)));

		putField = (EditText) findViewById(R.id.et_cam_pw);
		putField.setText(sharedPref.getString(OnvifSettings.PREF_PWD,getString(R.string.pref_default_passwd)));
	}

	@Override
	public void onClick(View view) {

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		if (BuildConfig.DEBUG) Log.d(DEBUG_LOG_TAG, "onCreateOptionsMenu started");
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.onvif_settings_menu, menu);
		return true;
	}

	@SuppressLint("ApplySharedPref")
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		switch (item.getItemId()) {
			case R.id.action_discover:
				Intent myIntent = new Intent(this, OnvifDiscovery.class);
				startActivity(myIntent);
				break;
			case R.id.action_save:
				saveSettings();
				break;
			case android.R.id.home:
				saveSettings();
			case R.id.action_cancel:
				startActivity(new Intent(this, CCTVview.class));
				finish();
				break;
			case R.id.action_clear:
				SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
				sharedPref.edit().clear().apply();
				recreate();
				break;
		}
		return super.onOptionsItemSelected(item);
	}

	@SuppressLint("ApplySharedPref")
	private void saveSettings() {
		SharedPreferences.Editor sharedPrefEditor = sharedPref.edit();

		EditText getField = (EditText) findViewById(R.id.et_cam_ip);
		sharedPrefEditor.putString(OnvifSettings.PREF_IP, getField.getText().toString());

		getField = (EditText) findViewById(R.id.et_cam_rtsppath);
		sharedPrefEditor.putString(OnvifSettings.PREF_RTSP_PATH, getField.getText().toString());

		getField = (EditText) findViewById(R.id.et_cam_rtspport);
		sharedPrefEditor.putString(OnvifSettings.PREF_RTSP_PORT, getField.getText().toString());

		getField = (EditText) findViewById(R.id.et_cam_webpath);
		sharedPrefEditor.putString(OnvifSettings.PREF_WEB_PATH, getField.getText().toString());

		getField = (EditText) findViewById(R.id.et_cam_webport);
		sharedPrefEditor.putString(OnvifSettings.PREF_WEB_PORT, getField.getText().toString());

		getField = (EditText) findViewById(R.id.et_cam_user);
		sharedPrefEditor.putString(OnvifSettings.PREF_USER, getField.getText().toString());

		getField = (EditText) findViewById(R.id.et_cam_pw);
		sharedPrefEditor.putString(OnvifSettings.PREF_PWD, getField.getText().toString());

		sharedPrefEditor.commit();
	}
}

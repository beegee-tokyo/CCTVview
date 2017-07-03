package tk.giesecke.cctvview;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import tk.giesecke.cctvview.BuildConfig;
import tk.giesecke.cctvview.Onvif.DeviceDiscovery;
import tk.giesecke.cctvview.Onvif.OnvifDevice;
import tk.giesecke.cctvview.Onvif.OnvifDeviceCapabilities;
import tk.giesecke.cctvview.Onvif.OnvifDeviceNetworkInterfaces;
import tk.giesecke.cctvview.Onvif.OnvifDeviceScopes;
import tk.giesecke.cctvview.Onvif.OnvifMediaProfiles;
import tk.giesecke.cctvview.Onvif.OnvifMediaStreamUri;

import static tk.giesecke.cctvview.Onvif.OnvifDeviceCapabilities.capabilitiesToString;
import static tk.giesecke.cctvview.Onvif.OnvifDeviceCapabilities.getCapabilitiesCommand;
import static tk.giesecke.cctvview.Onvif.OnvifDeviceCapabilities.parseCapabilitiesResponse;
import static tk.giesecke.cctvview.Onvif.OnvifDeviceNetworkInterfaces.getNetInterfacesCommand;
import static tk.giesecke.cctvview.Onvif.OnvifDeviceNetworkInterfaces.parseNetworkInterfacesResponse;
import static tk.giesecke.cctvview.Onvif.OnvifDeviceScopes.getScopesCommand;
import static tk.giesecke.cctvview.Onvif.OnvifDeviceScopes.parseScopesResponse;
import static tk.giesecke.cctvview.Onvif.OnvifDeviceScopes.scopesToString;
import static tk.giesecke.cctvview.Onvif.OnvifHeaderBody.getAuthorizationHeader;
import static tk.giesecke.cctvview.Onvif.OnvifHeaderBody.getEnvelopeEnd;
import static tk.giesecke.cctvview.Onvif.OnvifMediaProfiles.getProfilesCommand;
import static tk.giesecke.cctvview.Onvif.OnvifMediaProfiles.parseProfilesResponse;
import static tk.giesecke.cctvview.Onvif.OnvifMediaProfiles.profilesToString;
import static tk.giesecke.cctvview.Onvif.OnvifMediaStreamUri.getStreamUriCommand;
import static tk.giesecke.cctvview.Onvif.OnvifMediaStreamUri.parseStreamUriResponse;
import static tk.giesecke.cctvview.CCTVview.sharedPref;

public class OnvifDiscovery extends AppCompatActivity {

	/** Action for broadcast message to main activity */
	private static final String ONVIF_DISCOVERY_FINISHED = "O_DISC_FIN";
	/** Actions for device information collection */
	private static final int GET_SCOPES_ID = 0;
	private static final int GET_CAPABILITY_ID = 1;
	private static final int GET_PROFILES_ID = 2;
	private static final int GET_NETINTERF_ID = 3;
	private static final int GET_MEDIASTREAM_ID = 4;

	/** List of found devices */
	private List<OnvifDevice> onvifDevices;
	private OnvifDevice userSelectedDevice;

	private Intent receiverIntent = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.onvif_discovery);
		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);

		ActionBar discoveryActionBar = getSupportActionBar();
		if (discoveryActionBar != null) {
			discoveryActionBar.setDisplayHomeAsUpEnabled(true);
		} else {
			// SOMETHING REALLY BAD HAPPENED !!!!!!!!!!!!!!!!!!!
			finish();
		}
		onvifDevices = new ArrayList<>();
	}

	@Override
	public void onResume() {
		super.onResume();
		if (receiverIntent == null) {
			/* Intent filter for broadcast receiver */
			IntentFilter intentFilter = new IntentFilter(ONVIF_DISCOVERY_FINISHED);
			//Map the intent filter to the receiver
			receiverIntent = registerReceiver(onvifDiscoveryBCreceiver, intentFilter);
		}

		new ONVIFsearchDevices(this).execute();
	}

	@Override
	public void onPause() {
		super.onPause();
		if (receiverIntent != null) {
			unregisterReceiver(onvifDiscoveryBCreceiver);
			receiverIntent = null;
		}
	}

	/**
	 * Communication in Async Task between Android and Arduino Yun
	 */
	private class ONVIFsearchDevices extends AsyncTask<String, String, Boolean> {

		ProgressDialog searchDialog;
		final Context context;

		ONVIFsearchDevices(Activity activity) {
			this.context = activity;
		}

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			searchDialog = new ProgressDialog(context);
			searchDialog.setMessage(context.getString(R.string.dlg_txt_wait));
			searchDialog.setCancelable(false);
			searchDialog.show();
		}

		/**
		 * Background process of device discovery
		 *
		 * @param params
		 * 		params[0] = soapRequest as string
		 */
		@Override
		protected Boolean doInBackground(String... params) {

			OnvifDevice foundDevice = new OnvifDevice();

			boolean hasDevices = false;

			DeviceDiscovery.WS_DISCOVERY_PROBE_MESSAGE = "<s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:a=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\">" +
					"<s:Header>" +
					"<a:Action s:mustUnderstand=\"1\">http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</a:Action>" +
					"<a:MessageID>uuid:84c80cc6-34b7-4e48-bb52-c484522e641d</a:MessageID>" +
					"<a:ReplyTo>" +
					"<a:Address>http://schemas.xmlsoap.org/ws/2004/08/addressing/role/anonymous</a:Address>" +
					"</a:ReplyTo>" +
					"<a:To s:mustUnderstand=\"1\">urn:schemas-xmlsoap-org:ws:2005:04:discovery</a:To>" +
					"</s:Header>" +
					"<s:Body>" +
					"<Probe xmlns=\"http://schemas.xmlsoap.org/ws/2005/04/discovery\">" +
					"<d:Types xmlns:d=\"http://schemas.xmlsoap.org/ws/2005/04/discovery\" xmlns:dp0=\"http://www.onvif.org/ver10/network/wsdl\">dp0:NetworkVideoDisplay</d:Types>" +
					"</Probe>" +
					"</s:Body>" +
					"</s:Envelope>";
			// Optional to speed up search limit to http protocol and paths including "onvif"
			// final Collection<URL> urls = DeviceDiscovery.discoverWsDevicesAsUrls("^http$", ".*onvif.*");
			Collection<URL> urls = DeviceDiscovery.discoverWsDevicesAsUrls();
			if (urls.size() != 0) {
				hasDevices = true;
				for (URL url : urls) {
					foundDevice.discoveredURL = url;
					onvifDevices.add(foundDevice);
				}
			}

			DeviceDiscovery.WS_DISCOVERY_PROBE_MESSAGE = "<s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:a=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\">" +
					"<s:Header>" +
					"<a:Action s:mustUnderstand=\"1\">http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</a:Action>" +
					"<a:MessageID>uuid:33c74915-f359-4ae8-950f-992b24965c2c</a:MessageID>" +
					"<a:ReplyTo>\n" +
					"<a:Address>http://schemas.xmlsoap.org/ws/2004/08/addressing/role/anonymous</a:Address>" +
					"</a:ReplyTo>" +
					"<a:To s:mustUnderstand=\"1\">urn:schemas-xmlsoap-org:ws:2005:04:discovery</a:To>" +
					"</s:Header>" +
					"<s:Body>" +
					"<Probe xmlns=\"http://schemas.xmlsoap.org/ws/2005/04/discovery\">" +
					"<d:Types xmlns:d=\"http://schemas.xmlsoap.org/ws/2005/04/discovery\" xmlns:dp0=\"http://www.onvif.org/ver10/device/wsdl\">dp0:Device</d:Types>" +
					"</Probe>" +
					"</s:Body>" +
					"</s:Envelope";
			// Optional to speed up search limit to http protocol and paths including "onvif"
			// final Collection<URL> urls = DeviceDiscovery.discoverWsDevicesAsUrls("^http$", ".*onvif.*");
			urls = DeviceDiscovery.discoverWsDevicesAsUrls();
			if (urls.size() != 0) {
				hasDevices = true;
				for (URL url : urls) {
					foundDevice.discoveredURL = url;
					onvifDevices.add(foundDevice);
				}
			}

			DeviceDiscovery.WS_DISCOVERY_PROBE_MESSAGE = "<s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:a=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\">" +
					"<s:Header>" +
					"<a:Action s:mustUnderstand=\"1\">http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</a:Action>" +
					"<a:MessageID>uuid:02d6aa57-8cb4-4ab7-af24-3e9f814b8bd1</a:MessageID>" +
					"<a:ReplyTo>" +
					"<a:Address>http://schemas.xmlsoap.org/ws/2004/08/addressing/role/anonymous</a:Address>" +
					"</a:ReplyTo>" +
					"<a:To s:mustUnderstand=\"1\">urn:schemas-xmlsoap-org:ws:2005:04:discovery</a:To>" +
					"</s:Header>" +
					"<s:Body>" +
					"<Probe xmlns=\"http://schemas.xmlsoap.org/ws/2005/04/discovery\">" +
					"<d:Types xmlns:d=\"http://schemas.xmlsoap.org/ws/2005/04/discovery\" xmlns:dp0=\"http://www.onvif.org/ver10/network/wsdl\">dp0:NetworkVideoTransmitter</d:Types>" +
					"</Probe>" +
					"</s:Body>" +
					"</s:Envelope>";
			// Optional to speed up search limit to http protocol and paths including "onvif"
			// final Collection<URL> urls = DeviceDiscovery.discoverWsDevicesAsUrls("^http$", ".*onvif.*");
			urls = DeviceDiscovery.discoverWsDevicesAsUrls();
			if (urls.size() != 0) {
				hasDevices = true;
				for (URL url : urls) {
					foundDevice.discoveredURL = url;
					onvifDevices.add(foundDevice);
				}
			}

			if (hasDevices) {
				for (int i=0; i<onvifDevices.size(); i++) {
					OnvifDevice handledDevice = onvifDevices.get(i);
					handledDevice.baseUrl = handledDevice.discoveredURL.getHost();
					handledDevice.webPath = handledDevice.discoveredURL.getPath();
					handledDevice.webPort = handledDevice.discoveredURL.getPort();

					getDeviceInfo(handledDevice, getScopesCommand(), GET_SCOPES_ID);
					getDeviceInfo(handledDevice, getCapabilitiesCommand(), GET_CAPABILITY_ID);
					getDeviceInfo(handledDevice, getProfilesCommand(), GET_PROFILES_ID);
					getDeviceInfo(handledDevice, getNetInterfacesCommand(), GET_NETINTERF_ID);
					getDeviceInfo(handledDevice, getStreamUriCommand(), GET_MEDIASTREAM_ID);

					handledDevice.rtspPort = handledDevice.mediaStreamUri.mediaRtspPort;

					if (BuildConfig.DEBUG) Log.d("CCTVview", "Discovery found host: " + handledDevice.baseUrl
							+ " path: " + handledDevice.webPath
							+ " web port: " + handledDevice.webPort + "\n"
							+ " stream port: " + handledDevice.rtspPort + "\n"
					+ scopesToString(handledDevice.scopes)
					+ capabilitiesToString(handledDevice.devCapabilities)
					+ profilesToString(handledDevice.mediaProfiles[0]));
				}
			}
			return hasDevices;
		}

		/**
		 * Called when AsyncTask background process is finished
		 *
		 * @param foundDevices
		 * 		collection of all found camera URLs
		 */
		protected void onPostExecute(Boolean foundDevices) {
			// No devices found, close search dialog box
			if (searchDialog.isShowing()) {
				searchDialog.dismiss();
			}

			/* Intent for activity internal broadcast message */
			Intent broadCastIntent = new Intent();
			broadCastIntent.setAction(ONVIF_DISCOVERY_FINISHED);
			broadCastIntent.putExtra("RESULT",foundDevices);
			sendBroadcast(broadCastIntent);
		}
	}

	/**
	 * Broadcast receiver for notifications received over UDP or MQTT or GCM
	 */
	private final BroadcastReceiver onvifDiscoveryBCreceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getBooleanExtra("RESULT", false)) {
				showDiscoverySelection();
			} else {
				if (BuildConfig.DEBUG) Log.d("CCTVview", "No devices found");
				showDiscoveryFailed(getString(R.string.discovery_failed));
			}
		}
	};

	private void getDeviceInfo(OnvifDevice deviceUnderInvestigation, String command, int cmdType) {
		String httpURL = deviceUnderInvestigation.discoveredURL.toString();
		String receivedSOAP = "";

		/* A HTTP client to access the ONVIF device */
		// Set timeout to 5 minutes in case we have a lot of data to load
		OkHttpClient client = new OkHttpClient.Builder()
				.connectTimeout(300, TimeUnit.SECONDS)
				.writeTimeout(10, TimeUnit.SECONDS)
				.readTimeout(300, TimeUnit.SECONDS)
				.build();

		MediaType reqBodyType = MediaType.parse("application/soap+xml; charset=utf-8;");

		RequestBody reqBody = RequestBody.create(reqBodyType,
				getAuthorizationHeader() + command + getEnvelopeEnd());

		/* Request to ONVIF device */
		Request request;
		try {
			request = new Request.Builder()
					.url(httpURL)
					.post(reqBody)
					.build();
		} catch (IllegalArgumentException e) {
			if (BuildConfig.DEBUG) Log.d("CCTVview", "IllegalArgumentException: " + e.toString());
			return;
		}

		if (request != null) {
			try {
				/* Response from ONVIF device */
				Response response = client.newCall(request).execute();
				if (response.code() != 200) {
					if (BuildConfig.DEBUG) Log.d("CCTVview",
							"Failed command: " + command +
									" Response: " + response.code() +
									" " + response.message());
					return;
				} else {
					receivedSOAP = response.body().string();
				}
			} catch (IOException e) {
				if (BuildConfig.DEBUG) Log.d("CCTVview", "IllegalArgumentException: " + e.toString());
				return;
			}
		}
		switch (cmdType) {
			case GET_SCOPES_ID:
				deviceUnderInvestigation.scopes = new OnvifDeviceScopes();
				parseScopesResponse(receivedSOAP, deviceUnderInvestigation.scopes);
				break;
			case GET_PROFILES_ID:
				deviceUnderInvestigation.mediaProfiles[0] = new OnvifMediaProfiles();
				deviceUnderInvestigation.mediaProfiles[1] = new OnvifMediaProfiles();
				String part1Profile = receivedSOAP.substring(receivedSOAP.indexOf("<trt:Profiles token"),
						receivedSOAP.indexOf("</trt:Profiles>") + 15);
				parseProfilesResponse(part1Profile, deviceUnderInvestigation.mediaProfiles[0]);
				part1Profile = receivedSOAP.substring(receivedSOAP.lastIndexOf("<trt:Profiles token"),
						receivedSOAP.lastIndexOf("</trt:Profiles>") + 15);
				parseProfilesResponse(part1Profile, deviceUnderInvestigation.mediaProfiles[1]);
				break;
			case GET_CAPABILITY_ID:
				deviceUnderInvestigation.devCapabilities = new OnvifDeviceCapabilities();
				parseCapabilitiesResponse(receivedSOAP, deviceUnderInvestigation.devCapabilities);
				break;
			case GET_NETINTERF_ID:
				deviceUnderInvestigation.devNetInterface = new OnvifDeviceNetworkInterfaces();
				parseNetworkInterfacesResponse(receivedSOAP, deviceUnderInvestigation.devNetInterface);
				break;
			case GET_MEDIASTREAM_ID:
				deviceUnderInvestigation.mediaStreamUri = new OnvifMediaStreamUri();
				parseStreamUriResponse(receivedSOAP, deviceUnderInvestigation.mediaStreamUri);
				break;
		}

	}

	private void showDiscoveryFailed(String result) {
		Snackbar mySnackbar = Snackbar.make(findViewById(android.R.id.content),
				result,
				Snackbar.LENGTH_INDEFINITE);
		mySnackbar.setAction("OK", CCTVview.mOnClickListener);
		View snackbarView = mySnackbar.getView();
		TextView tv = (TextView) snackbarView.findViewById(android.support.design.R.id.snackbar_text);
		tv.setMaxLines(300);
		mySnackbar.show();
	}

	private void showDiscoverySelection() {
		final String[] discResults = new String[onvifDevices.size()];
		for (int i=0; i<onvifDevices.size(); i++) {
			OnvifDevice handledDevice = onvifDevices.get(i);
			discResults[i] = "Found:  " + handledDevice.scopes.scopeName +
			"  at  " + handledDevice.baseUrl + " Ports: " + handledDevice.webPort + " & " + handledDevice.rtspPort;
		}

		AlertDialog.Builder onvifDiscResultBuilder = new AlertDialog.Builder(this);
		onvifDiscResultBuilder.setTitle(getString(R.string.bt_txt_playselect));
		onvifDiscResultBuilder.setItems(discResults, new DialogInterface.OnClickListener() {
			@SuppressLint("ApplySharedPref")
			@Override
			public void onClick(DialogInterface dialog, int item) {
				userSelectedDevice = onvifDevices.get(item);
				SharedPreferences.Editor sharedPrefEditor = sharedPref.edit();
				sharedPrefEditor.putString(OnvifSettings.PREF_IP,userSelectedDevice.baseUrl);
				sharedPrefEditor.putString(OnvifSettings.PREF_WEB_PATH, userSelectedDevice.webPath);
				sharedPrefEditor.putString(OnvifSettings.PREF_WEB_PORT, String.valueOf(userSelectedDevice.webPort));
				sharedPrefEditor.putString(OnvifSettings.PREF_RTSP_PATH, userSelectedDevice.mediaStreamUri.mediaUri);
				sharedPrefEditor.putString(OnvifSettings.PREF_RTSP_PORT, String.valueOf(userSelectedDevice.mediaStreamUri.mediaRtspPort));
				sharedPrefEditor.commit();
				dialog.dismiss();
				startActivity(new Intent(getApplicationContext(), OnvifSettings.class));
				finish();
			}
		});

		onvifDiscResultBuilder.setCancelable(true)
				.setNegativeButton(getString(android.R.string.cancel), new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int id) {
						//When clicked on CANCEL button the dalog will be dismissed
						dialog.dismiss();
					}

				});
		AlertDialog onvifDiscResultDialog = onvifDiscResultBuilder.create();
		onvifDiscResultDialog.show();
	}
}

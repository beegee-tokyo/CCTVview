package tk.giesecke.cctvview.Onvif;

import java.net.URL;

@SuppressWarnings("unused")
public class OnvifDevice {
	public URL discoveredURL = null;
	public String baseUrl = "";
	public String webURL = "";
	public String webPath = "";
	public int webPort = -1;
	public String rtspURL = "";
	public String rtspPath = "";
	public int rtspPort = -1;
	public String userName = "";
	public String passWord = "";
	public OnvifDeviceScopes scopes = null;
	public OnvifDeviceInformation devInfo = null;
	public OnvifDeviceCapabilities devCapabilities = null;
	public OnvifDeviceDNS devDNS = null;
	public OnvifDeviceNetworkDefaultGateway devDefaultGateway = null;
	public OnvifDeviceNetworkInterfaces devNetInterface = null;
	public OnvifDeviceNetworkProtocols devNetProtocols = null;
	public final OnvifMediaProfiles[] mediaProfiles = {null, null};
	public OnvifMediaStreamUri mediaStreamUri = null;
	public OnvifMediaOSDs mediaOSDs = null;
	public OnvifPtzNodes ptzNodes = null;
	public OnvifPtzNode ptzNode = null;
	public OnvifPtzConfigurations ptzConfigs = null;
	public OnvifPtzConfiguration ptzConfig = null;
}

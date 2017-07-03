package tk.giesecke.cctvview.Onvif;

public class OnvifDeviceDNS {

	@SuppressWarnings("SameReturnValue")
	public static String getDNSCommand() {
		return "<GetDNS xmlns=\"http://www.onvif.org/ver10/device/wsdl\"/>";
	}
}

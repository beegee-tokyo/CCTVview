package tk.giesecke.cctvview.Onvif;

public class OnvifPtzNodes {

	@SuppressWarnings("SameReturnValue")
	public static String getNodesCommand() {
		return "<GetNodes xmlns=\"http://www.onvif.org/ver20/ptz/wsdl\"/>";
	}
}

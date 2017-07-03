package tk.giesecke.cctvview.Onvif;

public class OnvifPtzConfigurations {

	@SuppressWarnings("SameReturnValue")
	public static String getConfigsCommand() {
		return "<tds:GetConfigurations></tds:GetConfigurations>";
	}
}

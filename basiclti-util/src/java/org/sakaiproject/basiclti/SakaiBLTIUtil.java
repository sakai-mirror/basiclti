package org.sakaiproject.basiclti;

import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import java.net.URL;

import org.imsglobal.basiclti.BasicLTIUtil;

// Sakai APIs
import org.sakaiproject.tool.cover.ToolManager;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.cover.SessionManager;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.cover.UserDirectoryService;
import org.sakaiproject.tool.cover.ToolManager;
import org.sakaiproject.tool.api.ToolSession;
import org.sakaiproject.site.api.ToolConfiguration;
import org.sakaiproject.tool.api.Placement;
import org.sakaiproject.authz.cover.SecurityService;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SitePage;
import org.sakaiproject.site.cover.SiteService;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.tool.api.ActiveTool;
import org.sakaiproject.tool.cover.ActiveToolManager;
import org.sakaiproject.authz.cover.AuthzGroupService;
import org.sakaiproject.authz.api.AuthzGroup;
import org.sakaiproject.authz.api.GroupNotDefinedException;
import org.sakaiproject.component.cover.ServerConfigurationService;

/**
 * Some Sakai Utility code for IMS Basic LTI
 * This is mostly code to support the Sakai conventions for 
 * making and launching BLTI resources within Sakai.
 */
public class SakaiBLTIUtil {

    public static final boolean verbosePrint = false;

    public static void dPrint(String str)
    {
        if ( verbosePrint ) System.out.println(str);
    }

    // Look at a Placement and come up with the launch urls, and
    // other launch parameters to drive the launch.
    public static boolean parseDescriptor(Properties info, Properties launch, Placement placement)
    {
	Properties config = placement.getConfig();
	dPrint("Sakai properties=" + config);
        String launch_url = toNull(config.getProperty("imsti.launch", null));
        setProperty(info, "launch_url", launch_url);
        if ( launch_url == null ) {
            String xml = toNull(config.getProperty("imsti.xml", null));
            if ( xml == null ) return false;
	    BasicLTIUtil.parseDescriptor(info, launch, xml);
        }
        setProperty(info, "secret", config.getProperty("imsti.secret", null) );
        setProperty(info, "key", config.getProperty("imsti.key", null) );
        setProperty(info, "debug", config.getProperty("imsti.debug", null) );
        setProperty(info, "frameheight", config.getProperty("imsti.frameheight", null) );
        setProperty(info, "newwindow", config.getProperty("imsti.newwindow", null) );
        setProperty(info, "title", config.getProperty("imsti.tooltitle", null) );
        if ( info.getProperty("launch_url", null) != null || 
             info.getProperty("secure_launch_url", null) != null ) {
            return true;
        }
        return false;
    }

   // Retrieve the Sakai information about users, etc.
   public static boolean sakaiInfo(Properties props, Placement placement)
   {
	dPrint("placement="+ placement.getId());
	dPrint("placement title=" + placement.getTitle());
        String context = placement.getContext();
        dPrint("ContextID="+context);

        ToolConfiguration toolConfig = SiteService.findTool(placement.getId());
        // ActiveTool at = ActiveToolManager.getActiveTool(toolConfig.getToolId());
        Site site = null;
        SitePage page = null;
        try {
		site = SiteService.getSite(context);
        	page = site.getPage(toolConfig.getPageId());
        } catch (Exception e) {
                dPrint("No site/page associated with Launch context="+context);
                return false;
	}
                
	User user = UserDirectoryService.getCurrentUser();

	// Start setting the Basici LTI parameters
	setProperty(props,"resource_link_id",placement.getId());

	// TODO: Think about anonymus
	if ( user != null )
	{
		setProperty(props,"user_id",user.getId());
		setProperty(props,"launch_presentaion_locale","en_US"); // TODO: Really get this
		setProperty(props,"lis_person_name_given",user.getFirstName());
		setProperty(props,"lis_person_name_family",user.getLastName());
		setProperty(props,"lis_person_name_full",user.getDisplayName());
		setProperty(props,"lis_person_contact_emailprimary",user.getEmail());
		setProperty(props,"lis_person_sourced_id",user.getEid());
	}

	String theRole = "Student";
	if ( SecurityService.isSuperUser() )
	{
		theRole = "Administrator";
	}
	else if ( SiteService.allowUpdateSite(context) ) 
	{
		theRole = "Instructor";
	}
	setProperty(props,"roles",theRole);

	if ( site != null ) {
		String context_type = site.getType();
		if ( context_type != null && context_type.toLowerCase().contains("course") ){
			setProperty(props,"context_type","CourseOffering");
		}
		setProperty(props,"context_id",site.getId());
		setProperty(props,"context_title",site.getTitle());
		setProperty(props,"course_name",site.getShortDescription());
		String courseRoster = getExternalRealmId(site.getId());
		if ( courseRoster != null ) 
		{
			setProperty(props,"lis_course_offering_sourced_id",courseRoster);
		}
	}

	// We pass this along in the Sakai world - it might
	// might be useful to the external tool
	String serverId = ServerConfigurationService.getServerId();
	setProperty(props,"sakai_serverid",serverId);
        setProperty(props,"sakai_server",getOurServerUrl());

	// Get the organizational information
	setProperty(props,"tool_consmer_instance_guid", ServerConfigurationService.getString("basiclti.consumer_instance_guid",null));
	setProperty(props,"tool_consmer_instance_name", ServerConfigurationService.getString("basiclti.consumer_instance_name",null));
	setProperty(props,"tool_consmer_instance_url", ServerConfigurationService.getString("basiclti.consumer_instance_url",null));
	setProperty(props,"launch_presentation_return_url", ServerConfigurationService.getString("basiclti.consumer_return_url",null));
	return true;
    } 

    public static String postLaunchHTML(String placementId)
    {
        if ( placementId == null ) return "<p>Error, missing placementId.</p>";
        ToolConfiguration placement = SiteService.findTool(placementId);
        if ( placement == null ) return "<p>Error, cannot load placement="+placementId+".</p>";
    
        // Add user, course, etc to the launch parameters
        Properties launch = new Properties();
        if ( ! sakaiInfo(launch, placement) ) {
           return "<p>Error, cannot load Sakai information for placement="+placementId+".</p>";
        }
        
        // Retrieve the launch detail
        Properties info = new Properties();
        if ( ! parseDescriptor(info, launch, placement) ) {
           return "<p>Not Configured.</p>";
	}

        String launch_url = info.getProperty("secure_launch_url");
	if ( launch_url == null ) launch_url = info.getProperty("launch_url");
        if ( launch_url == null ) return "<p>Not configured</p>";

	String secret = toNull(info.getProperty("secret"));
	String key = toNull(info.getProperty("key"));

        String org_guid = ServerConfigurationService.getString("basiclti.consumer_instance_guid",null);
	String org_name = ServerConfigurationService.getString("basiclti.consumer_instance_name",null);
        String org_secret = null;
        if ( org_guid != null ) {
	    org_secret = getToolConsumerSecret(launch_url);
        }
        
        String oauth_callback = ServerConfigurationService.getString("basiclti.oauth_callback",null);
	// Too bad there is not a better default callback url for OAuth
        // Actually since we are using signing-only, there is really not much point 
	// In OAuth 6.2.3, this is after the user is authorized
	if ( oauth_callback == null ) oauth_callback = "about:blank";
        setProperty(launch, "oauth_callback", oauth_callback);

        launch = BasicLTIUtil.signProperties(launch, launch_url, "POST", 
            key, secret, org_secret, org_guid, org_name);

        if ( launch == null ) return "<p>Error signing message.</p>";
        dPrint("LAUNCH III="+launch);

	boolean dodebug = toNull(info.getProperty("debug")) != null;
        String postData = BasicLTIUtil.postLaunchHTML(launch, launch_url, dodebug);

        return postData;
    }

    public static void setProperty(Properties props, String key, String value)
    {
        if ( value == null ) return;
        if ( value.trim().length() < 1 ) return;
        props.setProperty(key, value);
    }

    private static String getContext()
    {
        String retval = ToolManager.getCurrentPlacement().getContext();
        return retval;
    }

    private static String getExternalRealmId(String siteId) {
        String realmId = SiteService.siteReference(siteId);
        String rv = null;
        try {
            AuthzGroup realm = AuthzGroupService.getAuthzGroup(realmId);
            rv = realm.getProviderGroupId();
        } catch (GroupNotDefinedException e) {
            dPrint("SiteParticipantHelper.getExternalRealmId: site realm not found"+e.getMessage());
        }
        return rv;
    } // getExternalRealmId

    // Look through a series of secrets from the properties based on the launchUrl
    private static String getToolConsumerSecret(String launchUrl)
    {
        String default_secret = ServerConfigurationService.getString("basiclti.consumer_instance_secret",null);
        dPrint("launchUrl = "+launchUrl);
        URL url = null;
        try {
            url = new URL(launchUrl);
        }
        catch (Exception e) {
            url = null;
        }
        if ( url == null ) return default_secret;
        String hostName = url.getHost();
        dPrint("host = "+hostName);
        if ( hostName == null || hostName.length() < 1 ) return default_secret;
        // Look for the property starting with the full name
        String org_secret = ServerConfigurationService.getString("basiclti.consumer_instance_secret."+hostName,null);
        if ( org_secret != null ) return org_secret;
        for ( int i = 0; i < hostName.length(); i++ ) {
            if ( hostName.charAt(i) != '.' ) continue;
            if ( i > hostName.length()-2 ) continue;
            String hostPart = hostName.substring(i+1);
            String propName = "basiclti.consumer_instance_secret."+hostPart;
            org_secret = ServerConfigurationService.getString(propName,null);
            if ( org_secret != null ) return org_secret;
        }
        return default_secret;
    }

    static private String getOurServerUrl() {
        String ourUrl = ServerConfigurationService.getString("sakai.rutgers.linktool.serverUrl");
        if (ourUrl == null || ourUrl.equals(""))
            ourUrl = ServerConfigurationService.getServerUrl();
        if (ourUrl == null || ourUrl.equals(""))
            ourUrl = "http://127.0.0.1:8080";

        return ourUrl;
    }

    public static String toNull(String str)
    {
       if ( str == null ) return null;
       if ( str.trim().length() < 1 ) return null;
       return str;
    }


}
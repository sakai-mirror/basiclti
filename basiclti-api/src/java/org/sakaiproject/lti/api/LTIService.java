/**********************************************************************************
 * $URL$
 * $Id$
 ***********************************************************************************
 *
 * Copyright (c) 2011 The Sakai Foundation
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.opensource.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **********************************************************************************/

package org.sakaiproject.lti.api;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.site.api.Site;

/**
 * <p>
 * A LTIService does things for LTI
 * </p>
 * <p>
 * Location is a combination of site id, (optional) page id and (optional) tool id
 * </p>
 */
public interface LTIService {
	/** This string starts the references to resources in this service. */
	static final String REFERENCE_ROOT = "/lti";

	static String WEB_PORTLET = "sakai.web.168";
	
	/**
	 * 
	 * @return
	 */
	public boolean isAdmin();

	/**
	 * 
	 * @return
	 */
	public boolean isMaintain();

    /**
     * Adds a memberships job. Quartz uses these to sync memberships for LTI
     * sites
     *
     * @param newProps
     * @return
     */
    public Object insertMembershipsJob(String siteId, String membershipsId, String membershipsUrl, String consumerKey, String ltiVersion);

    /**
     * Gets all the memberships jobs. Quartz uses these to sync memberships for LTI
     * sites
     *
     * @return A list of row mappings
     */
    public List<Map<String, Object>> getMembershipsJobs();

	/**
	 * 
	 * @return
	 */
	public String[] getToolModel();

	/**
	 * 
	 * @param newProps
	 * @return
	 */
	public Object insertTool(Properties newProps);

	/**
	 * 
	 * @param newProps
	 * @return
	 */
	public Object insertTool(Map<String,Object> newProps);

	/**
	 * 
	 * @param newProps
	 * @param siteId
	 * @return
	 */
	public Object insertToolDao(Properties newProps, String siteId);

	/**
	 * insert lti tool content
	 * @param id
	 * @param toolId
	 * @param reqProps
	 * @return
	 */
	public Object insertToolContent(String id, String toolId, Properties reqProps);
	
	/**
	 * insert lti tool content
	 * @param id
	 * @param toolId
	 * @param reqProps
	 * @param siteId
	 * @return
	 */
	public Object insertToolContent(String id, String toolId, Properties reqProps, String siteId);

	/**
	 * create an instance of lti tool within site
	 * @param id
	 * @param title
	 * @return
	 */
	public Object insertToolSiteLink(String id, String title);
	
	/**
	 * create an instance of lti tool within site
	 * @param id
	 * @param title
	 * @param siteId
	 * @return
	 */
	public Object insertToolSiteLink(String id, String title, String siteId);
	
	/**
	 * 
	 * @param key
	 * @return
	 */
	public Map<String, Object> getTool(Long key);

	/**
	 * 
	 * @param key
	 * @param siteId
	 * @return
	 */
	public Map<String, Object> getToolDao(Long key, String siteId);

	/**
	 * 
	 * @param resourceType
	 * @return
	 */
	public Map<String, Object> getToolForResourceHandlerDao(String resourceType);

	/**
	 * 
	 * @param url
	 * @return
	 */
	public Map<String, Object> getTool(String url);

	/**
	 * 
	 * @param key
	 * @return
	 */
	public boolean deleteTool(Long key);

	/**
	 * 
	 * @param key
	 * @param newProps
	 * @return
	 */
	public Object updateTool(Long key, Properties newProps);

	/**
	 * 
	 * @param key
	 * @param newProps
	 * @return
	 */
	public Object updateTool(Long key, Map<String, Object> newProps);

	/**
	 * 
	 * @param key
	 * @param newProps
	 * @param siteId
	 * @return
	 */
	public Object updateToolDao(Long key, Map<String, Object> newProps, String siteId);

	/**
	 * 
	 * @param search
	 * @param order
	 * @param first
	 * @param last
	 * @return
	 */
	public List<Map<String, Object>> getTools(String search, String order, int first, int last);

	/**
	 * 
	 * @param search
	 * @param order
	 * @param first
	 * @param last
	 * @param siteId
	 * @return
	 */
	public List<Map<String, Object>> getToolsDao(String search, String order, int first, int last, String siteId);

	/**
	 * 
	 * @param tool_id
	 * @return
	 */
	public String[] getContentModel(Long tool_id);

	/**
	 * 
	 * @param tool
	 * @return
	 */
	public String[] getContentModel(Map<String,Object> tool);

	/**
	 * 
	 * @param newProps
	 * @return
	 */
	public Object insertContent(Properties newProps);

	/**
	 * 
	 * @param newProps
	 * @param siteId
	 * @return
	 */
	public Object insertContentDao(Properties newProps, String siteId);

	/**
	 * 
	 * @param key
	 * @return
	 */
	public Map<String, Object> getContent(Long key);
	
	/**
	 * 
	 * @param key
	 * @param siteId
	 * @return
	 */
	public Map<String, Object> getContent(Long key, String siteId);

	/**
	 * Absolutely no checking at all.
	 * 
	 * @param key
	 * @return
	 */
	public Map<String, Object> getContentDao(Long key);

	/**
	 * 
	 * @param key
	 * @param siteId
	 * @return
	 */
	public Map<String, Object> getContentDao(Long key, String siteId);

	/**
	 * 
	 * @param key
	 * @return
	 */
	public boolean deleteContent(Long key);
	
	/**
	 * remove the tool content site link
	 * @param key
	 * @return
	 */
	public String deleteContentLink(Long key);

	/**
	 * 
	 * @param key
	 * @param newProps
	 * @return
	 */
	public Object updateContent(Long key, Map<String, Object> newProps);

	/**
	 * 
	 * @param key
	 * @param newProps
	 * @return
	 */
	public Object updateContent(Long key, Properties newProps);
	
	/**
	 * 
	 * @param key
	 * @param newProps
	 * @param siteId
	 * @return
	 */
	public Object updateContent(Long key, Properties newProps, String siteId);

	/**
	 * 
	 * @param key
	 * @param newProps
	 * @param siteId
	 * @return
	 */
	public Object updateContentDao(Long key, Map<String, Object> newProps, String siteId);

	/**
	 * 
	 * @param search
	 * @param order
	 * @param first
	 * @param last
	 * @return
	 */
	public List<Map<String, Object>> getContents(String search, String order, int first, int last);

	/**
	 * 
	 * @param content
	 * @return
	 */
	public String getContentLaunch(Map<String, Object> content);

	/**
	 * 
	 * @param content
	 * @param tool
	 */
	public void filterContent(Map<String, Object> content, Map<String, Object> tool);

	/**
	 * 
	 * @return
	 */
	public String[] getDeployModel();

	/**
	 * 
	 * @param newProps
	 * @param siteId
	 * @return
	 */
	public Object insertDeployDao(Properties newProps);

	/**
	 * 
	 * @param key
	 * @param newProps
	 * @return
	 */
	public Object updateDeployDao(Long key, Object newProps);

	/**
	 * 
	 * @param key
	 * @return
	 */
	public boolean deleteDeployDao(Long key);

	/**
	 * Absolutely no checking at all.
	 * 
	 * @param key
	 * @return
	 */
	public Map<String, Object> getDeployDao(Long key);

	/**
	 * Absolutely no checking at all.
	 * 
	 * @param consumerKey
	 * @return
	 */
	public Map<String, Object> getDeployForConsumerKeyDao(String consumerKey);

	/**
	 * 
	 * @param search
	 * @param order
	 * @param first
	 * @param last
	 * @param siteId
	 * @return
	 */
	public List<Map<String, Object>> getDeploysDao(String search, String order, int first, int last);






	/**
	 * 
	 * @param newProps
	 * @param siteId
	 * @return
	 */
	public Object insertProxyBindingDao(Properties newProps);

	/**
	 * 
	 * @param key
	 * @param newProps
	 * @return
	 */
	public Object updateProxyBindingDao(Long key, Object newProps);

	/**
	 * 
	 * @param key
	 * @return
	 */
	public boolean deleteProxyBindingDao(Long key);

	/**
	 * Absolutely no checking at all.
	 * 
	 * @param key
	 * @return
	 */
	public Map<String, Object> getProxyBindingDao(Long key);

	/**
	 * Absolutely no checking at all.
	 * 
	 * @param tool_id
	 * @param siteId
	 * @return
	 */
	public Map<String, Object> getProxyBindingDao(Long tool_id, String siteId);


	/**
	 * 
	 * @param row
	 * @param fieldInfo
	 * @return
	 */
	public String formOutput(Object row, String fieldInfo);

	/**
	 * 
	 * @param row
	 * @param fieldInfo
	 * @return
	 */
	public String formOutput(Object row, String[] fieldInfo);

	/**
	 * 
	 * @param row
	 * @param fieldInfo
	 * @return
	 */
	public String formInput(Object row, String fieldInfo);

	/**
	 * 
	 * @param row
	 * @param fieldInfo
	 * @return
	 */
	public String formInput(Object row, String[] fieldInfo);

	// For Instructors, this model is filtered down dynamically based on
	// Tool settings
	/**
	 * Model Descriptions for Foorm You should probably retrieve these through getters in
	 * case there is some filtering in the service based on role/permission
	 */
	static String[] CONTENT_MODEL = { 
		"id:key", 
		"tool_id:integer:hidden=true",
		"SITE_ID:text:label=bl_content_site_id:required=true:maxlength=99:role=admin",
		"title:text:label=bl_title:required=true:allowed=true:maxlength=1024",
		"pagetitle:text:label=bl_pagetitle:required=true:allowed=true:maxlength=1024",
		"frameheight:integer:label=bl_frameheight:allowed=true",
		"newpage:checkbox:label=bl_newpage",
		"debug:checkbox:label=bl_debug",
		"custom:textarea:label=bl_custom:rows=5:cols=25:allowed=true:maxlength=1024",
		"launch:url:label=bl_launch:maxlength=1024:allowed=true",
		"consumerkey:text:label=bl_consumerkey:allowed=true:maxlength=1024",
		"secret:text:label=bl_secret:allowed=true:maxlength=1024",
		"resource_handler:text:label=bl_resource_handler:maxlength=1024:role=admin",
		"xmlimport:text:hidden=true:maxlength=16384",
		// LTI 2.x settings
		"settings:text:hidden=true:maxlength=8096",
		// Sakai LTI 1.x extension settings (see SAK-25621)
		"settings_ext:text:hidden=true:maxlength=8096",
		"placement:text:hidden=true:maxlength=256", 
		"placementsecret:text:hidden=true:maxlength=512",
		"oldplacementsecret:text:hidden=true:maxlength=512",
		"created_at:autodate",
		"updated_at:autodate" };

	/**
	 * 
	 */
	static String[] TOOL_MODEL = { 
		"id:key",
		"version:radio:label=bl_version:choices=lti1,lti2:hidden=true",
		"SITE_ID:text:maxlength=99:role=admin",
		"title:text:label=bl_title:required=true:maxlength=1024",
		"allowtitle:radio:label=bl_allowtitle:choices=disallow,allow",
		"pagetitle:text:label=bl_pagetitle:required=true:maxlength=1024",
		"allowpagetitle:radio:label=bl_allowpagetitle:choices=disallow,allow",
		"description:textarea:label=bl_description:maxlength=4096",
		"status:radio:label=bl_status:choices=enable,disable",
		"visible:radio:label=bl_visible:choices=visible,stealth:role=admin",
		"resource_handler:text:label=bl_resource_handler:maxlength=1024:only=lti2",
		"deployment_id:integer:hidden=true",
		"lti2_launch:header:fields=launch,consumerkey,secret:only=lti2",
		"launch:url:label=bl_launch:maxlength=1024",
		"allowlaunch:radio:label=bl_allowlaunch:choices=disallow,allow:only=lti1",
		"consumerkey:text:label=bl_consumerkey:maxlength=1024",
		"allowconsumerkey:radio:label=bl_allowconsumerkey:choices=disallow,allow:only=lti1",
		"secret:text:label=bl_secret:maxlength=1024",
		"allowsecret:radio:label=bl_allowsecret:choices=disallow,allow:only=lti1",
		"frameheight:integer:label=bl_frameheight",
		"allowframeheight:radio:label=bl_allowframeheight:choices=disallow,allow",
		"privacy:header:fields=sendname,sendemailaddr",
		"sendname:checkbox:label=bl_sendname",
		"sendemailaddr:checkbox:label=bl_sendemailaddr",
		"services:header:fields=allowoutcomes,allowroster,allowsettings,allowlori",
		"allowoutcomes:checkbox:label=bl_allowoutcomes",
		"allowroster:checkbox:label=bl_allowroster",
		"allowsettings:checkbox:label=bl_allowsettings",
		"allowlori:checkbox:label=bl_allowlori",
		"newpage:radio:label=bl_newpage:choices=off,on,content",
		"debug:radio:label=bl_debug:choices=off,on,content",
		// LTI 1.x user-entered custom
		"custom:textarea:label=bl_custom:rows=5:cols=25:maxlength=1024",
		// LTI 2.x settings from web services
		"settings:text:hidden=true:maxlength=8096",
		// LTI 2.x tool-registration time parameters
		"parameter:textarea:label=bl_parameter:rows=5:cols=25:maxlength=1024:only=lti2",
		"enabled_capability:textarea:label=bl_enabled_capability:rows=5:cols=25:maxlength=1024:only=lti2",
		"allowcustom:checkbox:label=bl_allowcustom",
		"xmlimport:text:hidden=true:maxlength=16384",
		"splash:textarea:label=bl_splash:rows=5:cols=25:maxlength=4096",
		"created_at:autodate", 
		"updated_at:autodate" };

	/**
	 * 
	 */
	static String[] DEPLOY_MODEL = { 
		"id:key",
		"reg_state:radio:label=bl_reg_state:choices=lti2_ready,lti2_received,lti2_complete:hidden=true",
		"title:text:label=bl_title:required=true:maxlength=1024",
		"pagetitle:text:label=bl_pagetitle:required=true:maxlength=1024",
		"description:textarea:label=bl_description:maxlength=4096",
		"lti2_status:header:fields=status,visible",
		"status:radio:label=bl_status:choices=enable,disable",
		"visible:radio:label=bl_visible:choices=visible,stealth:role=admin",
		"privacy:header:fields=sendname,sendemailaddr",
		"sendname:checkbox:label=bl_sendname",
		"sendemailaddr:checkbox:label=bl_sendemailaddr",
		"services:header:fields=allowoutcomes,allowroster,allowsettings,allowlori",
		"allowoutcomes:checkbox:label=bl_allowoutcomes",
		"allowroster:checkbox:label=bl_allowroster",
		"allowsettings:checkbox:label=bl_allowsettings",
		"allowlori:checkbox:label=bl_allowlori",
		"lti2_internal:header:fields=reg_launch,reg_key,reg_secret,reg_password,consumerkey,secret,reg_profile:hide=insert",
		"reg_launch:url:label=bl_reg_launch:maxlength=1024:role=admin",
		"reg_key:text:label=bl_reg_key:maxlength=1024:hide=insert:role=admin",
		"reg_password:text:label=bl_reg_password:maxlength=1024:hide=insert:role=admin",
		"reg_ack:text:label=bl_reg_ack:maxlength=4096:hide=insert:role=admin",
		"consumerkey:text:label=bl_consumerkey:maxlength=1024:hide=insert",
		"secret:text:label=bl_secret:maxlength=1024:hide=insert",
		"new_secret:text:label=bl_secret:maxlength=1024:hide=insert",
		"reg_profile:textarea:label=bl_reg_profile:maxlength=10000:hide=insert:role=admin",
		"settings:text:hidden=true:maxlength=8096",   // This is "custom" in the JSON
		"created_at:autodate", 
		"updated_at:autodate" };

	// The model for the ToolProxy Binding (LTI 2.0)
	static String[] BINDING_MODEL = { 
		"id:key", 
		"tool_id:integer:hidden=true",
		"SITE_ID:text:maxlength=99:role=admin",
		"settings:text:hidden=true:maxlength=8096",
		"created_at:autodate",
		"updated_at:autodate" };

	static String[] MEMBERSHIPS_JOBS_MODEL = { 
		"SITE_ID:text:maxlength=99:required=true",
		"memberships_id:text:maxlength=256:required=true",
		"memberships_url:text:maxlength=4000:required=true",
		"consumerkey:text:label=bl_consumerkey:allowed=true:maxlength=1024",
		"lti_version:text:maxlength=32:required=true"};

	/** Static constants for data fields */

	static final String LTI_ID =    	"id";
	static final String LTI_SITE_ID =     "SITE_ID";
	static final String LTI_TOOL_ID =     "tool_id";
	static final String LTI_TITLE =    	"title";
	static final String LTI_ALLOWTITLE =	"allowtitle";
	static final String LTI_PAGETITLE =    	"pagetitle";
	static final String LTI_ALLOWPAGETITLE =	"allowpagetitle";
	static final String LTI_PLACEMENT =    "placement";
	static final String LTI_DESCRIPTION = "description";
	static final String LTI_STATUS = 	"status";
	static final String LTI_VISIBLE = 	"visible";
	static final String LTI_LAUNCH = 	"launch";
	static final String LTI_ALLOWLAUNCH = 	"allowlaunch";
	static final String LTI_CONSUMERKEY= 	"consumerkey";
	static final String LTI_ALLOWCONSUMERKEY= 	"allowconsumerkey";
	static final String LTI_SECRET =   	"secret";
	static final String LTI_NEW_SECRET =   	"new_secret";
	static final String LTI_ALLOWSECRET =   	"allowsecret";
	static final String LTI_SECRET_INCOMPLETE = "-----";
	static final String LTI_FRAMEHEIGHT = "frameheight";
	static final String LTI_ALLOWFRAMEHEIGHT = "allowframeheight";
	static final String LTI_SENDNAME =	"sendname";
	static final String LTI_SENDEMAILADDR = "sendemailaddr";
	static final String LTI_ALLOWOUTCOMES = "allowoutcomes";
	static final String LTI_ALLOWROSTER = "allowroster";
	static final String LTI_ALLOWSETTINGS = "allowsettings";
	static final String LTI_ALLOWLORI = "allowlori";
	static final String LTI_SETTINGS = "settings";
	static final String LTI_SETTINGS_EXT = "settings_ext";
	static final String LTI_NEWPAGE =	"newpage";
	static final String LTI_DEBUG =	"debug";
	static final String LTI_CUSTOM = 	"custom";
	static final String LTI_SPLASH = 	"splash";
	static final String LTI_ALLOWCUSTOM = "allowcustom";
	static final String LTI_XMLIMPORT = 	"xmlimport";
	static final String LTI_CREATED_AT =  "created_at"; 
	static final String LTI_UPDATED_AT = 	"updated_at";
	static final String LTI_MATCHPATTERN = "matchpattern";
	static final String LTI_NOTE = 	"note";
	static final String LTI_PLACEMENTSECRET = 	"placementsecret";
	static final String LTI_OLDPLACEMENTSECRET = 	"oldplacementsecret";
	static final String LTI_DEPLOYMENT_ID = 	"deployment_id";
	// BLTI-230 - LTI 2.0
	static final String LTI_VERSION = "version";
	static final Long LTI_VERSION_1 = new Long(0);
	static final Long LTI_VERSION_2 = new Long(1);
	static final String LTI_RESOURCE_HANDLER = "resource_handler";
	static final String LTI_REG_STATE = "reg_state";
	static final String LTI_REG_STATE_REGISTERED = "1";
	static final String LTI_REG_LAUNCH = "reg_launch";
	static final String LTI_REG_KEY = "reg_key";
	static final String LTI_REG_ACK = "reg_ack";
	static final String LTI_REG_PASSWORD = "reg_password";
	static final String LTI_PARAMETER = "parameter";
	static final String LTI_REG_PROFILE = "reg_profile";
	static final String LTI_ENABLED_CAPABILITY = "enabled_capability";
	// End of BLTI-230 - LTI 2.0

}
